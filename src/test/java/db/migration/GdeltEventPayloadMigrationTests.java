package db.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltEventPayloadMigrationTests {

    @Test
    void preservesEventIdentityAndRemapsArticleReferences() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:event-payload-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V2__create_gdelt_staging_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        var ingestedAt = Instant.parse("2026-07-05T12:00:00Z").atOffset(ZoneOffset.UTC);
        var parsedAt = Instant.parse("2026-07-05T12:01:00Z").atOffset(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (id, dataset_type, source_file, source_url, source_timestamp, status, started_at)
                VALUES (7, 'EVENTS', 'events.zip', 'https://example.org/events.zip', ?, 'COMPLETED', ?)
                """, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO gdelt_raw_events
                    (id, import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (42, 7, 'events.zip', ?, 3, 'unchanged raw row', ?)
                """, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_events
                    (id, raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     global_event_id, event_code, avg_tone, source_url)
                VALUES (5, 42, 7, 'events.zip', ?, 3, ?, 123, '042', -1.25,
                        'https://example.org/article')
                """, ingestedAt, parsedAt);
        jdbcTemplate.update("""
                INSERT INTO articles
                    (id, canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (1, 'https://example.org/article', 'hash', 'example.org', ?, ?, ?)
                """, ingestedAt, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, created_at)
                VALUES (1, 'EVENTS', 5, ?, ?)
                """, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO article_extraction_errors
                    (signal_type, source_id, source_timestamp, error_code, error_message, created_at)
                VALUES ('EVENTS', 5, ?, 'INVALID_URL', 'legacy failure', ?)
                """, ingestedAt, ingestedAt);

        try (Connection connection = dataSource.getConnection()) {
            new V16__migrate_events_to_payload_and_domain_model().migrate(connection);
        }

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM gdelt_event_payloads"))
                .containsEntry("ID", 42L)
                .containsEntry("RAW_TSV", "unchanged raw row");
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM gdelt_events"))
                .containsEntry("ID", 42L)
                .containsEntry("GLOBAL_EVENT_ID", 123L)
                .containsEntry("EVENT_CODE", "042");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_id FROM article_signals WHERE signal_type = 'EVENTS'", Long.class))
                .isEqualTo(42L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_id FROM article_extraction_errors WHERE signal_type = 'EVENTS'", Long.class))
                .isEqualTo(42L);
        assertThat(tableCount(jdbcTemplate, "gdelt_raw_events")).isZero();
        assertThat(tableCount(jdbcTemplate, "gdelt_stage_events")).isZero();

        jdbcTemplate.update("""
                INSERT INTO gdelt_event_payloads
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (7, 'next-events.zip', ?, 1, 'next raw row', ?)
                """, ingestedAt, ingestedAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_event_payloads WHERE source_file = 'next-events.zip'", Long.class))
                .isEqualTo(43L);
    }

    private int tableCount(JdbcTemplate jdbcTemplate, String tableName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = ?
                """, Integer.class, tableName);
    }
}
