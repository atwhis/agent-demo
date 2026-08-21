package com.atwhis.agentdemo.ai.model;

import reactor.core.publisher.Flux;

@FunctionalInterface
public interface ChatGateway {
    Flux<String> stream(String modelId, String message, String traceId);
}
