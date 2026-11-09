package com.atwhis.agentdemo.ai.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("agent-demo.ai.knowledge")
record KnowledgeSettings(
        String url, String username, String password,
        @DefaultValue("classpath:models/embedding") String embeddingDirectory,
        @DefaultValue("classpath:models/reranker") String rerankerDirectory,
        @DefaultValue("320") int targetTokens, @DefaultValue("400") int maxTokens,
        @DefaultValue("40") int overlapTokens, @DefaultValue("15") int topK,
        @DefaultValue("5") int topN, @DefaultValue("-3.0") double minimumLogit) {
    KnowledgeSettings {
        if (overlapTokens < 0 || overlapTokens >= targetTokens || targetTokens > maxTokens || maxTokens > 400
                || topK < 1 || topK > 15 || topN < 1 || topN > Math.min(topK, 5)
                || !Double.isFinite(minimumLogit)) {
            throw new IllegalArgumentException("知识库切片或检索参数无效");
        }
    }
}
