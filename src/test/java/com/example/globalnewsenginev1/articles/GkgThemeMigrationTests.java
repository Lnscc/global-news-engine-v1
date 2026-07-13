package com.example.globalnewsenginev1.articles;

import db.migration.V11__normalize_remaining_gkg_values;
import db.migration.V12__add_gkg_publication_time;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltParserTests;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GkgThemeMigrationTests {

    @Test
    void backfillsTrimmedDeduplicatedThemesAsArray() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:gkg-theme-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "V1__create_gdelt_raw_tables.sql");
            execute(connection, "V2__create_gdelt_staging_tables.sql");
            execute(connection, "V3__create_articles.sql");
            execute(connection, "V4__create_article_debug_views.sql");
            execute(connection, "V7__add_gkg_article_titles.sql");
            execute(connection, "V8__create_gkg_records.sql");
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-12T10:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO articles
                    (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES ('https://example.org/article', 'hash', 'example.org', ?, ?, ?)
                """, timestamp, timestamp, timestamp);
        Long articleId = jdbcTemplate.queryForObject("SELECT id FROM articles", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, row_count,
                     started_at, completed_at)
                VALUES ('GKG', 'sample.gkg.csv.zip', 'http://localhost/sample', ?, 'COMPLETED', 1, ?, ?)
                """, timestamp, timestamp, timestamp);
        Long importFileId = jdbcTemplate.queryForObject("SELECT id FROM gdelt_import_files", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_raw_gkg
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (?, 'sample.gkg.csv.zip', ?, 1, ?, ?)
                """, importFileId, timestamp, GdeltParserTests.gkgRow(), timestamp);
        Long rawId = jdbcTemplate.queryForObject("SELECT id FROM gdelt_raw_gkg", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_gkg
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     gkg_record_id, document_date, document_identifier, metadata_extracted)
                VALUES (?, ?, 'sample.gkg.csv.zip', ?, 1, ?, '20260705120000-1', ?,
                        'https://example.org/a', TRUE)
                """, rawId, importFileId, timestamp, timestamp,
                OffsetDateTime.parse("2026-07-05T12:00:00Z"));
        Long stageId = jdbcTemplate.queryForObject("SELECT id FROM gdelt_stage_gkg", Long.class);
        jdbcTemplate.update("""
                UPDATE gdelt_stage_gkg
                SET persons = ' Jane Doe;;John Doe;Jane Doe ',
                    organizations = ' Example Org;Example Org ',
                    locations = '4#Exeter, Devon, United Kingdom#UK#UKD4#50.7#-3.53333#-2595805;bad',
                    tone = '-3.5,2.0,5.5,7.5,1.25,0.75,420'
                WHERE id = ?
                """, stageId);
        jdbcTemplate.update("""
                INSERT INTO gdelt_gkg_records
                    (source_id, article_id, source_timestamp, themes_raw, persons_raw,
                     organizations_raw, locations_raw, tone_raw, created_at)
                VALUES (?, ?, ?, ' CLIMATE ; ;ENERGY;CLIMATE;CLIMATE_CHANGE ',
                        ' Jane Doe;;John Doe;Jane Doe ', ' Example Org;Example Org ',
                        '4#Exeter, Devon, United Kingdom#UK#UKD4#50.7#-3.53333#-2595805;bad',
                        '-3.5,2.0,5.5,7.5,1.25,0.75,420', ?)
                """, stageId, articleId, timestamp, timestamp);

        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "V9__normalize_gkg_themes.sql");
            execute(connection, "V10__store_gkg_themes_as_array.sql");
            new V11__normalize_remaining_gkg_values().migrate(connection);
            new V12__add_gkg_publication_time().migrate(connection);
        }

        java.util.List<String> themes = jdbcTemplate.queryForObject("SELECT themes FROM gdelt_gkg_records",
                (resultSet, rowNum) -> java.util.Arrays.stream(
                                (Object[]) resultSet.getArray("themes").getArray())
                        .map(Object::toString).toList());
        assertThat(themes)
                .containsExactly("CLIMATE", "ENERGY", "CLIMATE_CHANGE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'GDELT_GKG_RECORDS' AND column_name = 'THEMES_RAW'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'GDELT_GKG_THEMES'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'GDELT_GKG_RECORDS' "
                        + "AND column_name IN ('PERSONS_RAW', 'ORGANIZATIONS_RAW', 'LOCATIONS_RAW', 'TONE_RAW')",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT CARDINALITY(persons) FROM gdelt_gkg_records",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT CARDINALITY(organizations) FROM gdelt_gkg_records",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT CAST(locations AS VARCHAR) FROM gdelt_gkg_records",
                String.class)).contains("Exeter").doesNotContain("bad");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT tone_value, tone_positive_score, tone_negative_score, tone_polarity,
                       tone_activity_reference_density, tone_self_group_reference_density, tone_word_count
                FROM gdelt_gkg_records
                """))
                .containsEntry("TONE_VALUE", -3.5)
                .containsEntry("TONE_POSITIVE_SCORE", 2.0)
                .containsEntry("TONE_WORD_COUNT", 420);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_precise_pub_timestamp FROM gdelt_stage_gkg", OffsetDateTime.class))
                .isEqualTo(OffsetDateTime.parse("2026-07-05T11:55:00Z"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_precise_pub_timestamp FROM gdelt_gkg_records", OffsetDateTime.class))
                .isEqualTo(OffsetDateTime.parse("2026-07-05T11:55:00Z"));
    }

    private void execute(Connection connection, String migration) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + migration));
    }
}
