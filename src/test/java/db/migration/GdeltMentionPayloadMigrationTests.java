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

class GdeltMentionPayloadMigrationTests {

    @Test
    void preservesMentionIdentityAndRemapsArticleReferences() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mention-payload-migration-" + System.nanoTime()
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
                VALUES (7, 'MENTIONS', 'mentions.zip', 'https://example.org/mentions.zip', ?, 'COMPLETED', ?)
                """, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO gdelt_raw_mentions
                    (id, import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (42, 7, 'mentions.zip', ?, 3, 'unchanged raw row', ?),
                       (43, 7, 'mentions.zip', ?, 4, 'unparsed raw row', ?)
                """, ingestedAt, ingestedAt, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_mentions
                    (id, raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     global_event_id, mention_type, mention_identifier, confidence, mention_doc_tone)
                VALUES (5, 42, 7, 'mentions.zip', ?, 3, ?, 123, 1,
                        'https://example.org/article', 80, -1.25)
                """, ingestedAt, parsedAt);
        jdbcTemplate.update("""
                INSERT INTO articles
                    (id, canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (1, 'https://example.org/article', 'hash', 'example.org', ?, ?, ?)
                """, ingestedAt, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, created_at)
                VALUES (1, 'MENTIONS', 5, ?, ?)
                """, ingestedAt, ingestedAt);
        jdbcTemplate.update("""
                INSERT INTO article_extraction_errors
                    (signal_type, source_id, source_timestamp, error_code, error_message, created_at)
                VALUES ('MENTIONS', 5, ?, 'INVALID_URL', 'legacy failure', ?)
                """, ingestedAt, ingestedAt);

        try (Connection connection = dataSource.getConnection()) {
            new V17__migrate_mentions_to_payload_and_domain_model().migrate(connection);
        }

        assertThat(jdbcTemplate.queryForList("SELECT id, raw_tsv FROM gdelt_mention_payloads ORDER BY id"))
                .hasSize(2);
        assertThat(jdbcTemplate.queryForMap("SELECT id, raw_tsv FROM gdelt_mention_payloads WHERE id = 42"))
                .containsEntry("ID", 42L)
                .containsEntry("RAW_TSV", "unchanged raw row");
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM gdelt_mentions"))
                .containsEntry("ID", 42L)
                .containsEntry("GLOBAL_EVENT_ID", 123L)
                .containsEntry("MENTION_IDENTIFIER", "https://example.org/article")
                .doesNotContainKeys("RAW_TSV", "PROCESSING_STATUS");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_id FROM article_signals WHERE signal_type = 'MENTIONS'", Long.class))
                .isEqualTo(42L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT source_id FROM article_extraction_errors WHERE signal_type = 'MENTIONS'", Long.class))
                .isEqualTo(42L);
        assertThat(tableCount(jdbcTemplate, "gdelt_raw_mentions")).isZero();
        assertThat(tableCount(jdbcTemplate, "gdelt_stage_mentions")).isZero();

        jdbcTemplate.update("""
                INSERT INTO gdelt_mention_payloads
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (7, 'next-mentions.zip', ?, 1, 'next raw row', ?)
                """, ingestedAt, ingestedAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_mention_payloads WHERE source_file = 'next-mentions.zip'", Long.class))
                .isEqualTo(44L);
    }

    private int tableCount(JdbcTemplate jdbcTemplate, String tableName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = ?
                """, Integer.class, tableName);
    }
}
