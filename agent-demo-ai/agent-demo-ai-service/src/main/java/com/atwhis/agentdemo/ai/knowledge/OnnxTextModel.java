package com.atwhis.agentdemo.ai.knowledge;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class OnnxTextModel implements AutoCloseable {
    static final int INPUT_LIMIT = 512;
    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Path extracted;

    OnnxTextModel(ResourceLoader resources, String directory, String model, String... externalFiles)
            throws IOException, OrtException {
        Resource graph = resources.getResource(directory + "/" + model);
        Path temporary = graph.isFile() ? null : Files.createTempDirectory("agent-onnx-");
        HuggingFaceTokenizer loaded = null;
        try {
            Path modelPath = localFile(graph, temporary, model);
            for (String filename : externalFiles) {
                localFile(resources.getResource(directory + "/" + filename), temporary, filename);
            }
            Path tokenizerPath = localFile(resources.getResource(directory + "/tokenizer.json"), temporary, "tokenizer.json");
            // Each forward pass has one row, so there is no padding; check lengths before inference.
            loaded = HuggingFaceTokenizer.builder().optTokenizerPath(tokenizerPath)
                    .optPadding(false).optTruncation(false).optMaxLength(INPUT_LIMIT).build();
            environment = OrtEnvironment.getEnvironment();
            try (var options = new OrtSession.SessionOptions()) {
                options.setIntraOpNumThreads(2);
                session = environment.createSession(modelPath.toString(), options);
            }
        } catch (IOException | OrtException | RuntimeException | LinkageError error) {
            if (loaded != null) {
                loaded.close();
            }
            if (temporary != null) {
                FileSystemUtils.deleteRecursively(temporary.toFile());
            }
            throw error;
        }
        extracted = temporary;
        tokenizer = loaded;
    }

    private static Path localFile(Resource resource, Path directory, String filename) throws IOException {
        if (directory == null) {
            return resource.getFile().toPath();
        }
        Path destination = directory.resolve(filename);
        try (var input = resource.getInputStream()) {
            Files.copy(input, destination);
        }
        return destination;
    }

    int tokens(String text) {
        return tokenizer.encode(text, false, false).getIds().length;
    }

    Encoding encode(String text) {
        return tokenizer.encode(text);
    }

    Encoding pair(String question, String passage) {
        return tokenizer.encode(question, passage);
    }

    float[] infer(Encoding encoding, String output) {
        if (encoding.getIds().length > INPUT_LIMIT) {
            throw new IllegalArgumentException("本地模型输入过长，请精简问题或重新切片。");
        }
        Map<String, OnnxTensor> tensors = new LinkedHashMap<>();
        try {
            tensors.put("input_ids", tensor(encoding.getIds()));
            tensors.put("attention_mask", tensor(encoding.getAttentionMask()));
            if (session.getInputNames().contains("token_type_ids")) {
                tensors.put("token_type_ids", tensor(encoding.getTypeIds()));
            }
            try (var result = session.run(tensors, Set.of(output))) {
                return ((float[][]) result.get(output).orElseThrow().getValue())[0];
            }
        } catch (OrtException ex) {
            throw new IllegalStateException("本地 ONNX 推理失败", ex);
        } finally {
            tensors.values().forEach(OnnxTensor::close);
        }
    }

    private OnnxTensor tensor(long[] values) throws OrtException {
        return OnnxTensor.createTensor(environment, new long[][]{values});
    }

    @Override
    public void close() throws OrtException {
        try {
            session.close();
        } finally {
            tokenizer.close();
            if (extracted != null) {
                FileSystemUtils.deleteRecursively(extracted.toFile());
            }
        }
    }
}
