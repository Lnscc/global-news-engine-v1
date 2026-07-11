package com.example.globalnewsenginev1.articles.enrichment;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleEnrichmentRepositoryTests {

    private JdbcTemplate jdbcTemplate;
    private ArticleEnrichmentRepository repository;
    private Connection migrationConnection;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:enrichments-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        migrationConnection = dataSource.getConnection();
        {
            ScriptUtils.executeSqlScript(migrationConnection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
            ScriptUtils.executeSqlScript(migrationConnection,
                    new ClassPathResource("db/migration/V5__create_article_enrichments.sql"));
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new ArticleEnrichmentRepository(jdbcTemplate,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @AfterEach
    void tearDown() throws Exception {
        migrationConnection.close();
    }

    @Test
    void persistsSuccessAndClearsPreviousFailureState() {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        long articleId = insertArticle("success", now);
        assertThat(repository.enqueue(articleId, now)).isTrue();
        assertThat(repository.enqueue(articleId, now)).isFalse();

        assertThat(repository.claimDue(1, now)).containsExactly(
                new ClaimedArticleEnrichment(articleId, "https://example.org/success", 1));
        assertThat(repository.markFailed(articleId, "TIMEOUT", "temporary", now.plusSeconds(60), now))
                .isEqualTo(1);
        assertThat(repository.claimDue(1, now.plusSeconds(30))).isEmpty();
        assertThat(repository.claimDue(1, now.plusSeconds(60))).hasSize(1);

        ArticleEnrichmentResult result = new ArticleEnrichmentResult(
                "A title", now.minusSeconds(3600), "de-DE", "https://example.org/image.jpg", "Body");
        assertThat(repository.markSucceeded(articleId, result, now.plusSeconds(61))).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM article_enrichments WHERE article_id = ?", articleId);
        assertThat(row.get("STATUS")).isEqualTo("SUCCEEDED");
        assertThat(row.get("ATTEMPT_COUNT")).isEqualTo(2);
        assertThat(row.get("TITLE")).isEqualTo("A title");
        assertThat(row.get("EXTRACTED_TEXT")).isEqualTo("Body");
        assertThat(row.get("NEXT_ATTEMPT_AT")).isNull();
        assertThat(row.get("ERROR_CODE")).isNull();
        assertThat(row.get("ERROR_MESSAGE")).isNull();
    }

    @Test
    void enqueuesMissingArticlesIdempotentlyInLimitedBatches() {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        long first = insertArticle("auto-first", now);
        long second = insertArticle("auto-second", now);
        long third = insertArticle("auto-third", now);

        assertThat(repository.enqueueMissing(2, now)).isEqualTo(2);
        assertThat(repository.claimDue(10, now))
                .extracting(ClaimedArticleEnrichment::articleId)
                .containsExactly(first, second);

        assertThat(repository.enqueueMissing(2, now.plusSeconds(1))).isEqualTo(1);
        assertThat(repository.enqueueMissing(2, now.plusSeconds(2))).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM article_enrichments", Long.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM article_enrichments WHERE article_id = ?", String.class, third))
                .isEqualTo("PENDING");
    }

    @Test
    void distinguishesPermanentFailureFromRetryableFailure() {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        long articleId = insertArticle("permanent", now);
        repository.enqueue(articleId, now);
        repository.claimDue(1, now);

        repository.markFailed(articleId, "ROBOTS_DENIED", "permanent", null, now);

        assertThat(repository.claimDue(1, now.plusSeconds(86400))).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_attempt_at FROM article_enrichments WHERE article_id = ?",
                OffsetDateTime.class, articleId)).isNull();
    }

    @Test
    void databaseRejectsInvalidStatesAndServiceRejectsInvalidTransitions() {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        long articleId = insertArticle("invalid", now);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO article_enrichments
                    (article_id, status, attempt_count, created_at, updated_at)
                VALUES (?, 'UNKNOWN', 0, ?, ?)
                """, articleId, now.atOffset(java.time.ZoneOffset.UTC), now.atOffset(java.time.ZoneOffset.UTC)))
                .isInstanceOf(Exception.class);
        repository.enqueue(articleId, now);
        assertThat(repository.markSucceeded(articleId,
                new ArticleEnrichmentResult(null, null, null, null, null), now)).isZero();
    }

    @Test
    void parallelWorkersClaimEachEnrichmentOnlyOnce() throws Exception {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        for (int index = 0; index < 6; index++) {
            repository.enqueue(insertArticle("parallel-" + index, now), now);
        }
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<List<ClaimedArticleEnrichment>> first = executor.submit(() -> {
                start.await();
                return repository.claimDue(3, now);
            });
            Future<List<ClaimedArticleEnrichment>> second = executor.submit(() -> {
                start.await();
                return repository.claimDue(3, now);
            });
            start.countDown();

            List<Long> claimedIds = java.util.stream.Stream.concat(
                            first.get().stream(), second.get().stream())
                    .map(ClaimedArticleEnrichment::articleId)
                    .toList();
            assertThat(claimedIds).hasSize(6).doesNotHaveDuplicates();
        }
    }

    @Test
    void parallelWorkersEnqueueEachArticleOnlyOnce() throws Exception {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        for (int index = 0; index < 6; index++) {
            insertArticle("parallel-enqueue-" + index, now);
        }
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return repository.enqueueMissing(3, now);
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return repository.enqueueMissing(3, now);
            });
            start.countDown();

            assertThat(first.get() + second.get()).isEqualTo(6);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM article_enrichments", Long.class))
                    .isEqualTo(6);
        }
    }

    private long insertArticle(String suffix, Instant now) {
        OffsetDateTime timestamp = now.atOffset(java.time.ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO articles
                    (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (?, ?, 'example.org', ?, ?, ?)
                """, "https://example.org/" + suffix, "hash-" + suffix, timestamp, timestamp, timestamp);
        return jdbcTemplate.queryForObject("SELECT id FROM articles WHERE url_hash = ?", Long.class,
                "hash-" + suffix);
    }
}
