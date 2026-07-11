package com.example.globalnewsenginev1.articles.health;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleExtractionHealthServiceTests {

    private JdbcTemplate jdbcTemplate;
    private ArticleExtractionHealthService healthService;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:article-health-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V2__create_gdelt_staging_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        healthService = new ArticleExtractionHealthService(jdbcTemplate);
    }

    @Test
    void reportsPendingProcessedAndErrorRowsPerSignalType() {
        Instant oldTimestamp = Instant.parse("2026-07-05T11:45:00Z");
        Instant newTimestamp = Instant.parse("2026-07-05T12:00:00Z");
        long processedEvent = insertStageEvent(oldTimestamp, 1);
        insertStageEvent(newTimestamp, 2);
        long failedMention = insertStageMention(newTimestamp, 1);
        insertStageGkg(newTimestamp, 1);
        long articleId = insertArticle(oldTimestamp);
        insertSignal(articleId, "EVENTS", processedEvent, oldTimestamp);
        insertError("MENTIONS", failedMention, newTimestamp, "INVALID_URL");

        ArticleExtractionHealth health = healthService.health();

        assertThat(health.articlesCreatedTotal()).isEqualTo(1);
        assertThat(health.signalTypes()).containsExactly(
                new SignalTypeExtractionHealth("EVENTS", 1, 1, oldTimestamp, java.util.List.of()),
                new SignalTypeExtractionHealth("MENTIONS", 0, 0, newTimestamp,
                        java.util.List.of(new ExtractionErrorCount("INVALID_URL", 1))),
                new SignalTypeExtractionHealth("GKG", 1, 0, null, java.util.List.of()));
    }

    private long insertStageEvent(Instant timestamp, long rowNumber) {
        long rawId = insertRaw("EVENTS", "gdelt_raw_events", timestamp, rowNumber);
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_events
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at, global_event_id)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ?, ?
                FROM gdelt_raw_events WHERE id = ?
                """, utc(timestamp), rowNumber, rawId);
        return jdbcTemplate.queryForObject("SELECT id FROM gdelt_stage_events WHERE raw_id = ?", Long.class, rawId);
    }

    private long insertStageMention(Instant timestamp, long rowNumber) {
        long rawId = insertRaw("MENTIONS", "gdelt_raw_mentions", timestamp, rowNumber);
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_mentions
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at, global_event_id)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ?, ?
                FROM gdelt_raw_mentions WHERE id = ?
                """, utc(timestamp), rowNumber, rawId);
        return jdbcTemplate.queryForObject("SELECT id FROM gdelt_stage_mentions WHERE raw_id = ?", Long.class, rawId);
    }

    private void insertStageGkg(Instant timestamp, long rowNumber) {
        long rawId = insertRaw("GKG", "gdelt_raw_gkg", timestamp, rowNumber);
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_gkg
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at, gkg_record_id)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ?, 'record-' || id
                FROM gdelt_raw_gkg WHERE id = ?
                """, utc(timestamp), rawId);
    }

    private long insertRaw(String type, String table, Instant timestamp, long rowNumber) {
        String sourceFile = type.toLowerCase() + "-" + rowNumber + ".zip";
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, started_at)
                VALUES (?, ?, ?, ?, 'COMPLETED', ?)
                """, type, sourceFile, "http://localhost/" + sourceFile, utc(timestamp), utc(timestamp));
        Long importId = jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_import_files WHERE dataset_type = ? AND source_file = ?",
                Long.class, type, sourceFile);
        jdbcTemplate.update("""
                INSERT INTO %s
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (?, ?, ?, ?, 'raw', ?)
                """.formatted(table), importId, sourceFile, utc(timestamp), rowNumber, utc(timestamp));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM %s WHERE source_file = ? AND row_number = ?".formatted(table),
                Long.class, sourceFile, rowNumber);
    }

    private long insertArticle(Instant timestamp) {
        jdbcTemplate.update("""
                INSERT INTO articles
                    (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES ('https://example.org/a', 'hash', 'example.org', ?, ?, ?)
                """, utc(timestamp), utc(timestamp), utc(timestamp));
        return jdbcTemplate.queryForObject("SELECT id FROM articles", Long.class);
    }

    private void insertSignal(long articleId, String type, long sourceId, Instant timestamp) {
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, articleId, type, sourceId, utc(timestamp), utc(timestamp));
    }

    private void insertError(String type, long sourceId, Instant timestamp, String code) {
        jdbcTemplate.update("""
                INSERT INTO article_extraction_errors
                    (signal_type, source_id, source_timestamp, error_code, error_message, created_at)
                VALUES (?, ?, ?, ?, 'broken URL', ?)
                """, type, sourceId, utc(timestamp), code, utc(timestamp));
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
