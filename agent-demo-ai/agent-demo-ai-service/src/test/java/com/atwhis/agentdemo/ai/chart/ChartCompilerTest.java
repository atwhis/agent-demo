package com.atwhis.agentdemo.ai.chart;

import com.atwhis.agentdemo.ai.tool.SalesTools;
import com.atwhis.agentdemo.ai.tool.ToolResult;
import com.atwhis.agentdemo.sales.client.CategorySalesShareItem;
import com.atwhis.agentdemo.sales.client.PopularProductItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChartCompilerTest {
    private final ChartCompiler compiler = new ChartCompiler();

    @Test
    void keepsToolNumbersExactlyAndUsesWhitelistedDeclarativeOptions() {
        BigDecimal amount = new BigDecimal("1234.56");
        ChartSpec spec = compiler.compile(new ToolResult(SalesTools.POPULAR_PRODUCTS,
                List.of(new PopularProductItem(1L, "商品 A", 17, 4, amount)))).orElseThrow();

        assertThat(spec.type()).isEqualTo("bar");
        assertThat(spec.rows().getFirst()).containsEntry("销售数量", 17L).containsEntry("销售额", amount);
        assertThat(spec.options().values()).allMatch(v -> v instanceof String || v instanceof Number || v instanceof Boolean);
        assertThat(spec.toString()).doesNotContain("function", "javascript", "<script");
    }

    @Test
    void buildsPieFromCategoryShareAndRejectsExecutableValues() {
        ChartSpec spec = compiler.compile(new ToolResult(SalesTools.CATEGORY_SHARE,
                List.of(new CategorySalesShareItem(2L, "办公", 9, new BigDecimal("90.00"), new BigDecimal("45.00"))))).orElseThrow();
        assertThat(spec.type()).isEqualTo("pie");
        assertThat(spec.rows().getFirst()).containsEntry("销售占比", new BigDecimal("45.00"));

        assertThatThrownBy(() -> new ChartSpec("1.0", "id", "bar", "x", List.of("x"),
                List.of(java.util.Map.of("x", List.of(1))), java.util.Map.of("value", "x"), java.util.Map.of(), "tool"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesChartTypeAndMetricIndependentlyFromBusinessTool() {
        ToolResult category = new ToolResult(SalesTools.CATEGORY_SHARE,
                List.of(new CategorySalesShareItem(2L, "办公", 9, new BigDecimal("90.00"), new BigDecimal("45.00"))));
        ChartSpec bar = compiler.compile(category, ChartIntent.from("bar", "销售额")).orElseThrow();
        assertThat(bar.type()).isEqualTo("bar");
        assertThat(bar.encoding()).containsEntry("category", "分类").containsEntry("value", "销售额");

        ToolResult product = new ToolResult(SalesTools.POPULAR_PRODUCTS,
                List.of(new PopularProductItem(1L, "商品 A", 17, 4, new BigDecimal("1234.56"))));
        ChartSpec pie = compiler.compile(product, ChartIntent.from("pie", "销售额")).orElseThrow();
        assertThat(pie.type()).isEqualTo("pie");
        assertThat(pie.encoding()).containsEntry("name", "商品").containsEntry("value", "销售额");

        ChartSpec line = compiler.compile(product, ChartIntent.from("line", "订单数")).orElseThrow();
        assertThat(line.type()).isEqualTo("line");
        assertThat(line.encoding()).containsEntry("x", "商品").containsEntry("y", "订单数");
    }

    @Test
    void suppressesChartAndFallsBackFromInvalidOrUnavailableIntent() {
        ToolResult result = new ToolResult(SalesTools.CATEGORY_SHARE,
                List.of(new CategorySalesShareItem(2L, "办公", 9, new BigDecimal("90.00"), new BigDecimal("45.00"))));

        assertThat(compiler.compile(result, ChartIntent.from("none", "DEFAULT"))).isEmpty();

        ChartSpec invalid = compiler.compile(result, ChartIntent.from("scatter", "销售额")).orElseThrow();
        assertThat(invalid.type()).isEqualTo("pie");
        assertThat(invalid.encoding()).containsEntry("value", "销售占比");

        ChartSpec unavailable = compiler.compile(result,
                new ChartIntent(ChartIntent.Type.BAR, "购买数量")).orElseThrow();
        assertThat(unavailable.type()).isEqualTo("bar");
        assertThat(unavailable.encoding()).containsEntry("value", "销售占比");
    }
}
