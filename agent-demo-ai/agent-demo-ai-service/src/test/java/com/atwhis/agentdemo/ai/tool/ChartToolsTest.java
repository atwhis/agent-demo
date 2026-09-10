package com.atwhis.agentdemo.ai.tool;

import com.atwhis.agentdemo.ai.chart.ChartIntent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChartToolsTest {
    private final ToolResultRecorder recorder = new ToolResultRecorder();
    private final ChartTools tools = new ChartTools(recorder);

    @Test
    void recordsPresentationWithoutOverwritingBusinessResultAndClearsBoth() {
        ToolResult businessResult = new ToolResult(SalesTools.POPULAR_PRODUCTS, List.of("trusted-row"));
        recorder.record("trace-1", businessResult);

        tools.selectVisualization("pie", "销售额", new ToolContext(Map.of("traceId", "trace-1")));

        assertThat(recorder.find("trace-1")).contains(businessResult);
        assertThat(recorder.findChartIntent("trace-1"))
                .contains(new ChartIntent(ChartIntent.Type.PIE, "销售额"));

        recorder.clear("trace-1");
        assertThat(recorder.find("trace-1")).isEmpty();
        assertThat(recorder.findChartIntent("trace-1")).isEmpty();
    }

    @Test
    void normalizesInvalidIntentAndRequiresTraceId() {
        tools.selectVisualization("scatter", "profit", new ToolContext(Map.of("traceId", "trace-2")));
        assertThat(recorder.findChartIntent("trace-2")).contains(ChartIntent.auto());

        assertThatThrownBy(() -> tools.selectVisualization("bar", "销售额", new ToolContext(Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traceId");
    }
}
