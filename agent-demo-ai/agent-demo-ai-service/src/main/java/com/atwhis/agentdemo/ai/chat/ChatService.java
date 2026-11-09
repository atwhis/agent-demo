package com.atwhis.agentdemo.ai.chat;

import com.atwhis.agentdemo.ai.chart.ChartCompiler;
import com.atwhis.agentdemo.ai.knowledge.KnowledgeService;
import com.atwhis.agentdemo.ai.model.ChatGateway;
import com.atwhis.agentdemo.ai.model.ModelRegistry;
import com.atwhis.agentdemo.ai.tool.ToolResultRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

import static com.atwhis.agentdemo.ai.chat.IntentRouter.Route.SALES;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private final ChatGateway gateway;
    private final ChartCompiler chartCompiler;
    private final ConversationService conversations;
    private final IntentRouter router;
    private final ModelRegistry models;
    private final ObjectProvider<KnowledgeService> knowledge;

    public Flux<SseEvent> stream(ChatRequest request) {
        return Flux.defer(() -> {
            long startedAt = System.nanoTime();
            String cid = request.conversationId() == null || request.conversationId().isBlank()
                    ? UUID.randomUUID().toString() : request.conversationId();
            var turn = conversations.begin(cid);
            var decision = Mono.fromCallable(() -> ChatTiming.measure(turn.turnId, "intent_route",
                            () -> router.decide(request.modelId(), conversations.history(cid), request.message())))
                    .subscribeOn(Schedulers.boundedElastic());
            Flux<SseEvent> execution = decision.flatMapMany(plan -> {
                if (!turn.recordRoute(plan.route())) {
                    return Flux.empty();
                }
                return switch (plan.route()) {
                    case SALES -> sales(request, turn, plan.standaloneQuestion());
                    case RAG -> rag(request, turn, plan.standaloneQuestion());
                    case CLARIFY -> Flux.just(SseEvent.delta(plan.clarificationQuestion()))
                            .concatWith(done(request, turn, plan.route(), plan.clarificationQuestion()));
                };
            });
            return Flux.just(SseEvent.start(cid, turn.turnId)).concatWith(execution.takeUntilOther(turn.cancellation()))
                    .doOnError(error -> conversations.finish(turn, "FAILED"))
                    .doFinally(signal -> {
                        conversations.finish(turn, "CANCELLED");
                        var completed = turn.snapshot();
                        log.info("chat_timing turnId={} stage=total route={} elapsed_ms={} status={}",
                                turn.turnId, completed.route(), ChatTiming.elapsedMs(startedAt), completed.status());
                    });
        }).onErrorResume(error -> {
            log.warn("Chat failed: {}", error.getClass().getSimpleName());
            if (error instanceof ConversationService.ConversationBusyException) {
                return Flux.just(SseEvent.error("CONVERSATION_BUSY", error.getMessage()));
            }
            if (error instanceof KnowledgeService.QuestionTooLongException) {
                return Flux.just(SseEvent.error("QUESTION_TOO_LONG", error.getMessage()));
            }
            if (error instanceof IntentRouter.InvalidDecisionException) {
                return Flux.just(SseEvent.error("ROUTING_FAILED", "未能识别问题意图，请换一种问法后重试。"));
            }
            if (error instanceof KnowledgeFailure) {
                return Flux.just(SseEvent.error("KNOWLEDGE_FAILED", "知识检索或生成失败，请检查知识库和本地模型配置后重试。"));
            }
            return Flux.just(SseEvent.error(error instanceof IllegalArgumentException ? "INVALID_REQUEST" : "CHAT_FAILED",
                    error instanceof IllegalArgumentException ? "请求参数无效，请检查模型及查询条件。" : "模型或内部服务调用失败，请稍后重试。"));
        });
    }

    private Flux<SseEvent> sales(ChatRequest request, ToolResultRecorder.Turn turn, String standalone) {
        String prompt = request.message().equals(standalone) ? request.message()
                : request.message() + "\n本轮补全后的查询：" + standalone;
        StringBuilder answer = new StringBuilder();
        Flux<String> model = Flux.defer(() -> {
            long startedAt = System.nanoTime();
            return Flux.defer(() -> gateway.stream(request.modelId(), prompt, turn.turnId))
                    .doOnComplete(() -> {
                        ChatTiming.finish(turn.turnId, "sales.model_stream", startedAt, "OK");
                        if (turn.hasSales() && !turn.failed()) {
                            ChatTiming.finish(turn.turnId, "sales.llm_answer", turn.finalAnswerStartedAt(startedAt), "OK");
                        }
                    })
                    .doOnError(error -> ChatTiming.finish(turn.turnId, "sales.model_stream", startedAt, "ERROR"))
                    .doOnCancel(() -> ChatTiming.finish(turn.turnId, "sales.model_stream", startedAt, "CANCELLED"));
        });
        return model
                .filter(chunk -> !chunk.isEmpty()).map(chunk -> {
                    answer.append(chunk);
                    return SseEvent.delta(chunk);
                }).concatWith(Flux.defer(() -> {
                    if (turn.failed() || !turn.hasSales()) {
                        conversations.finish(turn, "FAILED");
                        return Flux.just(SseEvent.error("BUSINESS_DATA_UNVERIFIED", "未取得有效销售查询结果，请补充条件后重试。"));
                    }
                    var chart = turn.source().flatMap(call -> chartCompiler
                            .compile(call.businessResult(), turn.intent(), call.callId()));
                    return Mono.justOrEmpty(chart).map(SseEvent::chart).flux()
                            .concatWith(done(request, turn, SALES, answer.toString()));
                }));
    }

    private Flux<SseEvent> rag(ChatRequest request, ToolResultRecorder.Turn turn, String question) {
        return Mono.fromCallable(() -> {
            var service = ChatTiming.measure(turn.turnId, "rag.prepare", knowledge::getIfAvailable);
            if (service == null) {
                throw new IllegalStateException("知识库未启用");
            }
            var evidence = service.retrieve(question, turn.turnId);
            if (!turn.recordRetrieval(evidence.trace())) {
                return null;
            }
            return service.answer(models.requireClient(request.modelId()), question, evidence, turn.turnId);
        }).subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> !(error instanceof KnowledgeService.QuestionTooLongException), KnowledgeFailure::new)
                .flatMapMany(answer -> Flux.just(SseEvent.delta(answer.text()))
                        .concatWith(answer.sources().isEmpty() ? Flux.empty() : Flux.just(SseEvent.sources(answer.sources())))
                        .concatWith(done(request, turn, IntentRouter.Route.RAG, answer.text())));
    }

    private Mono<SseEvent> done(ChatRequest request, ToolResultRecorder.Turn turn, IntentRouter.Route route, String answer) {
        return Mono.defer(() -> {
            if (!conversations.complete(turn, request.message(), answer)) {
                return Mono.empty();
            }
            return Mono.just(SseEvent.done(turn.conversationId, turn.turnId, route));
        });
    }

    private static final class KnowledgeFailure extends RuntimeException {
        KnowledgeFailure(Throwable cause) {
            super(cause);
        }
    }
}
