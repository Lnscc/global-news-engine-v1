package db.migration;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ArticleLinksDomainModelMigrationTests {

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private OffsetDateTime timestamp;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:article-links-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V2__create_gdelt_staging_tables.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__create_articles.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V4__create_article_debug_views.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V7__add_gkg_article_titles.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V8__create_gkg_records.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V9__normalize_gkg_themes.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V10__store_gkg_themes_as_array.sql"));
            new V11__normalize_remaining_gkg_values().migrate(connection);
            new V12__add_gkg_publication_time().migrate(connection);
            new V13__add_gkg_sharing_image().migrate(connection);
            new V16__migrate_events_to_payload_and_domain_model().migrate(connection);
            new V17__migrate_mentions_to_payload_and_domain_model().migrate(connection);
            new V18__migrate_gkg_to_payload_and_domain_model().migrate(connection);
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        timestamp = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    }

    @Test
    void backfillsEveryLinkRebuildsViewsAndDropsSignalTable() throws Exception {
        long articleId = insertArticle("one");
        insertDomainRows();
        insertSignal("EVENTS", 11, articleId);
        insertSignal("MENTIONS", 12, articleId);

        try (Connection connection = dataSource.getConnection()) {
            new V20__move_article_links_to_domain_models().migrate(connection);
        }

        assertThat(jdbcTemplate.queryForObject("SELECT article_id FROM gdelt_events WHERE id = 11", Long.class))
                .isEqualTo(articleId);
        assertThat(jdbcTemplate.queryForObject("SELECT article_id FROM gdelt_mentions WHERE id = 12", Long.class))
                .isEqualTo(articleId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ARTICLE_SIGNALS'
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT signal_count FROM article_signal_summary_view WHERE article_id = ?
                """, Long.class, articleId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("""
                SELECT signal_type, signal_id, source_id FROM article_detail_view
                WHERE article_id = ? ORDER BY signal_type
                """, articleId)).allSatisfy(row -> assertThat(row.get("SIGNAL_ID")).isEqualTo(row.get("SOURCE_ID")));
    }

    @Test
    void abortsBeforeDropWhenAnExistingAssignmentConflicts() throws Exception {
        long expectedArticleId = insertArticle("expected");
        long conflictingArticleId = insertArticle("conflict");
        insertDomainRows();
        insertSignal("EVENTS", 11, expectedArticleId);
        jdbcTemplate.execute("ALTER TABLE gdelt_events ADD COLUMN article_id BIGINT REFERENCES articles(id)");
        jdbcTemplate.execute("ALTER TABLE gdelt_mentions ADD COLUMN article_id BIGINT REFERENCES articles(id)");
        jdbcTemplate.update("UPDATE gdelt_events SET article_id = ? WHERE id = 11", conflictingArticleId);

        assertThatIllegalStateException().isThrownBy(() -> {
            try (Connection connection = dataSource.getConnection()) {
                new V20__move_article_links_to_domain_models().migrateAssignmentsAndDrop(connection);
            }
        }).withMessageContaining("conflicting assignments");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ARTICLE_SIGNALS'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT article_id FROM gdelt_events WHERE id = 11", Long.class))
                .isEqualTo(conflictingArticleId);
    }

    @Test
    void abortsBeforeDropWhenASourceReferenceIsMissing() throws Exception {
        long articleId = insertArticle("missing");
        insertSignal("EVENTS", 999, articleId);

        assertThatIllegalStateException().isThrownBy(() -> {
            try (Connection connection = dataSource.getConnection()) {
                new V20__move_article_links_to_domain_models().migrate(connection);
            }
        }).withMessageContaining("invalid source references");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ARTICLE_SIGNALS'
                """, Integer.class)).isEqualTo(1);
    }

    private long insertArticle(String suffix) {
        jdbcTemplate.update("""
                INSERT INTO articles (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (?, ?, 'example.org', ?, ?, ?)
                """, "https://example.org/" + suffix, "hash-" + suffix, timestamp, timestamp, timestamp);
        return jdbcTemplate.queryForObject("SELECT id FROM articles WHERE url_hash = ?", Long.class, "hash-" + suffix);
    }

    private void insertDomainRows() {
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, started_at, completed_at)
                VALUES ('EVENTS', 'event.zip', 'https://example.org/event.zip', ?, 'COMPLETED', ?, ?),
                       ('MENTIONS', 'mention.zip', 'https://example.org/mention.zip', ?, 'COMPLETED', ?, ?)
                """, timestamp, timestamp, timestamp, timestamp, timestamp, timestamp);
        long eventImport = jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_import_files WHERE dataset_type = 'EVENTS'", Long.class);
        long mentionImport = jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_import_files WHERE dataset_type = 'MENTIONS'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_events
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     global_event_id, event_code, avg_tone, source_url)
                VALUES (11, ?, 'event.zip', ?, 1, ?, ?, 101, '042', -1.25, 'https://example.org/one')
                """, eventImport, timestamp, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO gdelt_mentions
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     global_event_id, mention_identifier, mention_doc_tone)
                VALUES (12, ?, 'mention.zip', ?, 1, ?, ?, 101, 'https://example.org/one', -1.5)
                """, mentionImport, timestamp, timestamp, timestamp);
    }

    private void insertSignal(String type, long sourceId, long articleId) {
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, articleId, type, sourceId, timestamp, timestamp);
    }
}
