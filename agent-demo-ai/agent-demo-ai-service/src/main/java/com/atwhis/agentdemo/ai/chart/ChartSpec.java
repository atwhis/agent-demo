package com.atwhis.agentdemo.ai.chart;

import java.util.List;
import java.util.Map;

public record ChartSpec(
        String version,
        String id,
        String type,
        String title,
        List<String> dimensions,
        List<Map<String, Object>> rows,
        Map<String, String> encoding,
        Map<String, Object> options,
        String sourceTool,
        String sourceCallId) {

    public ChartSpec {
        dimensions = List.copyOf(dimensions);
        rows = rows.stream().map(Map::copyOf).toList();
        encoding = Map.copyOf(encoding);
        options = Map.copyOf(options);
        if (sourceCallId == null || sourceCallId.isBlank()) {
            throw new IllegalArgumentException("缺少图表来源 callId");
        }
        validate(version, type, dimensions, rows, encoding, options, sourceTool);
    }

    private static void validate(String version, String type, List<String> dimensions,
                                 List<Map<String, Object>> rows, Map<String, String> encoding,
                                 Map<String, Object> options, String sourceTool) {
        if (!"1.0".equals(version)) throw new IllegalArgumentException("不支持的 ChartSpec 版本");
        if (!List.of("bar", "pie", "line").contains(type)) throw new IllegalArgumentException("不支持的图表类型");
        if (dimensions.isEmpty() || dimensions.size() > 8) throw new IllegalArgumentException("图表维度数量无效");
        if (rows.size() > 100) throw new IllegalArgumentException("图表数据不能超过 100 行");
        if (sourceTool == null || sourceTool.isBlank()) throw new IllegalArgumentException("缺少图表来源 Tool");
        if (encoding.isEmpty()) throw new IllegalArgumentException("缺少图表语义编码");
        for (Map<String, Object> row : rows) {
            if (!row.keySet().containsAll(dimensions)) throw new IllegalArgumentException("图表数据与维度不一致");
            for (Object value : row.values()) {
                if (!(value == null || value instanceof String || value instanceof Number || value instanceof Boolean)) {
                    throw new IllegalArgumentException("图表只允许标量数据");
                }
            }
        }
        for (Object value : options.values()) {
            if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("图表选项只允许安全标量");
            }
        }
    }
}
