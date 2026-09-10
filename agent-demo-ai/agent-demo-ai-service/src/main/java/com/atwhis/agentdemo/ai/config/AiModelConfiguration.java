package com.atwhis.agentdemo.ai.config;

import com.atwhis.agentdemo.ai.model.ModelDescriptor;
import com.atwhis.agentdemo.ai.model.ModelRegistry;
import com.atwhis.agentdemo.ai.tool.ChartTools;
import com.atwhis.agentdemo.ai.tool.SalesTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-demo.ai.models", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiModelConfiguration {
    public static final String DEEPSEEK_ID = "deepseek-v4-flash";
    public static final String OPENAI_ID = "gpt-5.6-terra";

    @Bean("deepSeekChatClient")
    ChatClient deepSeekChatClient(
            SalesTools salesTools,
            ChartTools chartTools,
            @Value("${agent-demo.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${agent-demo.ai.deepseek.api-key:}") String apiKey) {
        Assert.hasText(apiKey, "必须通过 DEEPSEEK_API_KEY 提供 DeepSeek API Key");
        // Each provider gets its own model and immutable ChatClient. Both clients register the same SalesTools.
        DeepSeekApi api = DeepSeekApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        DeepSeekChatOptions.Builder optionsBuilder = DeepSeekChatOptions.builder();
        optionsBuilder.model(DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH);
        DeepSeekChatModel model = DeepSeekChatModel.builder().deepSeekApi(api).options(optionsBuilder.build()).build();
        return ChatClient.builder(model).defaultTools(salesTools, chartTools).build();
    }

    @Bean("openAiChatClient")
    ChatClient openAiChatClient(
            SalesTools salesTools,
            ChartTools chartTools,
            @Value("${agent-demo.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${agent-demo.ai.openai.api-key:}") String apiKey,
            @Value("${agent-demo.ai.openai.model:gpt-5.6-terra}") String modelName) {
        Assert.hasText(apiKey, "必须通过 OPENAI_API_KEY 提供 OpenAI API Key");
        // Provider-specific connection options stay here; downstream routing uses only the stable modelId.
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        optionsBuilder.baseUrl(baseUrl).apiKey(apiKey).model(modelName);
        OpenAiChatModel model = OpenAiChatModel.builder().options(optionsBuilder.build()).build();
        return ChatClient.builder(model).defaultTools(salesTools, chartTools).build();
    }

    @Bean
    ModelRegistry modelRegistry(
            @Qualifier("deepSeekChatClient") ChatClient deepSeek,
            @Qualifier("openAiChatClient") ChatClient openAi) {
        // The map key is the value sent by the browser and never contains a credential or mutable option.
        return new ModelRegistry(
                Map.of(DEEPSEEK_ID, deepSeek, OPENAI_ID, openAi),
                List.of(
                        new ModelDescriptor(DEEPSEEK_ID, "DeepSeek V4 Flash", true),
                        new ModelDescriptor(OPENAI_ID, "OpenAI GPT-5.6 Terra", false)));
    }
}
