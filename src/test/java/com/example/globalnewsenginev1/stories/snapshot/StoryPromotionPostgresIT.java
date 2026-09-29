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
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryPromotionPostgresIT {

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
    void promotesPreservesIdsAndContinuesActiveProcessing() {
        seed(VERSION_24, false);
        seed(VERSION_48, false);
        publish();
        var promotion = promotion();
        var first = promotion.review(versionId(VERSION_24), holdout(false));
        assertThat(first.evaluation().passed()).isTrue();
        assertThat(promotion.promote(first, holdout(false), approval())).isEqualTo(StoryPromotionService.Outcome.PROMOTED);
        Map<String, UUID> before = publicAssignments(VERSION_24);
        var review = promotion.review(versionId(VERSION_48), holdout(false));
        assertThat(review.diff().changedMemberships()).isZero();
        assertThat(review).isEqualTo(promotion.review(versionId(VERSION_48), holdout(false)));
        assertThat(promotion.promote(review, holdout(false), approval())).isEqualTo(StoryPromotionService.Outcome.PROMOTED);
        assertThat(publicAssignments(VERSION_48)).isEqualTo(before);
        assertThat(promotion.promote(review, null, null)).isEqualTo(StoryPromotionService.Outcome.ALREADY_PROMOTED);
        assertThat(promotion.promote(first, null, null)).isEqualTo(StoryPromotionService.Outcome.ALREADY_PROMOTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_clustering_versions WHERE status = 'ACTIVE'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_clustering_version_status_history WHERE previous_status IS NOT NULL", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT reason FROM story_clustering_version_status_history WHERE clustering_version_id = ? AND new_status = 'ACTIVE'",
                String.class, versionId(VERSION_48))).contains("test-reviewer", "story-release-gates-v1", "Synthetic integration fixture");
        UUID publicId = before.get(ref("a0"));
        assertThat(promotion.resolvePublicId(publicId)).contains(jdbc.queryForObject(
                "SELECT id FROM stories WHERE clustering_version_id = ? AND public_id = ?", UUID.class, versionId(VERSION_48), publicId));
        insertReadyInput(VERSION_48, "a6", vector(1, 0), effectiveAt, null);
        assertThat(service().processBackfill(watermark.plusSeconds(1), 1).succeededVersions()).isOne();
        assertThat(publicAssignments(VERSION_48).get(ref("a6"))).isEqualTo(publicId);
    }

    @Test
    void preservesAnchorsAndMergeSplitRules() {
        seed(VERSION_24, false);
        seed(VERSION_48, true);
        publish();
        var promotion = promotion();
        promotion.promote(promotion.review(versionId(VERSION_24), holdout(false)), holdout(false), approval());
        Map<String, UUID> before = publicAssignments(VERSION_24);
        var review = promotion.review(versionId(VERSION_48), holdout(false));
        assertThat(review.diff().splits()).isOne();
        assertThat(review.diff().merges()).isOne();
        promotion.promote(review, holdout(false), approval());
        var after = publicAssignments(VERSION_48);
        // x0 is the old x component's identity anchor (fixture uses effective time to order it first).
        assertThat(after.get(ref("x0"))).isIn(before.get(ref("x0")), before.get(ref("y0")));
        assertThat(after.get(ref("x0"))).isEqualTo(after.get(ref("y0")));
        assertThat(after.get(ref("x1"))).isNotIn(before.values());
        assertThat(after.get(ref("a0"))).isEqualTo(before.get(ref("a0")));
        assertThat(service().processBackfill(watermark.plusSeconds(1), 1).succeededVersions()).isOne();
        assertThat(publicAssignments(VERSION_48)).isEqualTo(after);
    }

    @Test
    void rejectsFailedGatesMissingApprovalAndChangedEvidenceWithoutWrites() {
        seed(VERSION_24, false);
        publish();
        var promotion = promotion();
        var bad = promotion.review(versionId(VERSION_24), holdout(true));
        assertThat(bad.evaluation().passed()).isFalse();
        assertThatThrownBy(() -> promotion.promote(bad, holdout(true), approval())).hasMessageContaining("gates failed");
        var good = promotion.review(versionId(VERSION_24), holdout(false));
        assertThatThrownBy(() -> promotion.promote(good, holdout(false), new StoryPromotionService.Approval("", "doc", "reason")))
                .hasMessageContaining("Approver");
        assertThatThrownBy(() -> promotion.promote(good, holdout(true), approval())).hasMessageContaining("changed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_clustering_versions WHERE status = 'ACTIVE'", Integer.class)).isZero();
    }

    @Test
    void requiresNewReviewAfterSourceOrCandidatePublicationChanges() {
        seed(VERSION_24, false);
        seed(VERSION_48, false);
        publish();
        var promotion = promotion();
        promotion.promote(promotion.review(versionId(VERSION_24), holdout(false)), holdout(false), approval());
        var reviewed = promotion.review(versionId(VERSION_48), holdout(false));
        assertThat(service().processBackfill(watermark.plusSeconds(1), 2).succeededVersions()).isEqualTo(2);
        assertThatThrownBy(() -> promotion.promote(reviewed, holdout(false), approval())).hasMessageContaining("changed");
        var fresh = promotion.review(versionId(VERSION_48), holdout(false));
        assertThat(promotion.promote(fresh, holdout(false), approval())).isEqualTo(StoryPromotionService.Outcome.PROMOTED);
    }

    @Test
    void rejectsUnpublishedOrStaleInputs() {
        assertThatThrownBy(() -> promotion().review(versionId(VERSION_24), holdout(false))).hasMessageContaining("no published snapshot");
        seed(VERSION_24, false);
        publish();
        insertReadyInput(VERSION_24, "new", vector(1, 0), effectiveAt, null);
        assertThatThrownBy(() -> promotion().review(versionId(VERSION_24), holdout(false))).hasMessageContaining("inputs have changed");
    }

    @Test
    void rollsBackIdsStatusAndAuditIfWriteFails() {
        seed(VERSION_24, false);
        seed(VERSION_48, false);
        publish();
        var promotion = promotion();
        promotion.promote(promotion.review(versionId(VERSION_24), holdout(false)), holdout(false), approval());
        var review = promotion.review(versionId(VERSION_48), holdout(false));
        var before = publicAssignments(VERSION_48);
        jdbc.execute("""
                CREATE FUNCTION reject_release() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected audit failure'; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_release BEFORE INSERT ON story_clustering_version_status_history FOR EACH ROW EXECUTE FUNCTION reject_release()");
        assertThatThrownBy(() -> promotion.promote(review, holdout(false), approval())).hasMessageContaining("injected audit failure");
        assertThat(publicAssignments(VERSION_48)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT id FROM story_clustering_versions WHERE status = 'ACTIVE'", Long.class)).isEqualTo(versionId(VERSION_24));
        assertThat(promotion.review(versionId(VERSION_48), holdout(false))).isEqualTo(review);
    }

    @Test
    void concurrentRepeatedPromotionCommitsOnce() throws Exception {
        seed(VERSION_24, false);
        publish();
        var promotion = promotion();
        var review = promotion.review(versionId(VERSION_24), holdout(false));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<StoryPromotionService.Outcome>) () -> {
                start.await();
                return promotion.promote(review, holdout(false), approval());
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(StoryPromotionService.Outcome.PROMOTED, StoryPromotionService.Outcome.ALREADY_PROMOTED);
        }
    }

    @Test
    void competingVersionsRequireFreshReviewAndReadersSeeAtomicSwitch() throws Exception {
        seed(VERSION_24, false);
        seed(VERSION_48, false);
        publish();
        var promotion = promotion();
        var first = promotion.review(versionId(VERSION_24), holdout(false));
        var stale = promotion.review(versionId(VERSION_48), holdout(false));
        promotion.promote(first, holdout(false), approval());
        assertThatThrownBy(() -> promotion.promote(stale, holdout(false), approval())).hasMessageContaining("changed");
        var review = promotion.review(versionId(VERSION_48), holdout(false));
        UUID publicId = publicAssignments(VERSION_24).get(ref("a0"));
        UUID oldInternalId = promotion.resolvePublicId(publicId).orElseThrow();
        jdbc.execute("""
                CREATE FUNCTION pause_release() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN PERFORM pg_advisory_xact_lock(390040); RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER pause_release BEFORE INSERT ON story_clustering_version_status_history FOR EACH ROW EXECUTE FUNCTION pause_release()");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var executor = Executors.newSingleThreadExecutor()) {
            statement.execute("SELECT pg_advisory_lock(390040)");
            var pending = executor.submit(() -> promotion.promote(review, holdout(false), approval()));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT EXISTS (SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND objid = 390040 AND NOT granted)", Boolean.class));
                    if (waiting) break;
                    Thread.sleep(10);
                }
                assertThat(waiting).isTrue();
                assertThat(promotion.resolvePublicId(publicId)).contains(oldInternalId);
                assertThat(jdbc.queryForObject("SELECT id FROM story_clustering_versions WHERE status = 'ACTIVE'", Long.class))
                        .isEqualTo(versionId(VERSION_24));
            } finally {
                statement.execute("SELECT pg_advisory_unlock(390040)");
            }
            assertThat(pending.get(10, TimeUnit.SECONDS)).isEqualTo(StoryPromotionService.Outcome.PROMOTED);
            assertThat(promotion.resolvePublicId(publicId).orElseThrow()).isNotEqualTo(oldInternalId);
        }
    }

    @Test
    void rejectsDimensionMismatchEvenWithSuccessfulPublishRecord() {
        seed(VERSION_24, false);
        publish();
        // Simulate corrupt imported artifacts; normal writes cannot modify READY artifacts.
        jdbc.execute("ALTER TABLE story_embedding_artifacts DISABLE TRIGGER trg_story_embedding_artifacts_immutable");
        jdbc.update("UPDATE story_embedding_artifacts SET embedding_dimension = 2, vector_bytes = substring(vector_bytes FROM 1 FOR 8)");
        jdbc.execute("ALTER TABLE story_embedding_artifacts ENABLE TRIGGER trg_story_embedding_artifacts_immutable");
        assertThatThrownBy(() -> promotion().review(versionId(VERSION_24), holdout(false)))
                .hasMessageContaining("incomplete or incompatible");
    }

    private StoryPromotionService promotion() {
        var repository = new StorySnapshotRepository(jdbc);
        var manager = new DataSourceTransactionManager(dataSource);
        return new StoryPromotionService(jdbc, repository,
                new StoryPublisher(jdbc, repository, manager, new SimpleMeterRegistry()), manager);
    }

    private void seed(String version, boolean changed) {
        for (int i = 0; i < 6; i++) {
            insertReadyInput(version, "a" + i, vector(1, 0), effectiveAt, null);
            insertReadyInput(version, "b" + i, vector(0, 1), effectiveAt, null);
        }
        insertReadyInput(version, "x0", vector(-1, 0), effectiveAt, null);
        insertReadyInput(version, "x1", changed ? vector(0, -1) : vector(-1, 0), effectiveAt.plusSeconds(1), null);
        insertReadyInput(version, "y0", changed ? vector(-1, 0) : vector(0, -1), effectiveAt.plusSeconds(2), null);
        insertReadyInput(version, "y1", changed ? vector(-1, 0) : vector(0, -1), effectiveAt.plusSeconds(3), null);
    }

    private void publish() {
        assertThat(service().processBackfill(watermark, 2).failedVersions()).isZero();
    }

    private StoryReleaseEvaluation.Holdout holdout(boolean invert) {
        List<StoryReleaseEvaluation.Pair> pairs = new ArrayList<>();
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 6; i++) { refs.add("a" + i); refs.add("b" + i); }
        for (int i = 0; i < refs.size(); i++) for (int j = i + 1; j < refs.size(); j++) {
            String left = ref(refs.get(i)), right = ref(refs.get(j));
            boolean same = (refs.get(i).charAt(0) == refs.get(j).charAt(0)) != invert;
            pairs.add(new StoryReleaseEvaluation.Pair(left.compareTo(right) < 0 ? left : right,
                    left.compareTo(right) < 0 ? right : left, same, "Synthetic label for automated test only"));
        }
        return new StoryReleaseEvaluation.Holdout("synthetic-v1", "Synthetic integration fixture",
                "test", "Independent synthetic data, not production release evidence", pairs);
    }

    private StoryPromotionService.Approval approval() {
        return new StoryPromotionService.Approval("test-reviewer", "test://approval", "Integration test only");
    }

    private Map<String, UUID> publicAssignments(String version) {
        Map<String, UUID> result = new HashMap<>();
        jdbc.query("""
                SELECT member.article_ref, story.public_id FROM story_memberships member
                JOIN stories story ON story.id = member.story_id
                WHERE member.clustering_version_id = ? AND member.current_marker = 1
                """, rs -> { result.put(rs.getString(1), rs.getObject(2, UUID.class)); }, versionId(version));
        return result;
    }

    private StorySnapshotService service() {
        return service(clock);
    }

    private StorySnapshotService service(Clock serviceClock) {
        StorySnapshotRepository repository = new StorySnapshotRepository(jdbc);
        return new StorySnapshotService(
                repository,
                new DataSourceTransactionManager(dataSource),
                serviceClock,
                Duration.ofMinutes(30),
                new StorySnapshotMetrics(new SimpleMeterRegistry()),
                new StoryPublisher(jdbc, repository, new DataSourceTransactionManager(dataSource),
                        new SimpleMeterRegistry()));
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
        return StoryReleaseEvaluation.hash(suffix);
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
