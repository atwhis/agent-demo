package com.atwhis.agentdemo.ai.model;

import com.atwhis.agentdemo.ai.chat.ConversationService;
import com.atwhis.agentdemo.ai.tool.ChartTools;
import com.atwhis.agentdemo.ai.tool.SalesTools;
import com.atwhis.agentdemo.ai.tool.ToolResultRecorder;
import com.atwhis.agentdemo.ai.tool.TracedToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.template.NoOpTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "agent-demo.ai.models", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SpringAiChatGateway implements ChatGateway {
    private final ModelRegistry registry;
    private final SystemPromptService promptService;
    private final ConversationService conversations;

    private final List<ToolCallback> tools;

    public SpringAiChatGateway(ModelRegistry registry, SystemPromptService promptService, ConversationService conversations,
                               SalesTools sales, ChartTools charts, ToolResultRecorder recorder) {
        this.registry = registry;
        this.promptService = promptService;
        this.conversations = conversations;
        this.tools = Arrays.stream(ToolCallbacks.from(sales, charts))
                .<ToolCallback>map(callback -> new TracedToolCallback(callback, recorder)).toList();
    }

    @Override
    public Flux<String> stream(String modelId, String message, String traceId) {
        String systemPrompt = promptService.currentPrompt();
        log.info("发送模型请求: traceId={}, modelId={}", traceId, modelId);
        // A registry lookup selects one immutable ChatClient; no shared model setting is mutated per request.
        return registry.requireClient(modelId).prompt()
                .templateRenderer(new NoOpTemplateRenderer())
                .system(systemPrompt)
                .messages(conversations.historyForTurn(traceId))
                .user(message)
                // Spring AI forwards this context to every Tool call made in this model turn.
                .toolCallbacks(tools)
                .toolContext(Map.of("traceId", traceId))
                .stream()
                // content() exposes text fragments only; Tool calls and reasoning never become frontend events.
                .content();
    }
}
