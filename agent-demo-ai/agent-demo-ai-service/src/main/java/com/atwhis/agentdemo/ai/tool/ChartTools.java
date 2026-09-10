package com.atwhis.agentdemo.ai.tool;

import com.atwhis.agentdemo.ai.chart.ChartIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChartTools {
    public static final String SELECT_VISUALIZATION = "selectVisualization";

    private final ToolResultRecorder recorder;

    @Tool(name = SELECT_VISUALIZATION, description = "记录用户明确表达的图表展示意图，不查询业务数据。必须与销售查询 Tool 分开调用；用户未表达展示偏好时不要调用。")
    public String selectVisualization(
            @ToolParam(description = "图表类型，只允许 AUTO、BAR、PIE、LINE、NONE；用户要求只返回文字或不要图时使用 NONE") String chartType,
            @ToolParam(description = "数值指标，只允许 DEFAULT、销售额、销售数量、购买数量、订单数、销售占比；用户未指定时使用 DEFAULT") String metric,
            ToolContext context) {
        Object traceId = context.getContext().get("traceId");
        recorder.recordChartIntent(traceId == null ? null : traceId.toString(), ChartIntent.from(chartType, metric));
        return "展示意图已记录";
    }
}
