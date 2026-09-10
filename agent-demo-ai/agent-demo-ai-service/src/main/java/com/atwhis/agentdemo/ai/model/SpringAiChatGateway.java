package com.atwhis.agentdemo.ai.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agent-demo.ai.models", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SpringAiChatGateway implements ChatGateway {
    private final ModelRegistry registry;
    private final SystemPromptService promptService;

    @Override
    public Flux<String> stream(String modelId, String message, String traceId) {
        String systemPrompt = promptService.currentPrompt();
        log.info("发送模型请求: traceId={}, modelId={}, systemPrompt={}, userMessage={}",
                traceId, modelId, systemPrompt, message);
        // A registry lookup selects one immutable ChatClient; no shared model setting is mutated per request.
        return registry.requireClient(modelId).prompt()
                .system(systemPrompt)
                .user(message)
                // Spring AI forwards this context to every Tool call made in this model turn.
                .toolContext(Map.of("traceId", traceId))
                .stream()
                // content() exposes text fragments only; Tool calls and reasoning never become frontend events.
                .content();
    }
}
