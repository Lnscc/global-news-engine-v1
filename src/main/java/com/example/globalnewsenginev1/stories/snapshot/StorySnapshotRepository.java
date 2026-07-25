package com.example.globalnewsenginev1.stories.snapshot;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class StorySnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    StorySnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<ClusteringVersion> findShadowVersions(int limit) {
        return jdbcTemplate.query("""
                SELECT id, version_key, embedding_dimension, candidate_window_hours,
                       candidate_similarity_threshold, candidate_search_mode,
                       pair_decision_rule_version
                FROM story_clustering_versions
                WHERE status = 'SHADOW'
                ORDER BY id
                LIMIT ?
                """, (resultSet, rowNum) -> new ClusteringVersion(
                resultSet.getLong("id"),
                resultSet.getString("version_key"),
                resultSet.getInt("embedding_dimension"),
                resultSet.getInt("candidate_window_hours"),
                resultSet.getBigDecimal("candidate_similarity_threshold").setScale(6),
                resultSet.getString("candidate_search_mode"),
                resultSet.getString("pair_decision_rule_version")), limit);
    }

    void lockVersion(long versionId) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", resultSet -> null, versionId);
        jdbcTemplate.queryForObject("""
                SELECT id FROM story_clustering_versions WHERE id = ? FOR SHARE
                """, Long.class, versionId);
    }

    Optional<RetrySnapshot> findRetryableSnapshot(
            long versionId,
            StorySnapshotService.RunMode mode,
            Instant now,
            Duration claimTimeout
    ) {
        return jdbcTemplate.query("""
                SELECT snapshot.id, snapshot.clustering_version_id,
                       snapshot.snapshot_key, snapshot.snapshot_watermark,
                       snapshot.snapshot_input_hash,
                       (SELECT COUNT(*) FROM story_snapshot_members member
                        WHERE member.snapshot_id = snapshot.id) AS member_count
                FROM story_processing_runs run
                JOIN story_snapshots snapshot ON snapshot.id = run.snapshot_id
                WHERE run.clustering_version_id = ?
                  AND run.run_mode = ?
                  AND (
                    run.status = 'FAILED'
                    OR (run.status = 'RUNNING' AND run.started_at <= ?)
                  )
                ORDER BY run.started_at, run.id
                LIMIT 1
                """, (resultSet, rowNum) -> new RetrySnapshot(
                new Snapshot(
                        resultSet.getLong("id"),
                        resultSet.getLong("clustering_version_id"),
                        resultSet.getString("snapshot_key"),
                        resultSet.getTimestamp("snapshot_watermark").toInstant(),
                        resultSet.getString("snapshot_input_hash"),
                        false),
                resultSet.getInt("member_count")),
                versionId, mode.name(), Timestamp.from(now.minus(claimTimeout)))
                .stream().findFirst();
    }

    List<SnapshotInput> findReadyInputs(long versionId, Instant watermark) {
        return jdbcTemplate.query("""
                SELECT input.id AS article_input_id, input.article_ref,
                       input.article_input_fingerprint, input.effective_at,
                       input.effective_at_source, input.title_input_hash,
                       artifact.id AS embedding_artifact_id, artifact.vector_hash,
                       artifact.vector_bytes, artifact.embedding_dimension
                FROM story_article_inputs input
                JOIN story_embedding_artifacts artifact
                  ON artifact.id = input.embedding_artifact_id
                WHERE input.clustering_version_id = ?
                  AND input.current_marker = 1
                  AND input.title_usability = 'USABLE'
                  AND input.embedding_status = 'READY'
                  AND artifact.status = 'READY'
                  AND artifact.vector_bytes IS NOT NULL
                  AND artifact.vector_hash IS NOT NULL
                  AND input.effective_at <= ?
                ORDER BY input.effective_at, input.article_ref
                """, (resultSet, rowNum) -> new SnapshotInput(
                resultSet.getLong("article_input_id"),
                resultSet.getString("article_ref"),
                resultSet.getString("article_input_fingerprint"),
                resultSet.getTimestamp("effective_at").toInstant(),
                resultSet.getString("effective_at_source"),
                resultSet.getString("title_input_hash"),
                resultSet.getLong("embedding_artifact_id"),
                resultSet.getString("vector_hash"),
                resultSet.getBytes("vector_bytes"),
                resultSet.getInt("embedding_dimension")),
                versionId, Timestamp.from(watermark));
    }

    Snapshot ensureSnapshot(
            ClusteringVersion version,
            Instant watermark,
            String snapshotKey,
            String inputHash,
            List<SnapshotInput> inputs,
            Instant now
    ) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO story_snapshots (
                    clustering_version_id, snapshot_key, snapshot_watermark,
                    snapshot_input_hash, created_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, version.id(), snapshotKey, Timestamp.from(watermark), inputHash,
                Timestamp.from(now));
        Snapshot snapshot = jdbcTemplate.queryForObject("""
                SELECT id, clustering_version_id, snapshot_key, snapshot_watermark,
                       snapshot_input_hash
                FROM story_snapshots
                WHERE clustering_version_id = ? AND snapshot_key = ?
                """, (resultSet, rowNum) -> new Snapshot(
                resultSet.getLong("id"),
                resultSet.getLong("clustering_version_id"),
                resultSet.getString("snapshot_key"),
                resultSet.getTimestamp("snapshot_watermark").toInstant(),
                resultSet.getString("snapshot_input_hash"),
                inserted == 1), version.id(), snapshotKey);
        if (!snapshot.inputHash().equals(inputHash)
                || !snapshot.watermark().equals(watermark)) {
            throw new IllegalStateException("Snapshot key collision detected");
        }
        int memberCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM story_snapshot_members WHERE snapshot_id = ?
                """, Integer.class, snapshot.id());
        if (memberCount != inputs.size()) {
            for (SnapshotInput input : inputs) {
                jdbcTemplate.update("""
                        INSERT INTO story_snapshot_members (
                            snapshot_id, clustering_version_id, article_input_id,
                            article_ref, article_input_fingerprint,
                            embedding_artifact_id, vector_hash
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """, snapshot.id(), version.id(), input.articleInputId(),
                        input.articleRef(), input.articleInputFingerprint(),
                        input.embeddingArtifactId(), input.vectorHash());
            }
            memberCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM story_snapshot_members WHERE snapshot_id = ?
                    """, Integer.class, snapshot.id());
        }
        if (memberCount != inputs.size()) {
            throw new IllegalStateException(
                    "Snapshot member count differs from its canonical input");
        }
        return snapshot;
    }

    Optional<RunClaim> claimRun(
            ClusteringVersion version,
            Snapshot snapshot,
            StorySnapshotService.RunMode mode,
            String runKey,
            Instant now,
            Duration claimTimeout
    ) {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", resultSet -> null, version.id());
        long fencingToken = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(fencing_token), -1) + 1
                FROM story_processing_runs
                WHERE clustering_version_id = ?
                """, Long.class, version.id());
        jdbcTemplate.update("""
                INSERT INTO story_processing_runs (
                    run_key, clustering_version_id, snapshot_id, run_mode,
                    status, fencing_token, started_at
                ) VALUES (?, ?, ?, ?, 'RUNNING', ?, ?)
                ON CONFLICT (run_key) DO NOTHING
                """, runKey, version.id(), snapshot.id(), mode.name(),
                fencingToken, Timestamp.from(now));
        List<RunState> states = jdbcTemplate.query("""
                SELECT id, status, started_at, fencing_token
                FROM story_processing_runs
                WHERE run_key = ?
                FOR UPDATE
                """, (resultSet, rowNum) -> new RunState(
                resultSet.getLong("id"),
                resultSet.getString("status"),
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getLong("fencing_token")), runKey);
        if (states.isEmpty()) {
            return Optional.empty();
        }
        RunState state = states.getFirst();
        boolean newlyInserted = state.fencingToken() == fencingToken
                && "RUNNING".equals(state.status())
                && state.startedAt().equals(now);
        boolean stale = "RUNNING".equals(state.status())
                && !state.startedAt().plus(claimTimeout).isAfter(now);
        boolean retryable = "FAILED".equals(state.status()) || stale;
        if (!newlyInserted && !retryable) {
            return Optional.empty();
        }
        if (retryable) {
            fencingToken = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(MAX(fencing_token), -1) + 1
                    FROM story_processing_runs
                    WHERE clustering_version_id = ?
                    """, Long.class, version.id());
            jdbcTemplate.update("""
                    UPDATE story_processing_runs
                    SET status = 'RUNNING', fencing_token = ?, started_at = ?,
                        completed_at = NULL, read_article_count = 0,
                        changed_article_count = 0, skipped_article_count = 0,
                        failed_article_count = 0, candidate_count = 0
                    WHERE id = ?
                    """, fencingToken, Timestamp.from(now), state.id());
        }
        return Optional.of(new RunClaim(state.id(), fencingToken));
    }

    List<SnapshotInput> loadSnapshotInputs(long snapshotId) {
        return jdbcTemplate.query("""
                SELECT input.id AS article_input_id, input.article_ref,
                       member.article_input_fingerprint, input.effective_at,
                       input.effective_at_source, input.title_input_hash,
                       artifact.id AS embedding_artifact_id, member.vector_hash,
                       artifact.vector_bytes, artifact.embedding_dimension
                FROM story_snapshot_members member
                JOIN story_article_inputs input ON input.id = member.article_input_id
                JOIN story_embedding_artifacts artifact
                  ON artifact.id = member.embedding_artifact_id
                 AND artifact.vector_hash = member.vector_hash
                WHERE member.snapshot_id = ?
                ORDER BY input.effective_at, input.article_ref
                """, (resultSet, rowNum) -> new SnapshotInput(
                resultSet.getLong("article_input_id"),
                resultSet.getString("article_ref"),
                resultSet.getString("article_input_fingerprint"),
                resultSet.getTimestamp("effective_at").toInstant(),
                resultSet.getString("effective_at_source"),
                resultSet.getString("title_input_hash"),
                resultSet.getLong("embedding_artifact_id"),
                resultSet.getString("vector_hash"),
                resultSet.getBytes("vector_bytes"),
                resultSet.getInt("embedding_dimension")), snapshotId);
    }

    void insertDecision(
            ClusteringVersion version,
            Snapshot snapshot,
            RunClaim run,
            StorySnapshotService.PairDecision decision,
            String decisionHash,
            Instant now
    ) {
        SnapshotInput left = decision.left();
        SnapshotInput right = decision.right();
        jdbcTemplate.update("""
                INSERT INTO story_pair_decisions (
                    decision_hash, clustering_version_id, run_id, snapshot_id,
                    left_article_input_id, right_article_input_id,
                    left_article_ref, right_article_ref,
                    left_article_input_fingerprint, right_article_input_fingerprint,
                    left_effective_at, right_effective_at,
                    left_effective_at_source, right_effective_at_source,
                    left_title_input_hash, right_title_input_hash,
                    left_embedding_artifact_id, right_embedding_artifact_id,
                    left_vector_hash, right_vector_hash, cosine_similarity,
                    time_distance_seconds, candidate_rank, candidate_window_hours,
                    similarity_threshold, pair_rule_version, result,
                    triggered_rule, top_one_below_threshold, non_decisive_evidence,
                    created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                ON CONFLICT DO NOTHING
                """, decisionHash, version.id(), run.id(), snapshot.id(),
                left.articleInputId(), right.articleInputId(),
                left.articleRef(), right.articleRef(),
                left.articleInputFingerprint(), right.articleInputFingerprint(),
                Timestamp.from(left.effectiveAt()), Timestamp.from(right.effectiveAt()),
                left.effectiveAtSource(), right.effectiveAtSource(),
                left.titleInputHash(), right.titleInputHash(),
                left.embeddingArtifactId(), right.embeddingArtifactId(),
                left.vectorHash(), right.vectorHash(), decision.similarity(),
                decision.timeDistanceSeconds(), decision.rank(), version.windowHours(),
                version.threshold(), version.pairRuleVersion(), decision.result(),
                decision.triggeredRule(), decision.topOneBelowThreshold(),
                "{\"non_decisive\":true,\"search_mode\":\""
                        + version.searchMode() + "\"}",
                Timestamp.from(now));
    }

    void completeRun(
            RunClaim claim,
            int readArticles,
            int changedArticles,
            int skippedArticles,
            long candidateCount,
            Instant now
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE story_processing_runs
                SET status = 'SUCCEEDED', completed_at = ?, read_article_count = ?,
                    changed_article_count = ?, skipped_article_count = ?,
                    failed_article_count = 0, candidate_count = ?
                WHERE id = ? AND status = 'RUNNING' AND fencing_token = ?
                """, Timestamp.from(now), readArticles, changedArticles, skippedArticles,
                candidateCount, claim.id(), claim.fencingToken());
        if (updated != 1) {
            throw new IllegalStateException("Story run lost its fencing token");
        }
    }

    void failRun(RunClaim claim, int failedArticles, Instant now) {
        jdbcTemplate.update("""
                UPDATE story_processing_runs
                SET status = 'FAILED', completed_at = ?, failed_article_count = ?
                WHERE id = ? AND status = 'RUNNING' AND fencing_token = ?
                """, Timestamp.from(now), failedArticles, claim.id(), claim.fencingToken());
    }

    record ClusteringVersion(
            long id,
            String key,
            int dimension,
            int windowHours,
            BigDecimal threshold,
            String searchMode,
            String pairRuleVersion
    ) {
    }

    record SnapshotInput(
            long articleInputId,
            String articleRef,
            String articleInputFingerprint,
            Instant effectiveAt,
            String effectiveAtSource,
            String titleInputHash,
            long embeddingArtifactId,
            String vectorHash,
            byte[] vectorBytes,
            int embeddingDimension
    ) {
    }

    record Snapshot(
            long id,
            long versionId,
            String key,
            Instant watermark,
            String inputHash,
            boolean created
    ) {
    }

    record RunClaim(long id, long fencingToken) {
    }

    record RetrySnapshot(Snapshot snapshot, int memberCount) {
    }

    private record RunState(long id, String status, Instant startedAt, long fencingToken) {
    }
}
