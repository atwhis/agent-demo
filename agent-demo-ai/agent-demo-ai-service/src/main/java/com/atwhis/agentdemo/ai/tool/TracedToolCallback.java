package com.atwhis.agentdemo.ai.tool;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.HashMap;

public record TracedToolCallback(ToolCallback delegate, ToolResultRecorder recorder) implements ToolCallback {
    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }
    @Override
    public String call(String input) {
        throw new IllegalStateException("缺少请求上下文");
    }
    @Override
    public String call(String input, ToolContext context) {
        String trace = (String) context.getContext().get("traceId");
        String call = recorder.begin(trace, getToolDefinition().name(), input);
        var values = new HashMap<>(context.getContext());
        var turn = recorder.turn(trace).orElseThrow();
        values.put("conversationId", turn.conversationId);
        values.put("turnId", turn.turnId);
        values.put("callId", call);
        try {
            String output = delegate.call(input, new ToolContext(values));
            recorder.finish(trace, call, output, null);
            return output;
        } catch (RuntimeException ex) {
            recorder.finish(trace, call, null, ex);
            throw ex;
        }
    }
}
