package com.atwhis.agentdemo.ai.chat;

import com.atwhis.agentdemo.ai.chart.ChartCompiler;
import com.atwhis.agentdemo.ai.model.ChatGateway;
import com.atwhis.agentdemo.ai.tool.ToolResult;
import com.atwhis.agentdemo.ai.tool.ToolResultRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {
    private final ChatGateway gateway;
    private final ToolResultRecorder recorder;
    private final ChartCompiler chartCompiler;
    private final BusinessDataTurnGuard guard;

    /** Coordinates one complete browser turn: model text, captured Tool data, chart, then completion. */
    public Flux<SseEvent> stream(ChatRequest request) {
        // traceId belongs to one request and links Spring AI's ToolContext to its captured result.
        String traceId = UUID.randomUUID().toString();
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString() : request.conversationId();

        // defer prevents model/network work until the HTTP response is actually subscribed.
        Flux<SseEvent> deltas = Flux.defer(() -> gateway.stream(request.modelId(), request.message(), traceId))
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .map(SseEvent::delta);

        // concatWith runs this only after the model stream completes, so chart/done cannot overtake text.
        Flux<SseEvent> tail = Flux.defer(() -> {
            ToolResult result = recorder.find(traceId).orElse(null);
            if (guard.requiresSuccessfulTool(request.message()) && (result == null || result.rows().isEmpty())) {
                return Flux.just(SseEvent.error("BUSINESS_DATA_UNVERIFIED", "未取得有效销售数据，本次回答不能作为成功结果。请补充日期范围后重试。"));
            }
            List<SseEvent> events = new ArrayList<>();
            if (result != null) chartCompiler.compile(result).ifPresent(chart -> events.add(SseEvent.chart(chart)));
            events.add(SseEvent.done(conversationId));
            return Flux.fromIterable(events);
        });

        return deltas.concatWith(tail)
                .doOnError(error -> log.warn("Chat turn failed: traceId={}, modelId={}, errorType={}",
                        traceId, request.modelId(), error.getClass().getSimpleName()))
                .onErrorResume(error -> Flux.just(SseEvent.error(errorCode(error), safeMessage(error))))
                // doFinally also runs on browser cancellation; this prevents request-scoped data leaks.
                .doFinally(signal -> recorder.clear(traceId));
    }

    private String errorCode(Throwable error) {
        return error instanceof IllegalArgumentException ? "INVALID_REQUEST" : "CHAT_FAILED";
    }

    private String safeMessage(Throwable error) {
        return error instanceof IllegalArgumentException ? error.getMessage() : "模型或内部服务调用失败，请稍后重试。";
    }
}
