package com.atwhis.agentdemo.ai.tool;

import com.atwhis.agentdemo.ai.chart.ChartIntent;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolResultRecorder {
    // Tool execution and stream completion may happen on different threads, hence a concurrent map.
    private final ConcurrentHashMap<String, ToolResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChartIntent> chartIntents = new ConcurrentHashMap<>();

    public void record(String traceId, ToolResult result) {
        requireTraceId(traceId);
        results.put(traceId, result);
    }

    public Optional<ToolResult> find(String traceId) {
        return Optional.ofNullable(results.get(traceId));
    }

    public void recordChartIntent(String traceId, ChartIntent intent) {
        requireTraceId(traceId);
        chartIntents.put(traceId, intent);
    }

    public Optional<ChartIntent> findChartIntent(String traceId) {
        return Optional.ofNullable(chartIntents.get(traceId));
    }

    public void clear(String traceId) {
        // ChatService invokes this for success, failure, and cancellation.
        results.remove(traceId);
        chartIntents.remove(traceId);
    }

    private void requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("缺少请求 traceId");
        }
    }
}
