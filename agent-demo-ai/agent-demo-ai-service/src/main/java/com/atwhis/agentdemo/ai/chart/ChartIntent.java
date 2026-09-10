package com.atwhis.agentdemo.ai.chart;

import java.util.List;
import java.util.Locale;

public record ChartIntent(Type type, String metric) {
    public static final String DEFAULT_METRIC = "DEFAULT";
    private static final List<String> METRICS = List.of("销售额", "销售数量", "购买数量", "订单数", "销售占比");

    public ChartIntent {
        type = type == null ? Type.AUTO : type;
        metric = METRICS.contains(metric) ? metric : DEFAULT_METRIC;
    }

    public static ChartIntent from(String type, String metric) {
        try {
            return new ChartIntent(Type.valueOf(type == null ? "AUTO" : type.trim().toUpperCase(Locale.ROOT)), metric);
        } catch (IllegalArgumentException ignored) {
            return new ChartIntent(Type.AUTO, DEFAULT_METRIC);
        }
    }

    public static ChartIntent auto() {
        return new ChartIntent(Type.AUTO, DEFAULT_METRIC);
    }

    public String typeOr(String defaultType) {
        return type == Type.AUTO ? defaultType : type.name().toLowerCase(Locale.ROOT);
    }

    public String metricOr(String defaultMetric, List<String> availableMetrics) {
        return DEFAULT_METRIC.equals(metric) || !availableMetrics.contains(metric) ? defaultMetric : metric;
    }

    public enum Type { AUTO, BAR, PIE, LINE, NONE }
}
