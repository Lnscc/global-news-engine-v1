package db.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryDomainModelMigrationPostgresIT {

    private DataSource adminDataSource;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private String schemaName;
    private OffsetDateTime timestamp;
    private long existingArticleId;

    @BeforeEach
    void setUp() {
        adminDataSource = postgresDataSource(null);
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "Story migration test requires the local compose database");
        schemaName = "it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schemaName);
        dataSource = postgresDataSource(schemaName);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schemaName).defaultSchema(schemaName)
                .target(MigrationVersion.fromVersion("21")).load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        timestamp = OffsetDateTime.parse("2026-07-23T10:00:00Z");
        existingArticleId = insertArticle("existing");
    }

    @AfterEach
    void tearDown() {
        if (adminDataSource != null && schemaName != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void createsStorySchemaAndShadowVersionsWithoutChangingArticles() {
        migrateToLatest();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM articles WHERE id = ?", Integer.class, existingArticleId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM story_clustering_versions", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM story_clustering_versions WHERE status = 'SHADOW'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("""
                SELECT candidate_window_hours
                FROM story_clustering_versions
                ORDER BY candidate_window_hours
                """, Integer.class)).containsExactly(24, 48, 72);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM story_clustering_version_status_history", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('story_memberships')", String.class)).isEqualTo("story_memberships");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('story_publish_commits')", String.class)).isEqualTo("story_publish_commits");
    }

    @Test
    void deduplicatesEmbeddingArtifactsAndRejectsInvalidVectorPayloads() {
        migrateToLatest();

        long embeddingId = insertReadyEmbedding('a', 2);
        long versionId = versionId(24);
        long secondArticleId = insertArticle("second");
        insertReadyArticleInput(versionId, existingArticleId, "hash-existing", 'b', embeddingId, true);
        insertReadyArticleInput(versionId, secondArticleId, "hash-second", 'c', embeddingId, true);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM story_article_inputs WHERE embedding_artifact_id = ?
                """, Integer.class, embeddingId)).isEqualTo(2);

        assertThatThrownBy(() -> insertReadyEmbedding('a', 2))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO story_embedding_artifacts (
                    embedding_model_id, embedding_model_version, embedding_dimension,
                    title_normalization_version, title_input_hash, status, vector_bytes,
                    vector_hash, vector_norm, attempt_count, ready_at, created_at, updated_at
                ) VALUES (
                    'model', 'model@1', 2, 'title-v1', ?, 'READY', ?,
                    ?, 1.0, 1, ?, ?, ?
                )
                """, hash('d'), new byte[4], hash('e'), timestamp, timestamp, timestamp))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO story_clustering_versions (
                    version_key, title_normalization_version, generic_title_rule_version,
                    embedding_model_id, embedding_model_version, embedding_dimension,
                    candidate_time_rule_version, candidate_window_hours,
                    candidate_similarity_threshold, candidate_search_mode,
                    pair_decision_rule_version, component_rule_version,
                    feature_normalization_versions, status, created_at, updated_at
                ) VALUES (
                    'invalid-status', 'title-v1', 'generic-v1', 'model', 'model@1', 2,
                    'time-v1', 24, 0.7, 'exact', 'pair-v1', 'component-v1', 'none',
                    'ENABLED', ?, ?
                )
                """, timestamp, timestamp)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void freezesSnapshotMembershipAndRejectsCrossVersionRuns() {
        migrateToLatest();

        long version24 = versionId(24);
        long version48 = versionId(48);
        long embeddingId = insertReadyEmbedding('f', 2);
        long inputId = insertReadyArticleInput(
                version24, existingArticleId, "hash-existing", 'g', embeddingId, true);
        long snapshotId = insertSnapshot(version24, 'h', timestamp);
        insertSnapshotMember(snapshotId, version24, inputId, "hash-existing", 'g', embeddingId, 'g');

        assertThatThrownBy(() -> insertRun(version48, snapshotId, 'j', 1))
                .isInstanceOf(DataAccessException.class);

        long otherVersionInput = insertReadyArticleInput(
                version48, existingArticleId, "hash-existing", 'k', embeddingId, true);
        assertThatThrownBy(() -> insertSnapshotMember(
                snapshotId, version24, otherVersionInput, "hash-existing", 'k', embeddingId, 'g'))
                .isInstanceOf(DataAccessException.class);
        insertRun(version24, snapshotId, 'j', 1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM story_snapshot_members WHERE snapshot_id = ?", snapshotId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void protectsVersionContractsAndReadyEmbeddingsFromMutation() {
        migrateToLatest();

        long versionId = versionId(24);
        long embeddingId = insertReadyEmbedding('s', 2);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE story_clustering_versions
                SET candidate_similarity_threshold = 0.6
                WHERE id = ?
                """, versionId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE story_embedding_artifacts
                SET provider_request_id = 'changed'
                WHERE id = ?
                """, embeddingId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void enforcesCurrentMembershipHistoryLineageAndPublishIdempotency() {
        migrateToLatest();

        long versionId = versionId(24);
        long embeddingId = insertReadyEmbedding('l', 2);
        long inputId = insertReadyArticleInput(
                versionId, existingArticleId, "hash-existing", 'm', embeddingId, true);
        long snapshotId = insertSnapshot(versionId, 'n', timestamp);
        insertSnapshotMember(snapshotId, versionId, inputId, "hash-existing", 'm', embeddingId, 'm');
        long runId = insertRun(versionId, snapshotId, 'p', 1);
        UUID firstStoryId = insertStory(versionId, runId, "hash-existing");
        UUID secondStoryId = insertStory(versionId, runId, "hash-existing");
        long decisionId = insertAssignmentDecision(
                versionId, runId, snapshotId, inputId, "hash-existing", 'm', firstStoryId, 'q');
        insertMembership(
                firstStoryId, versionId, existingArticleId, "hash-existing", 'm', runId, decisionId);

        assertThatThrownBy(() -> insertMembership(
                secondStoryId, versionId, existingArticleId, "hash-existing", 'm', runId, decisionId))
                .isInstanceOf(DataAccessException.class);

        long closingRunId = insertRun(versionId, snapshotId, 's', 2);
        jdbcTemplate.update("""
                UPDATE story_memberships
                SET valid_to_run_id = ?, current_marker = NULL, ended_at = ?
                WHERE decision_id = ?
                """, closingRunId, timestamp.plusMinutes(2), decisionId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM story_memberships
                WHERE article_ref = 'hash-existing' AND current_marker IS NULL
                """, Integer.class)).isOne();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                UPDATE story_memberships SET assignment_reason = 'CHANGED' WHERE decision_id = ?
                """, decisionId)).isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("""
                INSERT INTO story_lineage (
                    clustering_version_id, predecessor_story_id, successor_story_id,
                    run_id, reason, created_at
                ) VALUES (?, ?, ?, ?, 'MERGE', ?)
                """, versionId, secondStoryId, firstStoryId, runId, timestamp);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT successor_story_id FROM story_lineage WHERE predecessor_story_id = ?
                """, UUID.class, secondStoryId)).isEqualTo(firstStoryId);

        insertPublish(versionId, snapshotId, runId, 'r');
        assertThatThrownBy(() -> insertPublish(versionId, snapshotId, runId, 'r'))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertSnapshot(versionId, 'n', timestamp.plusMinutes(1)))
                .isInstanceOf(DataAccessException.class);
    }

    private void migrateToLatest() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schemaName).defaultSchema(schemaName).load().migrate();
    }

    private long insertArticle(String suffix) {
        String articleRef = "hash-" + suffix;
        jdbcTemplate.update("""
                INSERT INTO articles (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (?, ?, 'example.org', ?, ?, ?)
                """, "https://example.org/" + suffix, articleRef, timestamp, timestamp, timestamp);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM articles WHERE url_hash = ?", Long.class, articleRef);
    }

    private long versionId(int windowHours) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM story_clustering_versions WHERE candidate_window_hours = ?
                """, Long.class, windowHours);
    }

    private long insertReadyEmbedding(char key, int dimension) {
        byte[] vector = ByteBuffer.allocate(dimension * Float.BYTES)
                .putFloat(1.0f)
                .putFloat(0.0f)
                .array();
        jdbcTemplate.update("""
                INSERT INTO story_embedding_artifacts (
                    embedding_model_id, embedding_model_version, embedding_dimension,
                    title_normalization_version, title_input_hash, status, vector_bytes,
                    vector_hash, vector_norm, provider_request_id, attempt_count,
                    ready_at, created_at, updated_at
                ) VALUES (
                    'model', 'model@1', ?, 'title-v1', ?, 'READY', ?,
                    ?, 1.0, 'request-1', 1, ?, ?, ?
                )
                """, dimension, hash(key), vector, hash((char) (key + 1)), timestamp, timestamp, timestamp);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM story_embedding_artifacts WHERE title_input_hash = ?
                """, Long.class, hash(key));
    }

    private long insertReadyArticleInput(
            long versionId,
            long articleId,
            String articleRef,
            char fingerprint,
            long embeddingId,
            boolean current
    ) {
        jdbcTemplate.update("""
                INSERT INTO story_article_inputs (
                    clustering_version_id, article_id, article_ref, effective_at,
                    effective_at_source, normalized_title, title_input_hash, title_usability,
                    article_input_fingerprint, embedding_status, embedding_artifact_id,
                    attempt_count, current_marker, created_at, superseded_at
                ) VALUES (
                    ?, ?, ?, ?, 'PUBLISHED_AT', 'A usable title', ?, 'USABLE',
                    ?, 'READY', ?, 1, ?, ?, ?
                )
                """, versionId, articleId, articleRef, timestamp, hash((char) (fingerprint + 1)),
                hash(fingerprint), embeddingId, current ? 1 : null, timestamp, current ? null : timestamp);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM story_article_inputs
                WHERE clustering_version_id = ? AND article_input_fingerprint = ?
                """, Long.class, versionId, hash(fingerprint));
    }

    private long insertSnapshot(long versionId, char hashKey, OffsetDateTime watermark) {
        jdbcTemplate.update("""
                INSERT INTO story_snapshots (
                    clustering_version_id, snapshot_key, snapshot_watermark,
                    snapshot_input_hash, created_at
                ) VALUES (?, ?, ?, ?, ?)
                """, versionId, hash(hashKey), watermark, hash((char) (hashKey + 1)), timestamp);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM story_snapshots
                WHERE clustering_version_id = ? AND snapshot_key = ?
                """, Long.class, versionId, hash(hashKey));
    }

    private void insertSnapshotMember(
            long snapshotId,
            long versionId,
            long inputId,
            String articleRef,
            char fingerprint,
            long embeddingId,
            char vectorHash
    ) {
        jdbcTemplate.update("""
                INSERT INTO story_snapshot_members (
                    snapshot_id, clustering_version_id, article_input_id, article_ref,
                    article_input_fingerprint, embedding_artifact_id, vector_hash
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, snapshotId, versionId, inputId, articleRef, hash(fingerprint), embeddingId, hash(vectorHash));
    }

    private long insertRun(long versionId, long snapshotId, char key, long fencingToken) {
        jdbcTemplate.update("""
                INSERT INTO story_processing_runs (
                    run_key, clustering_version_id, snapshot_id, run_mode, status,
                    fencing_token, started_at, completed_at
                ) VALUES (?, ?, ?, 'REPROCESSING', 'SUCCEEDED', ?, ?, ?)
                """, hash(key), versionId, snapshotId, fencingToken, timestamp, timestamp.plusMinutes(1));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM story_processing_runs WHERE run_key = ?", Long.class, hash(key));
    }

    private UUID insertStory(long versionId, long runId, String articleRef) {
        UUID storyId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO stories (
                    id, clustering_version_id, identity_anchor_article_ref,
                    representative_article_ref, state, effective_from, effective_to,
                    optimistic_version, created_by_run_id, last_changed_by_run_id,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, ?, ?, ?)
                """, storyId, versionId, articleRef, articleRef, timestamp, timestamp,
                runId, runId, timestamp, timestamp);
        return storyId;
    }

    private long insertAssignmentDecision(
            long versionId,
            long runId,
            long snapshotId,
            long inputId,
            String articleRef,
            char fingerprint,
            UUID storyId,
            char decisionHash
    ) {
        jdbcTemplate.update("""
                INSERT INTO story_assignment_decisions (
                    decision_hash, clustering_version_id, run_id, snapshot_id,
                    article_input_id, article_ref, article_input_fingerprint,
                    resulting_story_id, result, assignment_reason,
                    component_rule_version, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ASSIGNED', 'INITIAL_COMPONENT',
                          'component-v1', ?)
                """, hash(decisionHash), versionId, runId, snapshotId, inputId,
                articleRef, hash(fingerprint), storyId, timestamp);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM story_assignment_decisions WHERE decision_hash = ?
                """, Long.class, hash(decisionHash));
    }

    private void insertMembership(
            UUID storyId,
            long versionId,
            long articleId,
            String articleRef,
            char fingerprint,
            long runId,
            long decisionId
    ) {
        jdbcTemplate.update("""
                INSERT INTO story_memberships (
                    story_id, clustering_version_id, article_id, article_ref,
                    article_input_fingerprint, valid_from_run_id, decision_id,
                    assignment_reason, current_marker, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'INITIAL_COMPONENT', 1, ?)
                """, storyId, versionId, articleId, articleRef, hash(fingerprint),
                runId, decisionId, timestamp);
    }

    private void insertPublish(long versionId, long snapshotId, long runId, char key) {
        jdbcTemplate.update("""
                INSERT INTO story_publish_commits (
                    publish_key, clustering_version_id, snapshot_id, run_id,
                    fencing_token, published_at
                ) VALUES (?, ?, ?, ?, 1, ?)
                """, hash(key), versionId, snapshotId, runId, timestamp);
    }

    private String hash(char value) {
        return String.valueOf(value).repeat(64);
    }

    private DataSource postgresDataSource(String schema) {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        String url = System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne");
        if (schema != null) url += (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        postgres.setUrl(url);
        postgres.setUser(System.getProperty("it.postgres.username", "gne"));
        postgres.setPassword(System.getProperty("it.postgres.password", "gne"));
        return postgres;
    }

    private boolean canConnect(DataSource candidate) {
        try (Connection ignored = candidate.getConnection()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }
}
