package com.example.globalnewsenginev1.articles;

import db.migration.V11__normalize_remaining_gkg_values;
import db.migration.V12__add_gkg_publication_time;
import db.migration.V13__add_gkg_sharing_image;
import db.migration.V16__migrate_events_to_payload_and_domain_model;
import db.migration.V17__migrate_mentions_to_payload_and_domain_model;
import db.migration.V18__migrate_gkg_to_payload_and_domain_model;
import db.migration.V20__move_article_links_to_domain_models;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.SqlArrayValue;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleDatabaseViewsTests {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:article-views-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V2__create_gdelt_staging_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V4__create_article_debug_views.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V7__add_gkg_article_titles.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V8__create_gkg_records.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V9__normalize_gkg_themes.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V10__store_gkg_themes_as_array.sql"));
            new V11__normalize_remaining_gkg_values().migrate(connection);
            new V12__add_gkg_publication_time().migrate(connection);
            new V13__add_gkg_sharing_image().migrate(connection);
            new V16__migrate_events_to_payload_and_domain_model().migrate(connection);
            new V17__migrate_mentions_to_payload_and_domain_model().migrate(connection);
            new V18__migrate_gkg_to_payload_and_domain_model().migrate(connection);
            new V20__move_article_links_to_domain_models().migrate(connection);
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void exposesArticleSummaryAndDetailIncludingArticlesWithoutSignals() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-11T10:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO articles
                    (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """,
                "https://example.com/one", "hash-one", "example.com", timestamp, timestamp, timestamp,
                "https://example.com/two", "hash-two", "example.com", timestamp, timestamp, timestamp);
        Long articleId = jdbcTemplate.queryForObject(
                "SELECT id FROM articles WHERE url_hash = 'hash-one'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, started_at, completed_at)
                VALUES ('EVENTS', 'event.zip', 'https://example.com/event.zip', ?, 'COMPLETED', ?, ?),
                       ('GKG', 'gkg.zip', 'https://example.com/gkg.zip', ?, 'COMPLETED', ?, ?)
                """, timestamp, timestamp, timestamp, timestamp, timestamp, timestamp);
        Long eventImportId = jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_import_files WHERE dataset_type = 'EVENTS'", Long.class);
        Long gkgImportId = jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_import_files WHERE dataset_type = 'GKG'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_events
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     global_event_id, event_code, avg_tone, source_url, article_id)
                VALUES (101, ?, 'event.zip', ?, 1, ?, ?, 9001, '010', -1.5,
                        'https://example.com/one', ?)
                """, eventImportId, timestamp, timestamp, timestamp, articleId);
        jdbcTemplate.update("""
                INSERT INTO gdelt_gkg
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     gkg_record_id, document_identifier, themes_raw, persons_raw, organizations_raw,
                     locations_raw, tone_raw, article_id, themes, persons, organizations, locations,
                     tone_value, created_at)
                VALUES (102, ?, 'gkg.zip', ?, 1, ?, ?, 'record-102', 'https://example.com/one',
                        'THEME_B', '', '', '', '2.5', ?, ?, ?, ?, CAST('[]' AS JSON), 2.5, ?)
                """, gkgImportId, timestamp.plusMinutes(5), timestamp, timestamp, articleId,
                new SqlArrayValue("TEXT", "THEME_B"), new SqlArrayValue("TEXT"),
                new SqlArrayValue("TEXT"), timestamp);

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT signal_count, event_signal_count, mention_signal_count, gkg_signal_count
                FROM article_signal_summary_view
                WHERE article_id = ?
                """, articleId);
        assertThat(((Number) summary.get("SIGNAL_COUNT")).longValue()).isEqualTo(2);
        assertThat(((Number) summary.get("EVENT_SIGNAL_COUNT")).longValue()).isEqualTo(1);
        assertThat(((Number) summary.get("MENTION_SIGNAL_COUNT")).longValue()).isZero();
        assertThat(((Number) summary.get("GKG_SIGNAL_COUNT")).longValue()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForList("""
                SELECT canonical_url, signal_type, source_id, themes
                FROM article_detail_view
                WHERE article_id = ?
                ORDER BY source_id
                """, articleId))
                .hasSize(2)
                .extracting(row -> row.get("SIGNAL_TYPE"))
                .containsExactly("EVENTS", "GKG");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_detail_view
                WHERE canonical_url = 'https://example.com/two' AND signal_id IS NULL
                """, Long.class)).isEqualTo(1);
    }
}
