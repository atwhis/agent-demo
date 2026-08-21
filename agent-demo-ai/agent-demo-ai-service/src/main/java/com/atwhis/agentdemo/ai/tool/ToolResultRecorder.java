package com.atwhis.agentdemo.ai.tool;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolResultRecorder {
    // Tool execution and stream completion may happen on different threads, hence a concurrent map.
    private final ConcurrentHashMap<String, ToolResult> results = new ConcurrentHashMap<>();

    public void record(String traceId, ToolResult result) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("缺少请求 traceId");
        }
        results.put(traceId, result);
    }

    public Optional<ToolResult> find(String traceId) {
        return Optional.ofNullable(results.get(traceId));
    }

    public void clear(String traceId) {
        // ChatService invokes this for success, failure, and cancellation.
        results.remove(traceId);
    }
}
