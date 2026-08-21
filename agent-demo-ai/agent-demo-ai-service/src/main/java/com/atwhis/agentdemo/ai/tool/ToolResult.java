package com.atwhis.agentdemo.ai.tool;

import java.util.List;

public record ToolResult(String toolName, List<?> rows) {
    public ToolResult {
        rows = List.copyOf(rows);
    }
}
