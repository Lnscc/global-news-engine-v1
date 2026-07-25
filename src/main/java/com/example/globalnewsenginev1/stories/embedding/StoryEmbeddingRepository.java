package com.example.globalnewsenginev1.stories.embedding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
class StoryEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    StoryEmbeddingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<ArticleCandidate> findCandidates(int limit, boolean repairRun) {
        String retryPredicate = repairRun
                ? "(current.embedding_status = 'PENDING' OR "
                    + "(current.embedding_status = 'RETRYABLE_FAILURE' AND current.attempt_count < 5 "
                    + "AND (current.next_retry_at IS NULL OR current.next_retry_at <= CURRENT_TIMESTAMP)))"
                : "(current.embedding_status = 'PENDING' OR "
                    + "(current.embedding_status = 'RETRYABLE_FAILURE' AND current.attempt_count < 5 "
                    + "AND current.next_retry_at <= CURRENT_TIMESTAMP))";
        return jdbcTemplate.query("""
                SELECT version.id AS version_id, version.version_key,
                       version.title_normalization_version, version.generic_title_rule_version,
                       version.embedding_model_id, version.embedding_model_version,
                       version.embedding_dimension,
                       article.id AS article_id, article.url_hash AS article_ref,
                       article.first_seen_at,
                       (SELECT g.page_title FROM gdelt_gkg g
                        WHERE g.article_id = article.id
                          AND g.page_title IS NOT NULL AND TRIM(g.page_title) <> ''
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS title,
                       (SELECT g.page_precise_pub_timestamp FROM gdelt_gkg g
                        WHERE g.article_id = article.id
                          AND g.page_precise_pub_timestamp IS NOT NULL
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS published_at
                FROM story_clustering_versions version
                CROSS JOIN articles article
                LEFT JOIN story_article_inputs current
                  ON current.clustering_version_id = version.id
                 AND current.article_ref = article.url_hash
                 AND current.current_marker = 1
                WHERE version.status = 'SHADOW'
                  AND (
                    current.id IS NULL
                    OR article.updated_at > current.created_at
                    OR COALESCE(
                        (SELECT MAX(g.created_at) FROM gdelt_gkg g WHERE g.article_id = article.id),
                        article.created_at
                    ) > current.created_at
                    OR %s
                  )
                ORDER BY article.id, version.id
                LIMIT ?
                """.formatted(retryPredicate), (resultSet, rowNum) -> new ArticleCandidate(
                resultSet.getLong("version_id"),
                resultSet.getString("version_key"),
                resultSet.getString("title_normalization_version"),
                resultSet.getString("generic_title_rule_version"),
                resultSet.getString("embedding_model_id"),
                resultSet.getString("embedding_model_version"),
                resultSet.getInt("embedding_dimension"),
                resultSet.getLong("article_id"),
                resultSet.getString("article_ref"),
                resultSet.getTimestamp("first_seen_at").toInstant(),
                resultSet.getString("title"),
                nullableInstant(resultSet.getTimestamp("published_at"))), limit);
    }

    void lockArticle(long articleId) {
        jdbcTemplate.queryForObject("SELECT id FROM articles WHERE id = ? FOR UPDATE", Long.class, articleId);
    }

    long ensureArtifact(ArticleCandidate candidate, String titleInputHash, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO story_embedding_artifacts (
                    embedding_model_id, embedding_model_version, embedding_dimension,
                    title_normalization_version, title_input_hash, status, attempt_count,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (
                    embedding_model_id, embedding_model_version, embedding_dimension,
                    title_normalization_version, title_input_hash
                ) DO NOTHING
                """, candidate.embeddingModelId(), candidate.embeddingModelVersion(),
                candidate.embeddingDimension(), candidate.titleNormalizationVersion(), titleInputHash,
                Timestamp.from(now), Timestamp.from(now));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM story_embedding_artifacts
                WHERE embedding_model_id = ? AND embedding_model_version = ?
                  AND embedding_dimension = ? AND title_normalization_version = ?
                  AND title_input_hash = ?
                """, Long.class, candidate.embeddingModelId(), candidate.embeddingModelVersion(),
                candidate.embeddingDimension(), candidate.titleNormalizationVersion(), titleInputHash);
    }

    Optional<Artifact> lockArtifact(long artifactId) {
        return jdbcTemplate.query("""
                SELECT id, status, vector_hash, attempt_count, next_retry_at
                FROM story_embedding_artifacts
                WHERE id = ?
                FOR UPDATE SKIP LOCKED
                """, (resultSet, rowNum) -> new Artifact(
                resultSet.getLong("id"),
                resultSet.getString("status"),
                resultSet.getString("vector_hash"),
                resultSet.getInt("attempt_count"),
                nullableInstant(resultSet.getTimestamp("next_retry_at"))), artifactId).stream().findFirst();
    }

    void completeReady(
            long artifactId,
            int attemptNumber,
            EmbeddingVectorCodec.EncodedVector vector,
            String requestId,
            int observedDimension,
            Instant startedAt,
            Instant completedAt
    ) {
        insertAttempt(artifactId, attemptNumber, "READY", requestId, null, null,
                observedDimension, vector.hash(), startedAt, completedAt, null);
        jdbcTemplate.update("""
                UPDATE story_embedding_artifacts
                SET status = 'READY', vector_bytes = ?, vector_hash = ?, vector_norm = ?,
                    provider_request_id = ?, attempt_count = ?, next_retry_at = NULL,
                    ready_at = ?, updated_at = ?
                WHERE id = ? AND status <> 'READY'
                """, vector.bytes(), vector.hash(), vector.originalNorm(), requestId, attemptNumber,
                Timestamp.from(completedAt), Timestamp.from(completedAt), artifactId);
    }

    void completeFailure(
            long artifactId,
            int attemptNumber,
            String status,
            String requestId,
            String errorCode,
            String errorMessage,
            Integer observedDimension,
            Instant startedAt,
            Instant completedAt,
            Instant nextRetryAt
    ) {
        insertAttempt(artifactId, attemptNumber, status, requestId, errorCode, errorMessage,
                observedDimension, null, startedAt, completedAt, nextRetryAt);
        jdbcTemplate.update("""
                UPDATE story_embedding_artifacts
                SET status = ?, provider_request_id = ?, attempt_count = ?,
                    next_retry_at = ?, updated_at = ?
                WHERE id = ? AND status <> 'READY'
                """, status, requestId, attemptNumber, timestamp(nextRetryAt),
                Timestamp.from(completedAt), artifactId);
    }

    void recordModelDrift(
            long artifactId,
            int attemptNumber,
            String requestId,
            int observedDimension,
            String observedHash,
            Instant startedAt,
            Instant completedAt
    ) {
        insertAttempt(artifactId, attemptNumber, "MODEL_DRIFT", requestId,
                "MODEL_DRIFT", "Provider returned a different vector for an immutable artifact key",
                observedDimension, observedHash, startedAt, completedAt, null);
    }

    void synchronizeInput(
            ArticleCandidate candidate,
            TitleInput title,
            Artifact artifact,
            String fingerprint,
            Instant effectiveAt,
            String effectiveAtSource,
            Instant now
    ) {
        List<CurrentInput> current = jdbcTemplate.query("""
                SELECT id, article_input_fingerprint
                FROM story_article_inputs
                WHERE clustering_version_id = ? AND article_ref = ? AND current_marker = 1
                FOR UPDATE
                """, (resultSet, rowNum) -> new CurrentInput(
                resultSet.getLong("id"), resultSet.getString("article_input_fingerprint")),
                candidate.versionId(), candidate.articleRef());
        String embeddingStatus = title.usability() == TitleInput.TitleUsability.USABLE
                ? artifact.status() : "NOT_REQUIRED";
        Long artifactId = title.usability() == TitleInput.TitleUsability.USABLE ? artifact.id() : null;
        int attempts = artifact == null ? 0 : artifact.attemptCount();
        Instant nextRetry = artifact == null ? null : artifact.nextRetryAt();

        if (!current.isEmpty() && current.getFirst().fingerprint().equals(fingerprint)) {
            jdbcTemplate.update("""
                    UPDATE story_article_inputs
                    SET embedding_status = ?, embedding_artifact_id = ?,
                        attempt_count = ?, next_retry_at = ?
                    WHERE id = ?
                    """, embeddingStatus, artifactId, attempts, timestamp(nextRetry), current.getFirst().id());
            return;
        }
        if (!current.isEmpty()) {
            jdbcTemplate.update("""
                    UPDATE story_article_inputs
                    SET current_marker = NULL, superseded_at = ?
                    WHERE id = ?
                    """, Timestamp.from(now), current.getFirst().id());
        }
        jdbcTemplate.update("""
                INSERT INTO story_article_inputs (
                    clustering_version_id, article_id, article_ref, effective_at,
                    effective_at_source, normalized_title, title_input_hash, title_usability,
                    article_input_fingerprint, embedding_status, embedding_artifact_id,
                    attempt_count, next_retry_at, current_marker, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                ON CONFLICT (clustering_version_id, article_ref, article_input_fingerprint)
                DO UPDATE SET current_marker = 1, superseded_at = NULL,
                    embedding_status = EXCLUDED.embedding_status,
                    embedding_artifact_id = EXCLUDED.embedding_artifact_id,
                    attempt_count = EXCLUDED.attempt_count,
                    next_retry_at = EXCLUDED.next_retry_at
                """, candidate.versionId(), candidate.articleId(), candidate.articleRef(),
                Timestamp.from(effectiveAt), effectiveAtSource, title.normalizedTitle(),
                title.titleInputHash(), title.usability().name(), fingerprint, embeddingStatus,
                artifactId, attempts, timestamp(nextRetry), Timestamp.from(now));
    }

    StoryEmbeddingHealth.Snapshot healthSnapshot() {
        int pending = count("SELECT COUNT(*) FROM story_embedding_artifacts WHERE status = 'PENDING'");
        int retryable = count("""
                SELECT COUNT(*) FROM story_embedding_artifacts
                WHERE status = 'RETRYABLE_FAILURE' AND attempt_count < 5
                """);
        int exhausted = count("""
                SELECT COUNT(*) FROM story_embedding_artifacts
                WHERE status = 'RETRYABLE_FAILURE' AND attempt_count >= 5
                """);
        int terminal = count("""
                SELECT COUNT(*) FROM story_embedding_artifacts WHERE status = 'TERMINAL_FAILURE'
                """);
        return new StoryEmbeddingHealth.Snapshot(pending, retryable, exhausted, terminal);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private void insertAttempt(
            long artifactId,
            int attemptNumber,
            String status,
            String requestId,
            String errorCode,
            String errorMessage,
            Integer observedDimension,
            String observedHash,
            Instant startedAt,
            Instant completedAt,
            Instant nextRetryAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO story_embedding_attempts (
                    embedding_artifact_id, attempt_number, status, provider_request_id,
                    error_code, error_message, observed_dimension, observed_vector_hash,
                    started_at, completed_at, next_retry_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, artifactId, attemptNumber, status, requestId, errorCode,
                truncate(errorMessage, 4000), observedDimension, observedHash,
                Timestamp.from(startedAt), Timestamp.from(completedAt), timestamp(nextRetryAt));
    }

    private String truncate(String value, int maximum) {
        return value == null || value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    record ArticleCandidate(
            long versionId,
            String versionKey,
            String titleNormalizationVersion,
            String genericTitleRuleVersion,
            String embeddingModelId,
            String embeddingModelVersion,
            int embeddingDimension,
            long articleId,
            String articleRef,
            Instant firstSeenAt,
            String title,
            Instant publishedAt
    ) {
    }

    record Artifact(long id, String status, String vectorHash, int attemptCount, Instant nextRetryAt) {
    }

    private record CurrentInput(long id, String fingerprint) {
    }
}
