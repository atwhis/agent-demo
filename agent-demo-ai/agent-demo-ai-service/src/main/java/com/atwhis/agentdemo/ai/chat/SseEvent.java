package com.atwhis.agentdemo.ai.chat;

public record SseEvent(String type, Object data) {
    public static SseEvent delta(String text) { return new SseEvent("delta", new DeltaPayload(text)); }
    public static SseEvent chart(Object chart) { return new SseEvent("chart", chart); }
    public static SseEvent done(String conversationId) { return new SseEvent("done", new DonePayload(conversationId)); }
    public static SseEvent error(String code, String message) { return new SseEvent("error", new ErrorPayload(code, message)); }

    public record DeltaPayload(String text) { }
    public record DonePayload(String conversationId) { }
    public record ErrorPayload(String code, String message) { }
}
