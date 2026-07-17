package com.example.globalnewsenginev1.gdelt.staging;

import com.example.globalnewsenginev1.articles.extraction.ArticleExtractorService;
import com.example.globalnewsenginev1.articles.normalization.ArticleUrlNormalizer;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltParserTests;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltProcessingErrorPostgresIT {

    private DataSource adminDataSource;
    private String schemaName;
    private JdbcTemplate jdbcTemplate;
    private GdeltRawToStagingTransformer transformer;
    private ArticleExtractorService articleExtractor;

    @BeforeEach
    void setUp() {
        adminDataSource = adminDataSource();
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "PostgreSQL integration test requires local compose database at jdbc:postgresql://localhost:5432/gne");

        schemaName = "it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schemaName);
        DataSource dataSource = schemaDataSource(schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transformer = new GdeltRawToStagingTransformer(jdbcTemplate, transactionTemplate);
        articleExtractor = new ArticleExtractorService(
                jdbcTemplate, transactionTemplate, new ArticleUrlNormalizer());
    }

    @AfterEach
    void tearDown() {
        if (adminDataSource != null && schemaName != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void retainsAttemptsAndResolvesThemAfterSuccessfulRetry() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");
        var databaseTimestamp = timestamp.atOffset(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, started_at, completed_at)
                VALUES ('EVENTS', 'events.zip', 'https://example.org/events.zip', ?,
                        'COMPLETED', ?, ?)
                """, databaseTimestamp, databaseTimestamp, databaseTimestamp);
        Long importId = jdbcTemplate.queryForObject("SELECT id FROM gdelt_import_files", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_event_payloads
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (?, 'events.zip', ?, 1, 'bad row', ?)
                """, importId, databaseTimestamp, databaseTimestamp);
        Long sourceRowId = jdbcTemplate.queryForObject("SELECT id FROM gdelt_event_payloads", Long.class);

        assertThat(transformer.transformCompletedRawRows(100).errors()).isEqualTo(1);
        assertThat(transformer.transformCompletedRawRows(100).errors()).isEqualTo(1);
        jdbcTemplate.update("UPDATE gdelt_event_payloads SET raw_tsv = ? WHERE id = ?",
                GdeltParserTests.eventRow(), sourceRowId);
        assertThat(transformer.transformCompletedRawRows(100).eventsStaged()).isEqualTo(1);
        assertThat(articleExtractor.extractArticles(100).signalsCreated()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_processing_errors WHERE source_row_id = ?",
                Integer.class, sourceRowId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_processing_errors WHERE source_row_id = ? AND resolved_at IS NOT NULL",
                Integer.class, sourceRowId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_events WHERE id = ?",
                Integer.class, sourceRowId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_signals
                WHERE signal_type = 'EVENTS' AND source_id = ?
                """, Integer.class, sourceRowId)).isEqualTo(1);
    }

    private DataSource adminDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne"));
        dataSource.setUser(System.getProperty("it.postgres.username", "gne"));
        dataSource.setPassword(System.getProperty("it.postgres.password", "gne"));
        return dataSource;
    }

    private DataSource schemaDataSource(String schema) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        String jdbcUrl = System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne");
        dataSource.setUrl(jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        dataSource.setUser(System.getProperty("it.postgres.username", "gne"));
        dataSource.setPassword(System.getProperty("it.postgres.password", "gne"));
        return dataSource;
    }

    private boolean canConnect(DataSource dataSource) {
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }
}
