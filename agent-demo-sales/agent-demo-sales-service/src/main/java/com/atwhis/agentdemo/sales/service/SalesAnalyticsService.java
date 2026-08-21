package com.atwhis.agentdemo.sales.service;

import com.atwhis.agentdemo.sales.client.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class SalesAnalyticsService {
    private static final String VALID_ORDER = "o.status IN ('PAID', 'COMPLETED')";
    private final NamedParameterJdbcTemplate jdbc;

    public SalesAnalyticsService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CustomerPurchaseRankingItem> customerRanking(LocalDate start, LocalDate end, int limit) {
        validateRange(start, end); validateLimit(limit);
        String sql = """
                SELECT c.id customer_id, c.customer_name, SUM(i.quantity) purchase_quantity,
                       COUNT(DISTINCT o.id) order_count, SUM(i.line_amount) sales_amount
                FROM t_sales_order o
                JOIN t_customer c ON c.id = o.customer_id
                JOIN t_sales_order_item i ON i.order_id = o.id
                WHERE o.order_time >= :startTime AND o.order_time < :endTime AND %s
                GROUP BY c.id, c.customer_name
                ORDER BY purchase_quantity DESC, sales_amount DESC, c.id ASC
                LIMIT :limit
                """.formatted(VALID_ORDER);
        return jdbc.query(sql, parameters(start, end).addValue("limit", limit), (rs, row) ->
                new CustomerPurchaseRankingItem(rs.getLong("customer_id"), rs.getString("customer_name"),
                        rs.getLong("purchase_quantity"), rs.getLong("order_count"),
                        rs.getBigDecimal("sales_amount").setScale(2, RoundingMode.HALF_UP)));
    }

    public List<CategorySalesShareItem> categoryShare(LocalDate start, LocalDate end) {
        validateRange(start, end);
        String sql = """
                SELECT i.category_id, COALESCE(pc.category_name, 'Unknown') category_name,
                       SUM(i.quantity) sales_quantity, SUM(i.line_amount) sales_amount
                FROM t_sales_order o
                JOIN t_sales_order_item i ON i.order_id = o.id
                LEFT JOIN t_product_category pc ON pc.id = i.category_id
                WHERE o.order_time >= :startTime AND o.order_time < :endTime AND %s
                GROUP BY i.category_id, pc.category_name
                ORDER BY sales_amount DESC, i.category_id ASC
                """.formatted(VALID_ORDER);
        record Raw(Long id, String name, long quantity, BigDecimal amount) { }
        List<Raw> rows = jdbc.query(sql, parameters(start, end), (rs, row) ->
                new Raw(rs.getLong("category_id"), rs.getString("category_name"),
                        rs.getLong("sales_quantity"), rs.getBigDecimal("sales_amount")));
        BigDecimal total = rows.stream().map(Raw::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return rows.stream().map(row -> new CategorySalesShareItem(row.id(), row.name(), row.quantity(),
                row.amount().setScale(2, RoundingMode.HALF_UP), percentage(row.amount(), total))).toList();
    }

    public List<PopularProductItem> popularProducts(LocalDate start, LocalDate end, int limit) {
        validateRange(start, end); validateLimit(limit);
        String sql = """
                SELECT i.product_id, i.product_name, SUM(i.quantity) sales_quantity,
                       COUNT(DISTINCT o.id) order_count, SUM(i.line_amount) sales_amount
                FROM t_sales_order o JOIN t_sales_order_item i ON i.order_id = o.id
                WHERE o.order_time >= :startTime AND o.order_time < :endTime AND %s
                GROUP BY i.product_id, i.product_name
                ORDER BY sales_quantity DESC, sales_amount DESC, i.product_id ASC
                LIMIT :limit
                """.formatted(VALID_ORDER);
        return jdbc.query(sql, parameters(start, end).addValue("limit", limit), (rs, row) ->
                new PopularProductItem(rs.getLong("product_id"), rs.getString("product_name"),
                        rs.getLong("sales_quantity"), rs.getLong("order_count"),
                        rs.getBigDecimal("sales_amount").setScale(2, RoundingMode.HALF_UP)));
    }

    public List<SalesTrendPoint> salesTrend(LocalDate start, LocalDate end, TrendGranularity granularity) {
        validateRange(start, end);
        if (granularity == null) throw new IllegalArgumentException("Granularity is required");
        String sql = """
                SELECT CAST(o.order_time AS DATE) sales_day, COUNT(DISTINCT o.id) order_count,
                       SUM(i.quantity) sales_quantity, SUM(i.line_amount) sales_amount
                FROM t_sales_order o JOIN t_sales_order_item i ON i.order_id = o.id
                WHERE o.order_time >= :startTime AND o.order_time < :endTime AND %s
                GROUP BY CAST(o.order_time AS DATE)
                ORDER BY sales_day
                """.formatted(VALID_ORDER);
        Map<LocalDate, MutableTrend> buckets = new TreeMap<>();
        for (LocalDate cursor = bucketStart(start, granularity); !cursor.isAfter(end); cursor = next(cursor, granularity)) {
            buckets.put(cursor, new MutableTrend());
        }
        jdbc.query(sql, parameters(start, end), rs -> {
            Date date = rs.getDate("sales_day");
            LocalDate key = bucketStart(date.toLocalDate(), granularity);
            MutableTrend aggregate = buckets.computeIfAbsent(key, ignored -> new MutableTrend());
            aggregate.orders += rs.getLong("order_count");
            aggregate.quantity += rs.getLong("sales_quantity");
            aggregate.amount = aggregate.amount.add(rs.getBigDecimal("sales_amount"));
        });
        return buckets.entrySet().stream().map(entry -> new SalesTrendPoint(entry.getKey(),
                label(entry.getKey(), granularity), entry.getValue().orders, entry.getValue().quantity,
                entry.getValue().amount.setScale(2, RoundingMode.HALF_UP))).toList();
    }

    private MapSqlParameterSource parameters(LocalDate start, LocalDate end) {
        return new MapSqlParameterSource()
                .addValue("startTime", start.atStartOfDay())
                .addValue("endTime", end.plusDays(1).atStartOfDay());
    }
    private static BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }
    public static void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) throw new IllegalArgumentException("startDate must be on or before endDate");
    }
    public static void validateLimit(int limit) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
    }
    private static LocalDate bucketStart(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }
    private static LocalDate next(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case DAY -> date.plusDays(1);
            case WEEK -> date.plusWeeks(1);
            case MONTH -> date.plusMonths(1);
        };
    }
    private static String label(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case DAY -> date.toString();
            case WEEK -> date + " ~ " + date.plusDays(6);
            case MONTH -> date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        };
    }
    private static final class MutableTrend {
        private long orders;
        private long quantity;
        private BigDecimal amount = BigDecimal.ZERO;
    }
}
