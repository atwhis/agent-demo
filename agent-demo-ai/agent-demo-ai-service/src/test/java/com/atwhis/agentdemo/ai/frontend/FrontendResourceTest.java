package com.atwhis.agentdemo.ai.frontend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendResourceTest {
    @Test
    void frontendContainsStreamingSafetyAndAllChartAdapters() throws IOException {
        String script = resource("/static/app.js");
        assertThat(script)
                .contains("fetch('/api/chat/stream'", "getReader()", "replace(/\\r\\n/g", "data.join('\\n')")
                .contains("DOMPurify.sanitize", "marked.parse", "setTimeout", "hljs.highlightElement")
                .contains("pendingChart", "if (!completed)", "['bar', 'pie', 'line']")
                .doesNotContain("innerHTML = marked.parse");
    }

    @Test
    void browserFixtureCoversMarkdownInterruptionModelSwitchingAndChartWhitelist() throws IOException {
        String fixture = resource("/frontend-fixtures.md");
        assertThat(fixture)
                .contains("| 商品 | 数量 |", "- 第一项", "```java", "<script>alert('unsafe')</script>")
                .contains("CRLF", "中断", "deepseek-v4-flash", "gpt-5.6-terra")
                .contains("\"type\": \"bar\"", "\"type\": \"pie\"", "\"type\": \"line\"");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("classpath resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
