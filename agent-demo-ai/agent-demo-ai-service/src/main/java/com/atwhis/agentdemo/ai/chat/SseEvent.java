package com.atwhis.agentdemo.ai.chat;

import com.atwhis.agentdemo.ai.knowledge.KnowledgeService;

import java.util.List;

public record SseEvent(String type, Object data) {
    public static SseEvent delta(String text) { return new SseEvent("delta", new DeltaPayload(text)); }
    public static SseEvent chart(Object chart) { return new SseEvent("chart", chart); }
    public static SseEvent start(String conversationId, String turnId) { return new SseEvent("start", new StartPayload(conversationId, turnId)); }
    public static SseEvent done(String conversationId, String turnId, IntentRouter.Route route) { return new SseEvent("done", new DonePayload(conversationId, turnId, route)); }
    public static SseEvent sources(List<KnowledgeService.Source> sources) { return new SseEvent("sources", sources); }
    public static SseEvent error(String code, String message) { return new SseEvent("error", new ErrorPayload(code, message)); }

    public record DeltaPayload(String text) { }
    public record StartPayload(String conversationId, String turnId) { }
    public record DonePayload(String conversationId, String turnId, IntentRouter.Route route) { }
    public record ErrorPayload(String code, String message) { }
}
