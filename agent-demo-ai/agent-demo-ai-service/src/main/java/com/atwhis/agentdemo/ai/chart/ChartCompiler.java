package com.atwhis.agentdemo.ai.chart;

import com.atwhis.agentdemo.ai.tool.SalesTools;
import com.atwhis.agentdemo.ai.tool.ToolResult;
import com.atwhis.agentdemo.sales.client.CategorySalesShareItem;
import com.atwhis.agentdemo.sales.client.CustomerPurchaseRankingItem;
import com.atwhis.agentdemo.sales.client.PopularProductItem;
import com.atwhis.agentdemo.sales.client.SalesTrendPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ChartCompiler {
    public Optional<ChartSpec> compile(ToolResult result) {
        if (result.rows().isEmpty()) return Optional.empty();
        // Charts are derived from trusted Tool rows, never from model-authored JSON or JavaScript.
        return Optional.of(switch (result.toolName()) {
            case SalesTools.CUSTOMER_RANKING -> customer(result);
            case SalesTools.CATEGORY_SHARE -> category(result);
            case SalesTools.POPULAR_PRODUCTS -> product(result);
            case SalesTools.SALES_TREND -> trend(result);
            default -> throw new IllegalArgumentException("未知的图表来源 Tool");
        });
    }

    private ChartSpec customer(ToolResult result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            CustomerPurchaseRankingItem item = (CustomerPurchaseRankingItem) value;
            rows.add(row("客户", item.customerName(), "购买数量", item.purchaseQuantity(), "订单数", item.orderCount(), "销售额", item.salesAmount()));
        }
        return spec("bar", "客户购买数量排行", List.of("客户", "购买数量", "订单数", "销售额"), rows,
                Map.of("category", "客户", "value", "购买数量"), Map.of("horizontal", true), result.toolName());
    }

    private ChartSpec category(ToolResult result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            CategorySalesShareItem item = (CategorySalesShareItem) value;
            rows.add(row("分类", item.categoryName(), "销售占比", item.percentage(), "销售数量", item.salesQuantity(), "销售额", item.salesAmount()));
        }
        return spec("pie", "商品分类销售额占比", List.of("分类", "销售占比", "销售数量", "销售额"), rows,
                Map.of("name", "分类", "value", "销售占比"), Map.of("unit", "%"), result.toolName());
    }

    private ChartSpec product(ToolResult result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            PopularProductItem item = (PopularProductItem) value;
            rows.add(row("商品", item.productName(), "销售数量", item.salesQuantity(), "订单数", item.orderCount(), "销售额", item.salesAmount()));
        }
        return spec("bar", "热门商品销售数量排行", List.of("商品", "销售数量", "订单数", "销售额"), rows,
                Map.of("category", "商品", "value", "销售数量"), Map.of("horizontal", true), result.toolName());
    }

    private ChartSpec trend(ToolResult result) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            SalesTrendPoint item = (SalesTrendPoint) value;
            rows.add(row("时间", item.bucketLabel(), "订单数", item.orderCount(), "销售数量", item.salesQuantity(), "销售额", item.salesAmount()));
        }
        return spec("line", "销售趋势", List.of("时间", "订单数", "销售数量", "销售额"), rows,
                Map.of("x", "时间", "y", "销售额"), Map.of("smooth", true), result.toolName());
    }

    private ChartSpec spec(String type, String title, List<String> dimensions, List<Map<String, Object>> rows,
                           Map<String, String> encoding, Map<String, Object> options, String sourceTool) {
        // ChartSpec validates the small declarative whitelist consumed by the ECharts adapter.
        return new ChartSpec("1.0", UUID.randomUUID().toString(), type, title, dimensions, rows, encoding, options, sourceTool);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) row.put((String) values[index], values[index + 1]);
        return row;
    }
}
