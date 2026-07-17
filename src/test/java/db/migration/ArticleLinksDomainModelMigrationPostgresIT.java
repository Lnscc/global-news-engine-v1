package db.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ArticleLinksDomainModelMigrationPostgresIT {

    private DataSource adminDataSource;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private String schemaName;
    private OffsetDateTime timestamp;

    @BeforeEach
    void setUp() {
        adminDataSource = postgresDataSource(null);
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "PostgreSQL migration test requires the local compose database");
        schemaName = "it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schemaName);
        dataSource = postgresDataSource(schemaName);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schemaName).defaultSchema(schemaName)
                .target(MigrationVersion.fromVersion("19")).load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        timestamp = OffsetDateTime.parse("2026-07-17T10:00:00Z");
    }

    @AfterEach
    void tearDown() {
        if (adminDataSource != null && schemaName != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void backfillsLinksAndDropsArticleSignalsOnPostgres() {
        long articleId = insertArticle("backfill");
        insertDomainRows();
        insertSignal("EVENTS", 11, articleId);
        insertSignal("MENTIONS", 12, articleId);

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schemaName).defaultSchema(schemaName).load().migrate();

        assertThat(jdbcTemplate.queryForObject("SELECT article_id FROM gdelt_events WHERE id = 11", Long.class))
                .isEqualTo(articleId);
        assertThat(jdbcTemplate.queryForObject("SELECT article_id FROM gdelt_mentions WHERE id = 12", Long.class))
                .isEqualTo(articleId);
        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('article_signals')", String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT signal_count FROM article_signal_summary_view WHERE article_id = ?", Long.class, articleId))
                .isEqualTo(2);
    }

    @Test
    void rejectsAConflictingPreassignmentBeforeDropOnPostgres() {
        long expected = insertArticle("expected");
        long conflicting = insertArticle("conflicting");
        insertDomainRows();
        insertSignal("EVENTS", 11, expected);
        jdbcTemplate.execute("ALTER TABLE gdelt_events ADD COLUMN article_id BIGINT REFERENCES articles(id)");
        jdbcTemplate.execute("ALTER TABLE gdelt_mentions ADD COLUMN article_id BIGINT REFERENCES articles(id)");
        jdbcTemplate.update("UPDATE gdelt_events SET article_id = ? WHERE id = 11", conflicting);

        assertThatIllegalStateException().isThrownBy(() -> {
            try (Connection connection = dataSource.getConnection()) {
                new V20__move_article_links_to_domain_models().migrateAssignmentsAndDrop(connection);
            }
        }).withMessageContaining("conflicting assignments");

        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('article_signals')", String.class)).isNotNull();
    }

    @Test
    void rejectsAMissingSourceBeforeDropOnPostgres() {
        insertSignal("MENTIONS", 999, insertArticle("missing"));

        assertThatIllegalStateException().isThrownBy(() -> {
            try (Connection connection = dataSource.getConnection()) {
                new V20__move_article_links_to_domain_models().migrate(connection);
            }
        }).withMessageContaining("invalid source references");

        assertThat(jdbcTemplate.queryForObject("SELECT to_regclass('article_signals')", String.class)).isNotNull();
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
                VALUES (11, ?, 'event.zip', ?, 1, ?, ?, 101, '042', -1.25, 'https://example.org/a')
                """, eventImport, timestamp, timestamp, timestamp);
        jdbcTemplate.update("""
                INSERT INTO gdelt_mentions
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     global_event_id, mention_identifier, mention_doc_tone)
                VALUES (12, ?, 'mention.zip', ?, 1, ?, ?, 101, 'https://example.org/a', -1.5)
                """, mentionImport, timestamp, timestamp, timestamp);
    }

    private void insertSignal(String type, long sourceId, long articleId) {
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, articleId, type, sourceId, timestamp, timestamp);
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
