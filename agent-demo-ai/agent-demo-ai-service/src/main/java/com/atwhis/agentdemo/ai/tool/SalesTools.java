package com.atwhis.agentdemo.ai.tool;

import com.atwhis.agentdemo.sales.client.SalesStatisticsClient;
import com.atwhis.agentdemo.sales.client.TrendGranularity;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SalesTools {
    public static final String CUSTOMER_RANKING = "queryCustomerPurchaseRanking";
    public static final String CATEGORY_SHARE = "queryCategorySalesShare";
    public static final String POPULAR_PRODUCTS = "queryPopularProducts";
    public static final String SALES_TREND = "querySalesTrend";

    private final SalesStatisticsClient salesClient;
    private final ToolResultRecorder recorder;

    // @Tool description tells the model when to select a Java method; @ToolParam describes its JSON arguments.
    @Tool(name = CUSTOMER_RANKING, description = "查询指定日期范围内按购买商品数量降序的客户排行，同时返回订单数和销售额。适用于客户/人员购买排行，不适用于商品排行、分类占比或时间趋势。仅统计 PAID 和 COMPLETED 订单。")
    public List<?> queryCustomerPurchaseRanking(
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd，不能晚于结束日期") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，包含当天") String endDate,
            @ToolParam(description = "返回名次数量，1 到 50") int limit,
            ToolContext context) {
        DateRange range = range(startDate, endDate);
        validateLimit(limit);
        List<?> rows = salesClient.queryCustomerPurchaseRanking(range.start(), range.end(), limit);
        return record(context, CUSTOMER_RANKING, rows);
    }

    @Tool(name = CATEGORY_SHARE, description = "查询指定日期范围内各商品分类的销售金额占比，同时返回销售数量和销售额。适用于分类构成或占比，不适用于客户排行、具体商品排行或时间趋势。仅统计 PAID 和 COMPLETED 订单。")
    public List<?> queryCategorySalesShare(
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd，不能晚于结束日期") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，包含当天") String endDate,
            ToolContext context) {
        DateRange range = range(startDate, endDate);
        List<?> rows = salesClient.queryCategorySalesShare(range.start(), range.end());
        return record(context, CATEGORY_SHARE, rows);
    }

    @Tool(name = POPULAR_PRODUCTS, description = "查询指定日期范围内按销售数量降序的具体商品排行，同时返回订单数和销售额。适用于热门或最受欢迎的具体商品，不适用于客户排行、分类占比或时间趋势。仅统计 PAID 和 COMPLETED 订单。")
    public List<?> queryPopularProducts(
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd，不能晚于结束日期") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，包含当天") String endDate,
            @ToolParam(description = "返回名次数量，1 到 50") int limit,
            ToolContext context) {
        DateRange range = range(startDate, endDate);
        validateLimit(limit);
        List<?> rows = salesClient.queryPopularProducts(range.start(), range.end(), limit);
        return record(context, POPULAR_PRODUCTS, rows);
    }

    @Tool(name = SALES_TREND, description = "查询指定日期范围内按日、周或月连续补零的销售趋势，返回订单数、销售数量和销售额。适用于时间变化曲线，不适用于客户排行、商品排行或分类占比。仅统计 PAID 和 COMPLETED 订单。")
    public List<?> querySalesTrend(
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd，不能晚于结束日期") String startDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd，包含当天") String endDate,
            @ToolParam(description = "时间粒度，只允许 DAY、WEEK、MONTH") String granularity,
            ToolContext context) {
        DateRange range = range(startDate, endDate);
        TrendGranularity parsed;
        try {
            parsed = TrendGranularity.valueOf(granularity.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("granularity 只允许 DAY、WEEK、MONTH");
        }
        List<?> rows = salesClient.querySalesTrend(range.start(), range.end(), parsed);
        return record(context, SALES_TREND, rows);
    }

    private List<?> record(ToolContext context, String toolName, List<?> rows) {
        List<?> safeRows = rows == null ? List.of() : List.copyOf(rows);
        // Keep the exact Feign result for server-side chart compilation and verification after model streaming.
        Object traceId = context.getContext().get("traceId");
        recorder.record(traceId == null ? null : traceId.toString(), new ToolResult(toolName, safeRows));
        return safeRows;
    }

    private DateRange range(String startDate, String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("startDate 不能晚于 endDate");
            }
            if (start.plusYears(2).isBefore(end)) {
                throw new IllegalArgumentException("日期范围不能超过两年");
            }
            return new DateRange(start, end);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new IllegalArgumentException("日期必须使用 yyyy-MM-dd 格式");
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit 必须在 1 到 50 之间");
        }
    }

    private record DateRange(LocalDate start, LocalDate end) { }
}
