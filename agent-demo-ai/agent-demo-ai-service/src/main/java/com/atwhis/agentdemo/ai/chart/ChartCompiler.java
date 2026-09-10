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
        return compile(result, ChartIntent.auto());
    }

    public Optional<ChartSpec> compile(ToolResult result, ChartIntent intent) {
        if (result.rows().isEmpty()) return Optional.empty();
        ChartIntent selection = intent == null ? ChartIntent.auto() : intent;
        if (selection.type() == ChartIntent.Type.NONE) return Optional.empty();
        // Charts are derived from trusted Tool rows, never from model-authored JSON or JavaScript.
        return Optional.of(switch (result.toolName()) {
            case SalesTools.CUSTOMER_RANKING -> customer(result, selection);
            case SalesTools.CATEGORY_SHARE -> category(result, selection);
            case SalesTools.POPULAR_PRODUCTS -> product(result, selection);
            case SalesTools.SALES_TREND -> trend(result, selection);
            default -> throw new IllegalArgumentException("未知的图表来源 Tool");
        });
    }

    private ChartSpec customer(ToolResult result, ChartIntent intent) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            CustomerPurchaseRankingItem item = (CustomerPurchaseRankingItem) value;
            rows.add(row("客户", item.customerName(), "购买数量", item.purchaseQuantity(), "订单数", item.orderCount(), "销售额", item.salesAmount()));
        }
        return spec(intent, "bar", "客户购买数量排行", "客户", "购买数量",
                List.of("客户", "购买数量", "订单数", "销售额"), rows, true, result.toolName());
    }

    private ChartSpec category(ToolResult result, ChartIntent intent) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            CategorySalesShareItem item = (CategorySalesShareItem) value;
            rows.add(row("分类", item.categoryName(), "销售占比", item.percentage(), "销售数量", item.salesQuantity(), "销售额", item.salesAmount()));
        }
        return spec(intent, "pie", "商品分类销售额占比", "分类", "销售占比",
                List.of("分类", "销售占比", "销售数量", "销售额"), rows, false, result.toolName());
    }

    private ChartSpec product(ToolResult result, ChartIntent intent) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            PopularProductItem item = (PopularProductItem) value;
            rows.add(row("商品", item.productName(), "销售数量", item.salesQuantity(), "订单数", item.orderCount(), "销售额", item.salesAmount()));
        }
        return spec(intent, "bar", "热门商品销售数量排行", "商品", "销售数量",
                List.of("商品", "销售数量", "订单数", "销售额"), rows, true, result.toolName());
    }

    private ChartSpec trend(ToolResult result, ChartIntent intent) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : result.rows()) {
            SalesTrendPoint item = (SalesTrendPoint) value;
            rows.add(row("时间", item.bucketLabel(), "订单数", item.orderCount(), "销售数量", item.salesQuantity(), "销售额", item.salesAmount()));
        }
        return spec(intent, "line", "销售趋势", "时间", "销售额",
                List.of("时间", "订单数", "销售数量", "销售额"), rows, false, result.toolName());
    }

    private ChartSpec spec(ChartIntent intent, String defaultType, String defaultTitle,
                           String category, String defaultMetric, List<String> dimensions,
                           List<Map<String, Object>> rows, boolean horizontal, String sourceTool) {
        String type = intent.typeOr(defaultType);
        String metric = intent.metricOr(defaultMetric, dimensions);
        String title = metric.equals(defaultMetric) ? defaultTitle : metric + "（按" + category + "）";
        Map<String, String> encoding = switch (type) {
            case "pie" -> Map.of("name", category, "value", metric);
            case "line" -> Map.of("x", category, "y", metric);
            default -> Map.of("category", category, "value", metric);
        };
        Map<String, Object> options = switch (type) {
            case "pie" -> "销售占比".equals(metric) ? Map.of("unit", "%") : Map.of();
            case "line" -> Map.of("smooth", true);
            default -> Map.of("horizontal", horizontal);
        };
        // ChartSpec validates the small declarative whitelist consumed by the ECharts adapter.
        return new ChartSpec("1.0", UUID.randomUUID().toString(), type, title, dimensions, rows, encoding, options, sourceTool);
    }

    private Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) row.put((String) values[index], values[index + 1]);
        return row;
    }
}
