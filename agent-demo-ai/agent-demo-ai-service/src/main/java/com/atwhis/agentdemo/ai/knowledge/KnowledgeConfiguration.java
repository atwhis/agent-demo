package com.atwhis.agentdemo.ai.knowledge;

import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeSettings.class)
@ConditionalOnProperty(prefix = "agent-demo.ai.knowledge", name = "enabled", havingValue = "true")
class KnowledgeConfiguration {
    @Bean
    @Lazy
    OnnxTextModel embeddingEncoder(ResourceLoader resources, KnowledgeSettings settings) throws Exception {
        return new OnnxTextModel(resources, settings.embeddingDirectory(), "model.onnx", "model.onnx_data");
    }

    @Bean
    @Lazy
    OnnxTextModel rerankingEncoder(ResourceLoader resources, KnowledgeSettings settings) throws Exception {
        return new OnnxTextModel(resources, settings.rerankerDirectory(), "model_qint8_arm64.onnx");
    }

    @Bean
    @Lazy
    LocalEmbeddingModel knowledgeEmbedding(@Qualifier("embeddingEncoder") OnnxTextModel encoder) {
        return new LocalEmbeddingModel(encoder);
    }

    @Bean
    @Lazy
    KnowledgeStore knowledgeStore(KnowledgeSettings settings, LocalEmbeddingModel embedding) throws Exception {
        var source = new PGSimpleDataSource();
        source.setUrl(settings.url());
        source.setUser(settings.username());
        source.setPassword(settings.password());
        source.setConnectTimeout(5);
        source.setSocketTimeout(30);
        return new KnowledgeStore(new JdbcTemplate(source), embedding);
    }

    @Bean
    @Lazy
    KnowledgeService knowledgeService(KnowledgeStore store, KnowledgeSettings settings,
                                      @Qualifier("embeddingEncoder") OnnxTextModel embedding,
                                      @Qualifier("rerankingEncoder") OnnxTextModel reranker) {
        return new KnowledgeService(store, settings, embedding, reranker);
    }
}
