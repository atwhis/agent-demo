package com.atwhis.agentdemo.ai.knowledge;

import com.pgvector.PGvector;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class KnowledgeStore {
    private static final String CHUNK_TABLE = "knowledge_chunks";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CHUNK_METADATA = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final EmbeddingModel embedding;
    private final PgVectorStore vectors;
    private final TransactionTemplate transaction;

    KnowledgeStore(JdbcTemplate jdbc, EmbeddingModel embedding) throws Exception {
        this.jdbc = jdbc;
        this.embedding = embedding;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        vectors = PgVectorStore.builder(jdbc, embedding).dimensions(512)
                .vectorTableName(CHUNK_TABLE)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW).initializeSchema(true).build();
        vectors.afterPropertiesSet();
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_documents (
                  id uuid PRIMARY KEY, title varchar(80) NOT NULL, content text NOT NULL,
                  document_type text NOT NULL, content_hash char(64) NOT NULL UNIQUE,
                  chunk_count integer NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
                )
                """);
    }

    record ImportResult(UUID documentId, String title, String documentType, int chunkCount, boolean duplicate) { }

    Optional<ImportResult> existing(String hash) {
        return jdbc.query("""
                SELECT id, title, document_type, chunk_count FROM knowledge_documents WHERE content_hash = ?
                """, (row, i) -> new ImportResult(row.getObject("id", UUID.class), row.getString("title"),
                row.getString("document_type"), row.getInt("chunk_count"), true), hash).stream().findFirst();
    }

    ImportResult save(UUID id, String title, String content, String type, String hash, List<Document> chunks) {
        return transaction.execute(status -> {
            int inserted = jdbc.update("""
                    INSERT INTO knowledge_documents(id,title,content,document_type,content_hash,chunk_count)
                    VALUES (?,?,?,?,?,?) ON CONFLICT (content_hash) DO NOTHING
                    """, id, title, content, type, hash, chunks.size());
            if (inserted == 0) {
                return existing(hash).orElseThrow();
            }
            vectors.add(chunks);
            return new ImportResult(id, title, type, chunks.size(), false);
        });
    }

    float[] embedQuery(String query) {
        return embedding.embed(LocalEmbeddingModel.QUERY_PREFIX + query);
    }

    List<Document> recall(float[] query, int topK) {
        PGvector vector = new PGvector(query);
        // Match Spring AI PgVectorStore's cosine search with a similarity threshold of zero.
        return jdbc.query("""
                SELECT id, content, metadata, embedding <=> ? AS distance
                FROM public.%s WHERE embedding <=> ? < 1 ORDER BY distance LIMIT ?
                """.formatted(CHUNK_TABLE), (row, i) -> Document.builder()
                .id(row.getString("id")).text(row.getString("content"))
                .metadata(JSON.readValue(row.getString("metadata"), CHUNK_METADATA))
                .score(1.0 - row.getFloat("distance")).build(), vector, vector, topK);
    }
}
