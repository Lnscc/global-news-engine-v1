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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        StoryPartitionService partitionService = new StoryPartitionService(new StorySnapshotRepository(jdbc));
        StoryPartitionService.Partition partition = partitionService.calculate(frozenSnapshot);
        assertThat(partition.snapshot().versionKey()).isEqualTo(VERSION_24);
        assertThat(partition.components()).extracting(StoryPartitionService.Component::medoidArticleRef)
                .containsExactly(ref("a"), ref("c"), ref("d"), ref("e"));
        assertThat(partition.components().getFirst().members())
                .extracting(StoryPartitionService.MemberEvidence::articleRef)
                .containsExactly(ref("a"), ref("b"));
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
        assertPublishedStories();

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
        assertThat(resumed.reusedVersions()).isOne();
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
        assertThat(partitionService.calculate(frozenSnapshot)).isEqualTo(partition);
        assertPublishedStories();
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
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_publish_commits", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stories", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_memberships WHERE current_marker = 1", Integer.class)).isEqualTo(2);
    }

    @Test
    void freshRunBlocksANewSnapshotAndStaleRunResumesTheExistingSnapshot() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "b", vector(0.8f, 0.6f), effectiveAt, null);
        StorySnapshotRepository repository = new StorySnapshotRepository(jdbc);
        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        StorySnapshotRepository.RunClaim originalClaim =
                new TransactionTemplate(transactionManager).execute(status -> {
                    StorySnapshotRepository.ClusteringVersion version =
                            repository.findShadowVersions(1).getFirst();
                    repository.lockVersion(version.id());
                    List<StorySnapshotRepository.SnapshotInput> inputs =
                            repository.findSnapshotInputs(version.id(), watermark);
                    String inputHash = StorySnapshotCanonicalizer.snapshotInputHash(
                            version.key(), watermark, inputs);
                    String snapshotKey = StorySnapshotCanonicalizer.snapshotKey(
                            version.key(), watermark, inputHash);
                    StorySnapshotRepository.Snapshot snapshot =
                            repository.ensureSnapshot(version, watermark, snapshotKey,
                                    inputHash, inputs, clock.instant());
                    String runKey = StorySnapshotCanonicalizer.runKey(
                            version.key(), inputHash,
                            StorySnapshotService.RunMode.INCREMENTAL,
                            version.pairRuleVersion());
                    return repository.claimRun(version, snapshot,
                            StorySnapshotService.RunMode.INCREMENTAL, runKey,
                            clock.instant(), Duration.ofMinutes(30)).orElseThrow();
                });

        StorySnapshotService.ProcessingResult blocked = service().process(
                StorySnapshotService.RunMode.INCREMENTAL,
                watermark.plusSeconds(300), 1);

        assertThat(blocked.reusedVersions()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_snapshots", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_processing_runs", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM story_processing_runs", String.class))
                .isEqualTo("RUNNING");

        Clock afterTimeout = Clock.fixed(
                clock.instant().plus(Duration.ofMinutes(31)), ZoneOffset.UTC);
        StorySnapshotService.ProcessingResult resumed =
                service(afterTimeout).process(StorySnapshotService.RunMode.INCREMENTAL,
                        watermark.plusSeconds(600), 1);

        assertThat(resumed.succeededVersions()).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_snapshots", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_processing_runs", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM story_processing_runs", String.class))
                .isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject(
                "SELECT fencing_token FROM story_processing_runs", Long.class))
                .isGreaterThan(originalClaim.fencingToken());
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

    @Test
    void preservesIdentityAcrossNoOpExtensionClosingAndReopening() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        publish();
        UUID id = story("a");
        long membership = jdbc.queryForObject("SELECT id FROM story_memberships", Long.class);
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("a")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT id FROM story_memberships", Long.class)).isEqualTo(membership);
        assertThat(jdbc.queryForObject("SELECT optimistic_version FROM stories", Long.class)).isZero();

        insertReadyInput(VERSION_24, "b", vector(1, 0), effectiveAt, null);
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("b")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT identity_anchor_article_ref FROM stories", String.class))
                .isEqualTo(ref("a"));
        advance(Duration.ofHours(73));
        publish();
        assertThat(jdbc.queryForObject("SELECT state FROM stories", String.class)).isEqualTo("CLOSED");
        insertReadyInput(VERSION_24, "c", vector(1, 0), effectiveAt, null);
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("c")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT state FROM stories", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_memberships", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_clustering_versions WHERE status = 'ACTIVE'",
                Integer.class)).isZero();
    }

    @Test
    void mergesAndSplitsWithUnchangedNeighborFingerprintAndDeterministicSurvivor() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        publish();
        UUID oldest = story("a");
        advance(Duration.ofMinutes(1));
        insertReadyInput(VERSION_24, "b", vector(0, 1), effectiveAt, null);
        publish();
        UUID second = story("b");
        assertThat(second).isNotEqualTo(oldest);
        // B keeps its fingerprint; A's correction makes the two stories merge.
        replace("a", vector(0, 1), "merge");
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("b")).isEqualTo(oldest);
        assertThat(jdbc.queryForObject("SELECT state FROM stories WHERE id = ?", String.class, second))
                .isEqualTo("SUPERSEDED");
        replace("a", vector(1, 0), "split");
        advance(Duration.ofMinutes(1));
        publish();
        UUID split = story("b");
        assertThat(story("a")).isEqualTo(oldest);
        assertThat(split).isNotIn(oldest, second);
        int memberships = jdbc.queryForObject("SELECT COUNT(*) FROM story_memberships", Integer.class);
        assertThat(service().processBackfill(watermark, 1).reusedVersions()).isOne();
        assertThat(story("b")).isEqualTo(split);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_memberships", Integer.class)).isEqualTo(memberships);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_assignment_decisions
                WHERE article_ref = ? AND result = 'ASSIGNED'
                """, Integer.class, ref("b"))).isEqualTo(3);
        // A retry of an already committed older snapshot must not restore its partition.
        jdbc.update("UPDATE story_processing_runs SET status = 'FAILED' WHERE id = (SELECT MIN(id) FROM story_processing_runs)");
        assertThat(service().processBackfill(watermark, 1).reusedVersions()).isOne();
        assertThat(story("b")).isEqualTo(split);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_memberships", Integer.class)).isEqualTo(memberships);
    }

    @Test
    void missingAnchorUsesLargestOverlapAndThenEarliestComponent() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "b", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "c", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "d", vector(1, 0), effectiveAt, null);
        publish();
        UUID original = story("a");
        makeMissing("a");
        replace("b", vector(0, 1), "split");
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("c")).isEqualTo(original);
        assertThat(story("d")).isEqualTo(original);
        assertThat(story("b")).isNotEqualTo(original);
        replace("d", vector(-1, 0), "tie");
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("c")).isEqualTo(original);
        assertThat(story("d")).isNotEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT identity_anchor_article_ref FROM stories WHERE id = ?",
                String.class, original)).isEqualTo(ref("a"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM story_memberships WHERE article_ref = ? AND current_marker = 1
                """, Integer.class, ref("a"))).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT assignment_reason FROM story_assignment_decisions
                WHERE article_ref = ? ORDER BY id DESC LIMIT 1
                """, String.class, ref("a"))).isEqualTo("TITLE_MISSING");
    }

    @Test
    void simultaneousMergeAndSplitNominatesEachOldIdOnlyOnce() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        insertReadyInput(VERSION_24, "b", vector(1, 0), effectiveAt, null);
        publish();
        UUID oldest = story("a");
        advance(Duration.ofMinutes(1));
        insertReadyInput(VERSION_24, "c", vector(0, 1), effectiveAt, null);
        insertReadyInput(VERSION_24, "d", vector(0, 1), effectiveAt, null);
        publish();
        UUID other = story("c");
        replace("c", vector(1, 0), "merge");
        replace("b", vector(0, 1), "split");
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("a")).isEqualTo(oldest);
        assertThat(story("c")).isEqualTo(oldest);
        assertThat(story("b")).isEqualTo(story("d")).isNotIn(oldest, other);
        assertThat(jdbc.queryForObject("SELECT state FROM stories WHERE id = ?", String.class, other))
                .isEqualTo("SUPERSEDED");
    }

    @Test
    void unavailableSnapshotStaysFrozenWhenEmbeddingBecomesReady() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        jdbc.update("UPDATE story_article_inputs SET embedding_status = 'PENDING'");
        Work work = prepareWork();
        jdbc.update("UPDATE story_article_inputs SET embedding_status = 'READY'");
        StoryPublisher publisher = publisher();
        StoryPublisher.Plan plan = publisher.prepare(work.snapshot());
        assertThat(plan.partition().components()).isEmpty();
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            publisher.publish(plan, work.run(), clock.instant(), Duration.ofMinutes(30));
            new StorySnapshotRepository(jdbc).completeRun(work.run(), 1, 0, 1, 0, clock.instant());
        });
        assertThat(jdbc.queryForObject("SELECT assignment_reason FROM story_assignment_decisions", String.class))
                .isEqualTo("EMBEDDING_NOT_READY");
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(story("a")).isNotNull();
        assertThat(publisher.prepare(work.snapshot()).partition().components()).isEmpty();
    }

    @Test
    void staleUnpublishedSnapshotCannotReplaceNewerPublishedState() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        Work old = prepareWork();
        new StorySnapshotRepository(jdbc).failRun(old.run(), 1, clock.instant());
        advance(Duration.ofMinutes(1));
        insertReadyInput(VERSION_24, "b", vector(1, 0), effectiveAt, null);
        publish(); // BACKFILL; old work is REPROCESSING and is not retried here.
        UUID id = story("b");
        assertThat(service().reprocess(watermark, 1).reusedVersions()).isOne();
        assertThat(story("b")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT status FROM story_processing_runs WHERE id = ?",
                String.class, old.run().id())).isEqualTo("DISCARDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_publish_commits", Integer.class)).isOne();
    }

    @Test
    void expiredAndSupersededFencingTokensCannotPublishAndExpiryRollsBackAllWrites() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        Work work = prepareWork();
        StoryPublisher publisher = publisher();
        StoryPublisher.Plan plan = publisher.prepare(work.snapshot());
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        Instant expired = clock.instant().plus(Duration.ofMinutes(30));
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                publisher.publish(plan, work.run(), expired, Duration.ofMinutes(30))))
                .hasMessageContaining("lease expired");
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            publisher.publish(plan, work.run(), clock.instant(), Duration.ofMinutes(30));
            publisher.assertLease(work.snapshot().versionId(), work.run(), expired, Duration.ofMinutes(30));
        })).hasMessageContaining("lease expired");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stories", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_assignment_decisions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_memberships", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_publish_commits", Integer.class)).isZero();
        jdbc.update("UPDATE story_processing_runs SET fencing_token = fencing_token + 1 WHERE id = ?", work.run().id());
        assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                publisher.publish(plan, work.run(), clock.instant(), Duration.ofMinutes(30))))
                .hasMessageContaining("fencing token");
    }

    @Test
    void optimisticStoryConflictRejectsTheEntirePlan() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        publish();
        advance(Duration.ofMinutes(1));
        insertReadyInput(VERSION_24, "b", vector(1, 0), effectiveAt, null);
        Work work = prepareWork();
        StoryPublisher publisher = publisher();
        StoryPublisher.Plan plan = publisher.prepare(work.snapshot());
        jdbc.update("UPDATE stories SET optimistic_version = optimistic_version + 1");
        assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
                .executeWithoutResult(status -> publisher.publish(plan, work.run(), clock.instant(), Duration.ofMinutes(30))))
                .hasMessageContaining("basis changed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_memberships", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_publish_commits", Integer.class)).isOne();
    }

    @Test
    void publishesSuccessfulLegacyRunWithoutAnExistingCommit() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        Work work = prepareWork();
        new StorySnapshotRepository(jdbc).completeRun(work.run(), 1, 0, 1, 0, clock.instant());
        assertThat(service().reprocess(watermark, 1).succeededVersions()).isOne();
        assertThat(story("a")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_processing_runs", Integer.class)).isOne();
    }

    @Test
    void futureTimeCorrectionEndsMembershipWithFrozenReason() {
        insertReadyInput(VERSION_24, "a", vector(1, 0), effectiveAt, null);
        publish();
        UUID id = story("a");
        jdbc.update("UPDATE story_article_inputs SET current_marker = NULL, superseded_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), currentInput(VERSION_24, "a"));
        insertReadyInput(VERSION_24, "a", vector(1, 0), watermark.plus(Duration.ofDays(1)), "future");
        advance(Duration.ofMinutes(1));
        publish();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM story_memberships WHERE current_marker = 1",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM stories WHERE id = ?", String.class, id))
                .isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("""
                SELECT assignment_reason FROM story_assignment_decisions ORDER BY id DESC LIMIT 1
                """, String.class)).isEqualTo("AFTER_WATERMARK");
    }

    private void publish() {
        assertThat(service().processBackfill(watermark, 1).succeededVersions()).isOne();
    }

    private UUID story(String suffix) {
        return jdbc.queryForObject("SELECT story_id FROM story_memberships WHERE article_ref = ? AND current_marker = 1",
                UUID.class, ref(suffix));
    }

    private void advance(Duration duration) {
        clock = Clock.fixed(clock.instant().plus(duration), ZoneOffset.UTC);
        watermark = clock.instant();
    }

    private void replace(String suffix, byte[] vector, String variant) {
        jdbc.update("UPDATE story_article_inputs SET current_marker = NULL, superseded_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), currentInput(VERSION_24, suffix));
        insertReadyInput(VERSION_24, suffix, vector, effectiveAt, variant);
    }

    private void makeMissing(String suffix) {
        long old = currentInput(VERSION_24, suffix);
        jdbc.update("UPDATE story_article_inputs SET current_marker = NULL, superseded_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), old);
        jdbc.update("""
                INSERT INTO story_article_inputs (clustering_version_id, article_id, article_ref, effective_at,
                    effective_at_source, title_usability, article_input_fingerprint, embedding_status, current_marker, created_at)
                SELECT clustering_version_id, article_id, article_ref, effective_at, effective_at_source,
                    'TITLE_MISSING', ?, 'NOT_REQUIRED', 1, ? FROM story_article_inputs WHERE id = ?
                """, "0".repeat(64), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), old);
    }

    private StoryPublisher publisher() {
        return new StoryPublisher(jdbc, new StorySnapshotRepository(jdbc),
                new DataSourceTransactionManager(dataSource), new SimpleMeterRegistry());
    }

    private Work prepareWork() {
        StorySnapshotRepository repository = new StorySnapshotRepository(jdbc);
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource)).execute(status -> {
            var version = repository.findShadowVersions(1).getFirst();
            repository.lockVersion(version.id());
            var inputs = repository.findSnapshotInputs(version.id(), watermark);
            String hash = StorySnapshotCanonicalizer.snapshotInputHash(version.key(), watermark, inputs);
            var snapshot = repository.ensureSnapshot(version, watermark,
                    StorySnapshotCanonicalizer.snapshotKey(version.key(), watermark, hash), hash, inputs, clock.instant());
            String key = StorySnapshotCanonicalizer.runKey(version.key(), hash,
                    StorySnapshotService.RunMode.REPROCESSING, version.pairRuleVersion());
            var run = repository.claimRun(version, snapshot, StorySnapshotService.RunMode.REPROCESSING,
                    key, clock.instant(), Duration.ofMinutes(30)).orElseThrow();
            return new Work(snapshot, run);
        });
    }

    private record Work(StorySnapshotRepository.Snapshot snapshot, StorySnapshotRepository.RunClaim run) { }

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

    private void assertPublishedStories() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM stories", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_memberships WHERE current_marker = 1", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_assignment_decisions", Integer.class)).isGreaterThanOrEqualTo(5);
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
