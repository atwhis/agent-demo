package com.atwhis.agentdemo.ai.knowledge;

import com.atwhis.agentdemo.ai.model.ModelRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/knowledge/documents")
@RequiredArgsConstructor
@Slf4j
class KnowledgeController {
    private final ObjectProvider<KnowledgeService> knowledge;
    private final ModelRegistry models;

    record TextInput(String title, String content, String documentType, String modelId) { }

    @PostMapping("/text")
    Mono<KnowledgeStore.ImportResult> text(@RequestBody Mono<TextInput> request) {
        return request.flatMap(this::importText);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    Mono<KnowledgeStore.ImportResult> upload(ServerWebExchange exchange) {
        return exchange.getMultipartData().flatMap(parts -> {
            if (parts.size() != 4 || parts.getOrDefault("file", java.util.List.of()).size() != 1
                    || !(parts.getFirst("file") instanceof FilePart file)
                    || !file.filename().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                return Mono.error(new ResponseStatusException(BAD_REQUEST, "请提交一个 UTF-8 TXT 文件。"));
            }
            String title = field(parts, "title");
            String type = field(parts, "documentType");
            String model = field(parts, "modelId");
            return DataBufferUtils.join(file.content(), KnowledgeService.MAX_BYTES).map(buffer -> {
                try {
                    return StandardCharsets.UTF_8.newDecoder().decode(buffer.asByteBuffer()).toString();
                } catch (CharacterCodingException ex) {
                    throw new ResponseStatusException(BAD_REQUEST, "TXT 文件必须使用 UTF-8 编码。");
                } finally {
                    DataBufferUtils.release(buffer);
                }
            }).defaultIfEmpty("").flatMap(content -> importText(new TextInput(title, content, type, model)));
        });
    }

    private static String field(MultiValueMap<String, Part> parts, String key) {
        if (parts.getOrDefault(key, java.util.List.of()).size() != 1 || !(parts.getFirst(key) instanceof FormFieldPart value)) {
            throw new ResponseStatusException(BAD_REQUEST, "缺少或重复的导入字段。");
        }
        return value.value();
    }

    private Mono<KnowledgeStore.ImportResult> importText(TextInput request) {
        return Mono.fromCallable(() -> {
            if (request.modelId() == null || request.modelId().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "请选择已启用的模型。");
            }
            var client = models.requireClient(request.modelId());
            var document = KnowledgeService.prepare(request.title(), request.content(), request.documentType());
            var service = knowledge.getIfAvailable();
            if (service == null) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "知识库功能当前未启用。");
            }
            return service.importText(document, client);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> error(Exception error) {
        if (error instanceof DataBufferLimitException) {
            return ResponseEntity.status(CONTENT_TOO_LARGE).body(Map.of("message", "正文不能超过 2 MiB。"));
        }
        if (error instanceof ResponseStatusException response) {
            String message = response.getReason();
            if (message == null || error.getClass() != ResponseStatusException.class) {
                message = "导入请求格式无效，请检查文件和输入内容。";
            }
            return ResponseEntity.status(response.getStatusCode()).body(Map.of("message", message));
        }
        if (error instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(Map.of("message", "导入参数无效，请检查所选模型。"));
        }
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(error);
        if (cause instanceof SQLException sql && "28P01".equals(sql.getSQLState())) {
            log.warn("Knowledge import rejected by PostgreSQL (SQLSTATE 28P01)");
            return ResponseEntity.status(SERVICE_UNAVAILABLE).body(Map.of("message",
                    "知识库数据库认证失败，请检查 AI 服务的 KNOWLEDGE_DB_USER 和 KNOWLEDGE_DB_PASSWORD。"));
        }
        log.warn("Knowledge import failed: {} (root cause: {})",
                error.getClass().getSimpleName(), cause.getClass().getSimpleName());
        return ResponseEntity.status(SERVICE_UNAVAILABLE)
                .body(Map.of("message", "导入失败，请检查知识库连接和本地模型配置后重试。"));
    }
}
