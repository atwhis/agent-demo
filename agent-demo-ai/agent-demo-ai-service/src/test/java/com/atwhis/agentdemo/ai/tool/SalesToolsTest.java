package com.atwhis.agentdemo.ai.tool;

import com.atwhis.agentdemo.sales.client.PopularProductItem;
import com.atwhis.agentdemo.sales.client.SalesStatisticsClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesToolsTest {
    private final SalesStatisticsClient client = mock(SalesStatisticsClient.class);
    private final ToolResultRecorder recorder = new ToolResultRecorder();
    private final SalesTools tools = new SalesTools(client, recorder);

    @Test
    void delegatesValidatedArgumentsAndCapturesResultByTrace() {
        var expected = List.of(new PopularProductItem(1L, "测试商品", 12, 3, new BigDecimal("88.50")));
        when(client.queryPopularProducts(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), 5)).thenReturn(expected);

        Object result = tools.queryPopularProducts("2026-07-01", "2026-07-31", 5,
                new ToolContext(Map.of("traceId", "trace-1")));

        assertThat(result).isEqualTo(expected);
        verify(client).queryPopularProducts(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), 5);
        assertThat(recorder.find("trace-1")).hasValueSatisfying(captured -> {
            assertThat(captured.toolName()).isEqualTo(SalesTools.POPULAR_PRODUCTS);
            assertThat(captured.rows()).isEqualTo(expected);
        });
    }

    @Test
    void rejectsInvalidArgumentsBeforeFeignCall() {
        assertThatThrownBy(() -> tools.queryPopularProducts("2026-08-10", "2026-08-01", 10,
                new ToolContext(Map.of("traceId", "trace"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不能晚于");
        assertThatThrownBy(() -> tools.querySalesTrend("2026-08-01", "2026-08-10", "hour",
                new ToolContext(Map.of("traceId", "trace"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DAY");
    }
}
