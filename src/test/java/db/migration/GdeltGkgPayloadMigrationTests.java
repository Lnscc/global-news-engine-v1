package db.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.SqlArrayValue;

import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltGkgPayloadMigrationTests {

    @Test
    void preservesPayloadIdentityNormalizedValuesAndArticleRelationship() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:gkg-payload-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "V1__create_gdelt_raw_tables.sql");
            execute(connection, "V2__create_gdelt_staging_tables.sql");
            execute(connection, "V3__create_articles.sql");
            execute(connection, "V4__create_article_debug_views.sql");
            execute(connection, "V7__add_gkg_article_titles.sql");
            execute(connection, "V8__create_gkg_records.sql");
            execute(connection, "V9__normalize_gkg_themes.sql");
            execute(connection, "V10__store_gkg_themes_as_array.sql");
            new V11__normalize_remaining_gkg_values().migrate(connection);
            new V12__add_gkg_publication_time().migrate(connection);
            new V13__add_gkg_sharing_image().migrate(connection);
            new V16__migrate_events_to_payload_and_domain_model().migrate(connection);
            new V17__migrate_mentions_to_payload_and_domain_model().migrate(connection);
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var ingestedAt = Instant.parse("2026-07-05T12:00:00Z").atOffset(ZoneOffset.UTC);
        var parsedAt = Instant.parse("2026-07-05T12:01:00Z").atOffset(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO gdelt_import_files
                    (id, dataset_type, source_file, source_url, source_timestamp, status, started_at)
                VALUES (7, 'GKG', 'gkg.zip', 'https://example.org/gkg.zip', ?, 'COMPLETED', ?)
                """, ingestedAt, ingestedAt);
        jdbc.update("""
                INSERT INTO gdelt_raw_gkg
                    (id, import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (42, 7, 'gkg.zip', ?, 3, 'unchanged raw row', ?),
                       (43, 7, 'gkg.zip', ?, 4, 'unparsed raw row', ?)
                """, ingestedAt, ingestedAt, ingestedAt, ingestedAt);
        jdbc.update("""
                INSERT INTO gdelt_stage_gkg
                    (id, raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     gkg_record_id, document_identifier, themes, persons, organizations, locations, tone,
                     metadata_extracted)
                VALUES (5, 42, 7, 'gkg.zip', ?, 3, ?, 'record-42', 'https://example.org/article',
                        'RAW_A;RAW_B', 'Raw Person', 'Raw Org', '1#Berlin#GM#GM16#52.5#13.4#1',
                        '-1,2,3,5,6,7,8', TRUE)
                """, ingestedAt, parsedAt);
        jdbc.update("""
                INSERT INTO articles
                    (id, canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (1, 'https://example.org/article', 'hash', 'example.org', ?, ?, ?)
                """, ingestedAt, ingestedAt, ingestedAt);
        jdbc.update("""
                INSERT INTO gdelt_gkg_records
                    (source_id, article_id, source_timestamp, document_identifier, themes, persons,
                     organizations, locations, tone_value, tone_positive_score, tone_word_count, created_at)
                VALUES (5, 1, ?, 'https://example.org/article', ?, ?, ?, CAST(? AS JSON), -1.0, 2.0, 8, ?)
                """, ingestedAt, new SqlArrayValue("TEXT", "PRODUCT_A", "PRODUCT_B"),
                new SqlArrayValue("TEXT", "Product Person"), new SqlArrayValue("TEXT", "Product Org"),
                "[]", parsedAt);

        try (Connection connection = dataSource.getConnection()) {
            new V18__migrate_gkg_to_payload_and_domain_model().migrate(connection);
        }

        assertThat(jdbc.queryForMap("SELECT id, raw_tsv FROM gdelt_gkg_payloads WHERE id = 42"))
                .containsEntry("ID", 42L).containsEntry("RAW_TSV", "unchanged raw row");
        assertThat(jdbc.queryForMap("SELECT id, article_id, gkg_record_id, tone_value FROM gdelt_gkg"))
                .containsEntry("ID", 42L).containsEntry("ARTICLE_ID", 1L)
                .containsEntry("GKG_RECORD_ID", "record-42").containsEntry("TONE_VALUE", -1.0);
        assertThat(arrayValues(jdbc, "themes")).containsExactly("PRODUCT_A", "PRODUCT_B");
        assertThat(arrayValues(jdbc, "persons")).containsExactly("Product Person");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gdelt_gkg_payloads", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gdelt_gkg", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT gkg_signal_count FROM article_signal_summary_view WHERE article_id = 1", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT source_id FROM article_detail_view WHERE article_id = 1 AND signal_type = 'GKG'",
                Long.class)).isEqualTo(42L);
        assertThat(tableCount(jdbc, "gdelt_raw_gkg")).isZero();
        assertThat(tableCount(jdbc, "gdelt_stage_gkg")).isZero();
        assertThat(tableCount(jdbc, "gdelt_gkg_records")).isZero();

        jdbc.update("""
                INSERT INTO gdelt_gkg_payloads
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (7, 'next-gkg.zip', ?, 1, 'next row', ?)
                """, ingestedAt, ingestedAt);
        assertThat(jdbc.queryForObject(
                "SELECT id FROM gdelt_gkg_payloads WHERE source_file = 'next-gkg.zip'", Long.class))
                .isEqualTo(44L);
    }

    private void execute(Connection connection, String migration) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + migration));
    }

    private java.util.List<String> arrayValues(JdbcTemplate jdbc, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM gdelt_gkg", (row, rowNum) ->
                java.util.Arrays.stream((Object[]) row.getArray(1).getArray()).map(Object::toString).toList());
    }

    private int tableCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = ?
                """, Integer.class, tableName);
    }
}
