package com.atwhis.agentdemo.ai.config;

import com.atwhis.agentdemo.ai.model.ChatGateway;
import com.atwhis.agentdemo.ai.model.ModelRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-demo.ai.models", name = "enabled", havingValue = "false")
public class DisabledAiConfiguration {
    @Bean
    ModelRegistry disabledModelRegistry() {
        return new ModelRegistry(Map.of(), List.of());
    }

    @Bean
    ChatGateway disabledChatGateway() {
        return (modelId, message, traceId) -> Flux.error(new IllegalArgumentException("模型功能当前未启用"));
    }
}
