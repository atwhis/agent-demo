package com.atwhis.agentdemo.ai.tool;

import com.atwhis.agentdemo.sales.client.PopularProductItem;
import com.atwhis.agentdemo.sales.client.SalesStatisticsClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.support.ToolCallbacks;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiToolCallingContractTest {

    @Test
    void mockedModelToolCallSelectsMethodBindsArgumentsAndReturnsFeignResult() {
        SalesStatisticsClient client = mock(SalesStatisticsClient.class);
        ToolResultRecorder recorder = new ToolResultRecorder();
        SalesTools salesTools = new SalesTools(client, recorder);
        var expected = List.of(new PopularProductItem(7L, "机械键盘", 18, 6, new BigDecimal("3582.00")));
        when(client.queryPopularProducts(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), 5))
                .thenReturn(expected);

        var options = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(Arrays.asList(ToolCallbacks.from(salesTools)))
                .toolContext(Map.of("traceId", "mock-model-trace"))
                .build();
        Prompt prompt = new Prompt("查询七月最受欢迎商品前五名", options);

        // This AssistantMessage is the provider response in which a mocked model selects a Tool.
        var toolCall = new AssistantMessage.ToolCall(
                "call-1", "function", SalesTools.POPULAR_PRODUCTS,
                "{\"startDate\":\"2026-07-01\",\"endDate\":\"2026-07-31\",\"limit\":5}");
        ChatResponse mockedModelResponse = new ChatResponse(List.of(
                new Generation(AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build())));

        var execution = ToolCallingManager.builder().build().executeToolCalls(prompt, mockedModelResponse);

        verify(client).queryPopularProducts(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), 5);
        assertThat(recorder.find("mock-model-trace")).hasValueSatisfying(result -> {
            assertThat(result.toolName()).isEqualTo(SalesTools.POPULAR_PRODUCTS);
            assertThat(result.rows()).isEqualTo(expected);
        });
        assertThat(execution.conversationHistory().getLast()).isInstanceOf(ToolResponseMessage.class);
        ToolResponseMessage response = (ToolResponseMessage) execution.conversationHistory().getLast();
        assertThat(response.getResponses()).singleElement().satisfies(toolResponse -> {
            assertThat(toolResponse.name()).isEqualTo(SalesTools.POPULAR_PRODUCTS);
            assertThat(toolResponse.responseData()).contains("机械键盘", "3582.00");
        });
    }
}
