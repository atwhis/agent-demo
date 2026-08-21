package com.atwhis.agentdemo.ai.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ModelRegistryTest {
    @Test
    void routesConcurrentlyWithoutCrossRequestMutation() throws Exception {
        ChatClient deepSeek = mock(ChatClient.class), openAi = mock(ChatClient.class);
        ModelRegistry registry = new ModelRegistry(Map.of("deepseek-v4-flash", deepSeek, "gpt-5.6-terra", openAi),
                List.of(new ModelDescriptor("deepseek-v4-flash", "DeepSeek", true), new ModelDescriptor("gpt-5.6-terra", "OpenAI", false)));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 200).mapToObj(index -> executor.submit(() ->
                    registry.requireClient(index % 2 == 0 ? "deepseek-v4-flash" : "gpt-5.6-terra"))).toList();
            for (int index = 0; index < tasks.size(); index++) {
                assertThat(tasks.get(index).get()).isSameAs(index % 2 == 0 ? deepSeek : openAi);
            }
        }
        assertThatThrownBy(() -> registry.requireClient("unknown")).isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.descriptors()).hasSize(2).extracting(ModelDescriptor::id)
                .containsExactly("deepseek-v4-flash", "gpt-5.6-terra");
    }
}
