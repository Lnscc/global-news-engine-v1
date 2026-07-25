package com.example.globalnewsenginev1.stories.embedding;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StoryEmbeddingServicePostgresIT {

    private DataSource adminDataSource;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String schemaName;
    private FakeClient client;
    private StoryEmbeddingService service;
    private StoryEmbeddingRepository repository;
    private OffsetDateTime timestamp;

    @BeforeEach
    void setUp() {
        adminDataSource = postgresDataSource(null);
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "Story embedding test requires the local compose database");
        schemaName = "it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schemaName);
        dataSource = postgresDataSource(schemaName);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schemaName).defaultSchema(schemaName).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        client = new FakeClient();
        repository = new StoryEmbeddingRepository(jdbc);
        service = new StoryEmbeddingService(
                repository, new StoryTitleNormalizer(), client,
                new DataSourceTransactionManager(dataSource),
                new StoryEmbeddingMetrics(new SimpleMeterRegistry(), repository));
        timestamp = OffsetDateTime.parse("2026-07-23T10:00:00Z");
    }

    @AfterEach
    void tearDown() {
        if (adminDataSource != null && schemaName != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void createsNotRequiredAndSharedReadyInputsIdempotentlyAndHistoriesChanges() {
        long first = insertArticle("first");
        long second = insertArticle("second");
        insertArticle("missing");
        insertArticle("generic");
        insertGkg(first, 101, "News &amp; Analysis", timestamp.plusHours(2));
        insertGkg(second, 102, "News & Analysis", null);
        long generic = articleId("generic");
        insertGkg(generic, 103, "NPR News", null);

        StoryEmbeddingService.ProcessingResult firstRun = service.processIncremental(100);

        assertThat(firstRun.failed()).isZero();
        assertThat(client.calls()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_embedding_artifacts", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_embedding_attempts WHERE status = 'READY'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_article_inputs WHERE current_marker = 1", Integer.class))
                .isEqualTo(12);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs
                WHERE title_usability = 'TITLE_MISSING' AND embedding_status = 'NOT_REQUIRED'
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs
                WHERE title_usability = 'TITLE_GENERIC' AND embedding_status = 'NOT_REQUIRED'
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT embedding_artifact_id) FROM story_article_inputs
                WHERE title_usability = 'USABLE'
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs
                WHERE article_id = ? AND effective_at_source = 'PUBLISHED_AT'
                """, Integer.class, first)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs
                WHERE article_id = ? AND effective_at_source = 'FIRST_SEEN_AT'
                """, Integer.class, second)).isEqualTo(3);
        assertThat(service.processIncremental(100).selected()).isZero();
        assertThat(client.calls()).isOne();

        long readyArtifact = jdbc.queryForObject(
                "SELECT id FROM story_embedding_artifacts", Long.class);
        String originalHash = jdbc.queryForObject(
                "SELECT vector_hash FROM story_embedding_artifacts WHERE id = ?",
                String.class, readyArtifact);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status ->
                repository.recordModelDrift(readyArtifact, 2, "request-drift", 1536,
                        "f".repeat(64), java.time.Instant.now(), java.time.Instant.now()));
        assertThat(jdbc.queryForObject(
                "SELECT vector_hash FROM story_embedding_artifacts WHERE id = ?",
                String.class, readyArtifact)).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_embedding_attempts
                WHERE embedding_artifact_id = ? AND status = 'MODEL_DRIFT'
                """, Integer.class, readyArtifact)).isOne();

        jdbc.update("""
                UPDATE gdelt_gkg
                SET page_title = 'Changed title', created_at = CURRENT_TIMESTAMP + INTERVAL '1 hour'
                WHERE article_id = ?
                """, first);
        service.processIncremental(100);

        assertThat(client.calls()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs
                WHERE article_id = ? AND current_marker IS NULL AND superseded_at IS NOT NULL
                """, Integer.class, first)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs
                WHERE article_id = ? AND current_marker = 1
                """, Integer.class, first)).isEqualTo(3);
    }

    @Test
    void persistsRetryAfterThenRejectsAnInvalidRetryVector() {
        long article = insertArticle("retry");
        insertGkg(article, 301, "Retry title", null);
        client.retryAfter = java.time.Duration.ofMinutes(2);

        service.processIncremental(100);

        OffsetDateTime firstAttemptCompleted = jdbc.queryForObject("""
                SELECT completed_at FROM story_embedding_attempts WHERE attempt_number = 1
                """, OffsetDateTime.class);
        OffsetDateTime nextRetry = jdbc.queryForObject("""
                SELECT next_retry_at FROM story_embedding_artifacts
                """, OffsetDateTime.class);
        assertThat(nextRetry).isEqualTo(firstAttemptCompleted.plusMinutes(2));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM story_embedding_artifacts
                """, String.class)).isEqualTo("RETRYABLE_FAILURE");

        jdbc.update("""
                UPDATE story_embedding_artifacts SET next_retry_at = CURRENT_TIMESTAMP
                """);
        jdbc.update("""
                UPDATE story_article_inputs SET next_retry_at = CURRENT_TIMESTAMP
                """);
        client.retryAfter = null;
        client.invalidDimension = true;
        service.processIncremental(100);

        assertThat(jdbc.queryForObject("""
                SELECT status FROM story_embedding_artifacts
                """, String.class)).isEqualTo("TERMINAL_FAILURE");
        assertThat(jdbc.queryForObject("""
                SELECT error_code FROM story_embedding_attempts WHERE attempt_number = 2
                """, String.class)).isEqualTo("DIMENSION_MISMATCH");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_embedding_artifacts WHERE status = 'READY'
                """, Integer.class)).isZero();
    }

    @Test
    void concurrentWorkersDoNotDuplicateArtifactsInputsOrModelCalls() throws Exception {
        long article = insertArticle("concurrent");
        insertGkg(article, 201, "Concurrent title", null);
        CountDownLatch start = new CountDownLatch(1);
        Thread first = Thread.ofPlatform().start(() -> awaitAndProcess(start));
        Thread second = Thread.ofPlatform().start(() -> awaitAndProcess(start));

        start.countDown();
        first.join();
        second.join();

        assertThat(client.calls()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_embedding_artifacts", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs WHERE current_marker = 1
                """, Integer.class)).isEqualTo(3);
    }

    private void awaitAndProcess(CountDownLatch start) {
        try {
            start.await();
            service.processIncremental(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private long insertArticle(String suffix) {
        jdbc.update("""
                INSERT INTO articles (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (?, ?, 'example.org', ?, ?, ?)
                """, "https://example.org/" + suffix, hash(suffix), timestamp, timestamp, timestamp);
        return articleId(suffix);
    }

    private long articleId(String suffix) {
        return jdbc.queryForObject("SELECT id FROM articles WHERE url_hash = ?",
                Long.class, hash(suffix));
    }

    private void insertGkg(long articleId, long id, String title, OffsetDateTime publishedAt) {
        String sourceFile = "gkg-" + id + ".zip";
        jdbc.update("""
                INSERT INTO gdelt_import_files (
                    dataset_type, source_file, source_url, source_timestamp,
                    status, started_at, completed_at
                ) VALUES ('GKG', ?, ?, ?, 'COMPLETED', ?, ?)
                """, sourceFile, "https://example.org/" + sourceFile,
                timestamp, timestamp, timestamp);
        long importId = jdbc.queryForObject("""
                SELECT id FROM gdelt_import_files WHERE source_file = ?
                """, Long.class, sourceFile);
        jdbc.update("""
                INSERT INTO gdelt_gkg (
                    id, import_file_id, source_file, source_timestamp, row_number,
                    ingested_at, parsed_at, gkg_record_id, page_title,
                    page_precise_pub_timestamp, article_id, themes, persons,
                    organizations, locations, created_at
                ) VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?,
                          ARRAY[]::TEXT[], ARRAY[]::TEXT[], ARRAY[]::TEXT[], '[]'::JSONB, ?)
                """, id, importId, sourceFile, timestamp, timestamp, timestamp,
                "record-" + id, title, publishedAt, articleId, timestamp);
    }

    private String hash(String value) {
        return StoryTitleNormalizer.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private DataSource postgresDataSource(String schema) {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        String url = System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne");
        if (schema != null) {
            url += (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        }
        postgres.setUrl(url);
        postgres.setUser(System.getProperty("it.postgres.username", "gne"));
        postgres.setPassword(System.getProperty("it.postgres.password", "gne"));
        return postgres;
    }

    private boolean canConnect(DataSource candidate) {
        try (var ignored = candidate.getConnection()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    private static final class FakeClient implements EmbeddingClient {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile java.time.Duration retryAfter;
        private volatile boolean invalidDimension;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<EmbeddingResponse> embed(String modelId, int dimension, List<String> inputs) {
            calls.incrementAndGet();
            if (retryAfter != null) {
                throw new EmbeddingClientException("RATE_LIMIT", "rate limited",
                        true, retryAfter, "request-" + calls.get(), null);
            }
            if (invalidDimension) {
                return List.of(new EmbeddingResponse(0, new float[]{1}, "request-" + calls.get()));
            }
            float[] vector = new float[dimension];
            vector[0] = 3;
            vector[1] = 4;
            return List.of(new EmbeddingResponse(0, vector, "request-" + calls.get()));
        }

        int calls() {
            return calls.get();
        }
    }
}
