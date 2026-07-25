package com.example.globalnewsenginev1.stories.snapshot;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class StorySnapshotServicePostgresIT {

    private static final String VERSION_24 =
            "story-mvp-title-embedding-24h-v1.0.0";
    private static final String VERSION_48 =
            "story-mvp-title-embedding-48h-v1.0.0";

    private DataSource adminDataSource;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String schemaName;
    private Instant effectiveAt;
    private Instant watermark;
    private Clock clock;

    @BeforeEach
    void setUp() {
        adminDataSource = postgresDataSource(null);
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "Story snapshot test requires the local compose database");
        schemaName = "it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schemaName);
        dataSource = postgresDataSource(schemaName);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schemaName).defaultSchema(schemaName).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        effectiveAt = Instant.parse("2026-07-25T08:00:00Z");
        watermark = effectiveAt.plus(Duration.ofHours(26));
        clock = Clock.fixed(watermark.plusSeconds(60), ZoneOffset.UTC);
    }

    @AfterEach
    void tearDown() {
        if (adminDataSource != null && schemaName != null) {
            new JdbcTemplate(adminDataSource)
                    .execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void freezesReadyInputsPersistsExactPairsAndReusesRetriesAndHistory() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "b", vector(0.8f, 0.6f), effectiveAt, null);
        insertReadyInput(VERSION_24, "c", vector(0, 1), effectiveAt, null);
        insertReadyInput(VERSION_24, "d", vector(-1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "e", vector(0, -1),
                effectiveAt.plus(Duration.ofHours(25)), null);
        StorySnapshotService service = service();

        StorySnapshotService.ProcessingResult first =
                service.processBackfill(watermark, 1);

        assertThat(first.succeededVersions()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_snapshots", Integer.class)).isOne();
        long frozenSnapshot = jdbc.queryForObject(
                "SELECT id FROM story_snapshots", Long.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_snapshot_members WHERE snapshot_id = ?
                """, Integer.class, frozenSnapshot)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT candidate_count FROM story_processing_runs
                """, Long.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_pair_decisions WHERE result = 'SAME_STORY'
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_pair_decisions
                WHERE result = 'UNCERTAIN' AND top_one_below_threshold
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.query("""
                SELECT left_article_ref, right_article_ref, cosine_similarity,
                       candidate_rank, result
                FROM story_pair_decisions
                ORDER BY candidate_rank
                """, (resultSet, rowNum) -> List.of(
                resultSet.getString("left_article_ref"),
                resultSet.getString("right_article_ref"),
                resultSet.getBigDecimal("cosine_similarity").toPlainString(),
                Integer.toString(resultSet.getInt("candidate_rank")),
                resultSet.getString("result"))))
                .containsExactly(
                        List.of(ref("a"), ref("b"), "0.800000", "1", "SAME_STORY"),
                        List.of(ref("b"), ref("c"), "0.600000", "2", "UNCERTAIN"),
                        List.of(ref("c"), ref("d"), "0.000000", "3", "UNCERTAIN"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_pair_decisions
                WHERE left_article_ref >= right_article_ref
                """, Integer.class)).isZero();
        assertNoStoryOutputs();

        long originalRun = jdbc.queryForObject(
                "SELECT id FROM story_processing_runs", Long.class);
        long originalFencingToken = jdbc.queryForObject(
                "SELECT fencing_token FROM story_processing_runs", Long.class);
        jdbc.update("""
                UPDATE story_processing_runs SET status = 'FAILED'
                WHERE id = ?
                """, originalRun);
        StorySnapshotService.ProcessingResult resumed =
                service.processBackfill(watermark.plusSeconds(30), 1);
        assertThat(resumed.succeededVersions()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_processing_runs", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT id FROM story_processing_runs", Long.class)).isEqualTo(originalRun);
        assertThat(jdbc.queryForObject(
                "SELECT fencing_token FROM story_processing_runs", Long.class))
                .isGreaterThan(originalFencingToken);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_pair_decisions", Integer.class)).isEqualTo(3);

        StorySnapshotService.ProcessingResult retry =
                service.processBackfill(watermark, 1);
        assertThat(retry.reusedVersions()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_snapshots", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_processing_runs", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_pair_decisions", Integer.class)).isEqualTo(3);

        long oldInput = currentInput(VERSION_24, "a");
        jdbc.update("""
                UPDATE story_article_inputs
                SET current_marker = NULL, superseded_at = ?
                WHERE id = ?
                """, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), oldInput);
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, "changed");

        StorySnapshotService.ProcessingResult changed =
                service.processBackfill(watermark, 1);

        assertThat(changed.succeededVersions()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_snapshots", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_processing_runs", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_snapshot_members
                WHERE snapshot_id = ? AND article_input_id = ?
                """, Integer.class, frozenSnapshot, oldInput)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_snapshot_members WHERE snapshot_id = ?
                """, Integer.class, frozenSnapshot)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(DISTINCT snapshot_input_hash) FROM story_snapshots
                """, Integer.class)).isEqualTo(2);
        assertNoStoryOutputs();
    }

    @Test
    void concurrentWorkersCreateOneSnapshotRunAndPairSet() throws Exception {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "b", vector(0.8f, 0.6f), effectiveAt, null);
        StorySnapshotService first = service();
        StorySnapshotService second = service();
        CountDownLatch start = new CountDownLatch(1);
        Thread firstWorker = Thread.ofPlatform().start(
                () -> awaitAndRun(start, first));
        Thread secondWorker = Thread.ofPlatform().start(
                () -> awaitAndRun(start, second));

        start.countDown();
        firstWorker.join();
        secondWorker.join();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_snapshots", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_snapshot_members", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_processing_runs", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_pair_decisions", Integer.class)).isOne();
    }

    @Test
    void invalidVectorFailsOnlyItsVersionAndDoesNotReachSearch() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt,
                "corrupt-hash");
        insertReadyInput(VERSION_48, "b", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_48, "c", vector(0.8f, 0.6f), effectiveAt, null);

        StorySnapshotService.ProcessingResult result =
                service().processBackfill(watermark, 2);

        assertThat(result.failedVersions()).isOne();
        assertThat(result.succeededVersions()).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT run.status
                FROM story_processing_runs run
                JOIN story_clustering_versions version
                  ON version.id = run.clustering_version_id
                WHERE version.version_key = ?
                """, String.class, VERSION_24)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("""
                SELECT run.status
                FROM story_processing_runs run
                JOIN story_clustering_versions version
                  ON version.id = run.clustering_version_id
                WHERE version.version_key = ?
                """, String.class, VERSION_48)).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM story_pair_decisions decision
                JOIN story_clustering_versions version
                  ON version.id = decision.clustering_version_id
                WHERE version.version_key = ?
                """, Integer.class, VERSION_24)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM story_pair_decisions decision
                JOIN story_clustering_versions version
                  ON version.id = decision.clustering_version_id
                WHERE version.version_key = ?
                """, Integer.class, VERSION_48)).isOne();
    }

    private StorySnapshotService service() {
        StorySnapshotRepository repository = new StorySnapshotRepository(jdbc);
        return new StorySnapshotService(
                repository,
                new DataSourceTransactionManager(dataSource),
                clock,
                Duration.ofMinutes(30),
                new StorySnapshotMetrics(new SimpleMeterRegistry()));
    }

    private void awaitAndRun(
            CountDownLatch start,
            StorySnapshotService service
    ) {
        try {
            start.await();
            service.processBackfill(watermark, 1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void insertReadyInput(
            String versionKey,
            String suffix,
            byte[] bytes,
            Instant inputEffectiveAt,
            String variant
    ) {
        String articleRef = ref(suffix);
        Long articleId = jdbc.query("""
                SELECT id FROM articles WHERE url_hash = ?
                """, (resultSet, rowNum) -> resultSet.getLong(1), articleRef)
                .stream().findFirst().orElse(null);
        if (articleId == null) {
            jdbc.update("""
                    INSERT INTO articles (
                        canonical_url, url_hash, domain, first_seen_at, created_at, updated_at
                    ) VALUES (?, ?, 'example.org', ?, ?, ?)
                    """, "https://example.org/" + suffix, articleRef,
                    OffsetDateTime.ofInstant(effectiveAt, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(effectiveAt, ZoneOffset.UTC),
                    OffsetDateTime.ofInstant(effectiveAt, ZoneOffset.UTC));
            articleId = jdbc.queryForObject(
                    "SELECT id FROM articles WHERE url_hash = ?",
                    Long.class, articleRef);
        }
        long versionId = versionId(versionKey);
        String discriminator = versionKey + ":" + suffix + ":"
                + (variant == null ? "initial" : variant);
        String titleHash = StorySnapshotCanonicalizer.sha256(
                ("title:" + discriminator).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String vectorHash = "corrupt-hash".equals(variant)
                ? "f".repeat(64)
                : StorySnapshotCanonicalizer.sha256(bytes);
        jdbc.update("""
                INSERT INTO story_embedding_artifacts (
                    embedding_model_id, embedding_model_version, embedding_dimension,
                    title_normalization_version, title_input_hash, status,
                    vector_bytes, vector_hash, vector_norm, attempt_count,
                    ready_at, created_at, updated_at
                ) VALUES (
                    'text-embedding-3-small',
                    'openai:text-embedding-3-small@2026-07-20',
                    1536, 'art031-title-nfkc-ws-v1', ?, 'READY',
                    ?, ?, 1.0000000000, 1, ?, ?, ?
                )
                """, titleHash, bytes, vectorHash,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        long artifactId = jdbc.queryForObject("""
                SELECT id FROM story_embedding_artifacts WHERE title_input_hash = ?
                """, Long.class, titleHash);
        String fingerprint = StorySnapshotCanonicalizer.sha256(
                ("input:" + discriminator).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO story_article_inputs (
                    clustering_version_id, article_id, article_ref, effective_at,
                    effective_at_source, normalized_title, title_input_hash,
                    title_usability, article_input_fingerprint, embedding_status,
                    embedding_artifact_id, attempt_count, current_marker, created_at
                ) VALUES (?, ?, ?, ?, 'PUBLISHED_AT', ?, ?, 'USABLE', ?,
                          'READY', ?, 1, 1, ?)
                """, versionId, articleId, articleRef,
                OffsetDateTime.ofInstant(inputEffectiveAt, ZoneOffset.UTC),
                "Title " + suffix, titleHash, fingerprint, artifactId,
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private byte[] vector(float first, float second) {
        ByteBuffer buffer = ByteBuffer.allocate(1536 * Float.BYTES);
        buffer.putFloat(first);
        buffer.putFloat(second);
        while (buffer.hasRemaining()) {
            buffer.putFloat(0);
        }
        return buffer.array();
    }

    private long currentInput(String versionKey, String suffix) {
        return jdbc.queryForObject("""
                SELECT input.id
                FROM story_article_inputs input
                JOIN story_clustering_versions version
                  ON version.id = input.clustering_version_id
                WHERE version.version_key = ? AND input.article_ref = ?
                  AND input.current_marker = 1
                """, Long.class, versionKey, ref(suffix));
    }

    private long versionId(String versionKey) {
        return jdbc.queryForObject("""
                SELECT id FROM story_clustering_versions WHERE version_key = ?
                """, Long.class, versionKey);
    }

    private String ref(String suffix) {
        return suffix.repeat(64);
    }

    private void assertNoStoryOutputs() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stories", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_memberships", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_assignment_decisions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_lineage", Integer.class)).isZero();
    }

    private DataSource postgresDataSource(String schema) {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        String url = System.getProperty(
                "it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne");
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
}
