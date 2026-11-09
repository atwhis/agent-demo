package com.atwhis.agentdemo.ai.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

final class LocalEmbeddingModel implements EmbeddingModel {
    static final String QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";
    private final OnnxTextModel model;

    LocalEmbeddingModel(OnnxTextModel model) {
        this.model = model;
    }

    @Override
    public int dimensions() {
        return 512;
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> results = new ArrayList<>();
        for (String text : request.getInstructions()) {
            results.add(new Embedding(vector(text), results.size()));
        }
        return new EmbeddingResponse(results);
    }

    private float[] vector(String text) {
        float[] result = model.infer(model.encode(text), "sentence_embedding");
        if (result.length != dimensions()) {
            throw new IllegalStateException("Embedding 输出维度不匹配");
        }
        return result;
    }
}
