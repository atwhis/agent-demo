package com.atwhis.agentdemo.ai.chat;

import com.atwhis.agentdemo.ai.chart.ChartCompiler;
import com.atwhis.agentdemo.ai.chart.ChartIntent;
import com.atwhis.agentdemo.ai.chart.ChartSpec;
import com.atwhis.agentdemo.ai.model.ChatGateway;
import com.atwhis.agentdemo.ai.tool.SalesTools;
import com.atwhis.agentdemo.ai.tool.ToolResult;
import com.atwhis.agentdemo.ai.tool.ToolResultRecorder;
import com.atwhis.agentdemo.sales.client.PopularProductItem;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {
    private final ToolResultRecorder recorder = new ToolResultRecorder();

    @Test
    void emitsSplitDeltasThenChartThenExactlyOneDone() {
        ChatGateway gateway = (model, message, trace) -> {
            recorder.record(trace, new ToolResult(SalesTools.POPULAR_PRODUCTS,
                    List.of(new PopularProductItem(1L, "A", 3, 1, new BigDecimal("10.00")))));
            return Flux.just("销售", "结果");
        };
        ChatService service = service(gateway);

        List<SseEvent> events = service.stream(new ChatRequest(null, "deepseek-v4-flash", "查询热门商品排行")).collectList().block();

        assertThat(events).extracting(SseEvent::type).containsExactly("delta", "delta", "chart", "done");
        assertThat(events).filteredOn(event -> event.type().equals("done")).hasSize(1);
    }

    @Test
    void refusesBusinessSuccessWithoutNonEmptyToolResult() {
        List<SseEvent> events = service((model, message, trace) -> Flux.just("我猜是 100"))
                .stream(new ChatRequest("c1", "deepseek-v4-flash", "查询销售数量"))
                .collectList().block();
        assertThat(events).extracting(SseEvent::type).containsExactly("delta", "error").doesNotContain("done");
    }

    @Test
    void appliesRecordedPresentationIntentToTrustedBusinessResult() {
        ChatGateway gateway = (model, message, trace) -> {
            recorder.record(trace, new ToolResult(SalesTools.POPULAR_PRODUCTS,
                    List.of(new PopularProductItem(1L, "A", 3, 1, new BigDecimal("10.00")))));
            recorder.recordChartIntent(trace, ChartIntent.from("pie", "销售额"));
            return Flux.just("销售结果");
        };

        List<SseEvent> events = service(gateway)
                .stream(new ChatRequest(null, "deepseek-v4-flash", "热门商品用饼图展示销售额"))
                .collectList().block();

        assertThat(events).extracting(SseEvent::type).containsExactly("delta", "chart", "done");
        ChartSpec chart = (ChartSpec) events.get(1).data();
        assertThat(chart.type()).isEqualTo("pie");
        assertThat(chart.encoding()).containsEntry("name", "商品").containsEntry("value", "销售额");
    }

    @Test
    void omitsChartWhenPresentationIntentIsNone() {
        ChatGateway gateway = (model, message, trace) -> {
            recorder.record(trace, new ToolResult(SalesTools.POPULAR_PRODUCTS,
                    List.of(new PopularProductItem(1L, "A", 3, 1, new BigDecimal("10.00")))));
            recorder.recordChartIntent(trace, ChartIntent.from("none", "DEFAULT"));
            return Flux.just("仅文字结果");
        };

        List<SseEvent> events = service(gateway)
                .stream(new ChatRequest(null, "deepseek-v4-flash", "查询热门商品，只要文字不要图"))
                .collectList().block();

        assertThat(events).extracting(SseEvent::type).containsExactly("delta", "done");
    }

    @Test
    void convertsProviderFailureToErrorWithoutDone() {
        List<SseEvent> events = service((model, message, trace) -> Flux.error(new IllegalStateException("secret upstream detail")))
                .stream(new ChatRequest(null, "gpt-5.6-terra", "你好"))
                .collectList().block();
        assertThat(events).extracting(SseEvent::type).containsExactly("error");
        assertThat(events.getFirst().data().toString()).doesNotContain("secret upstream detail");
    }

    @Test
    void cancellationClearsRequestScopedResultAndDoesNotEmitDone() {
        AtomicReference<String> trace = new AtomicReference<>();
        ChatGateway gateway = (model, message, id) -> {
            trace.set(id); recorder.record(id, new ToolResult(SalesTools.POPULAR_PRODUCTS, List.of()));
            return Flux.never();
        };
        StepVerifier.create(service(gateway).stream(new ChatRequest(null, "deepseek-v4-flash", "查询销售")))
                .thenAwait().thenCancel().verify();
        assertThat(recorder.find(trace.get())).isEmpty();
    }

    private ChatService service(ChatGateway gateway) {
        return new ChatService(gateway, recorder, new ChartCompiler(), new BusinessDataTurnGuard());
    }
}
