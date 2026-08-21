package com.atwhis.agentdemo.ai.model;

import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

public final class ModelRegistry {
    private final Map<String, ChatClient> clients;
    private final List<ModelDescriptor> descriptors;

    public ModelRegistry(Map<String, ChatClient> clients, List<ModelDescriptor> descriptors) {
        // Defensive copies make routing stable even while many SSE requests run concurrently.
        this.clients = Map.copyOf(clients);
        this.descriptors = List.copyOf(descriptors);
    }

    public ChatClient requireClient(String modelId) {
        // Validate before contacting a provider; an unknown frontend value must never fall back silently.
        ChatClient client = clients.get(modelId);
        if (client == null) {
            throw new IllegalArgumentException("未知或未启用的模型: " + modelId);
        }
        return client;
    }

    public List<ModelDescriptor> descriptors() {
        return descriptors;
    }
}
