package com.example.globalnewsenginev1.gdelt.retention;

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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltPayloadRetentionPostgresIT {

    private DataSource adminDataSource;
    private String schemaName;
    private JdbcTemplate jdbcTemplate;
    private GdeltPayloadRetentionService retentionService;
    private long importFileId;

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
        var transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        retentionService = new GdeltPayloadRetentionService(
                new GdeltPayloadRetentionRepository(jdbcTemplate), transactionTemplate);
        importFileId = insertImportFile();
    }

    @AfterEach
    void tearDown() {
        if (adminDataSource != null && schemaName != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void deletesOnlyExpiredProcessedPayloadsInDeterministicBatches() {
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        Instant oldIngestedAt = now.minus(Duration.ofDays(20));
        Instant expiredParsedAt = now.minus(Duration.ofDays(8));
        Instant retainedParsedAt = now.minus(Duration.ofDays(6));

        long firstExpiredEvent = insertPayload("gdelt_event_payloads", "events-1.zip", 1, oldIngestedAt);
        insertEvent(firstExpiredEvent, "events-1.zip", 1, oldIngestedAt, expiredParsedAt);
        long secondExpiredEvent = insertPayload("gdelt_event_payloads", "events-2.zip", 2, oldIngestedAt);
        insertEvent(secondExpiredEvent, "events-2.zip", 2, oldIngestedAt, expiredParsedAt);
        long recentEvent = insertPayload("gdelt_event_payloads", "events-3.zip", 3, oldIngestedAt);
        insertEvent(recentEvent, "events-3.zip", 3, oldIngestedAt, retainedParsedAt);
        long unprocessedEvent = insertPayload("gdelt_event_payloads", "events-4.zip", 4, oldIngestedAt);

        long expiredMention = insertPayload("gdelt_mention_payloads", "mentions.zip", 1, oldIngestedAt);
        insertMention(expiredMention, oldIngestedAt, expiredParsedAt);
        long expiredGkg = insertPayload("gdelt_gkg_payloads", "gkg.zip", 1, oldIngestedAt);
        insertGkg(expiredGkg, oldIngestedAt, expiredParsedAt);
        insertResolvedProcessingError(firstExpiredEvent, now.minus(Duration.ofDays(10)));
        insertOpenProcessingError(unprocessedEvent, now.minus(Duration.ofDays(9)));

        assertThat(retentionService.health(now, Duration.ofDays(7)))
                .extracting(GdeltPayloadRetentionHealth::eligiblePayloadRows)
                .containsExactly(2L, 1L, 1L);

        assertThat(retentionService.cleanupBatch(now.minus(Duration.ofDays(7)), 1))
                .isEqualTo(new GdeltPayloadRetentionResult(1, 1, 1));
        assertThat(count("gdelt_event_payloads")).isEqualTo(3);
        assertThat(count("gdelt_mention_payloads")).isZero();
        assertThat(count("gdelt_gkg_payloads")).isZero();

        assertThat(retentionService.cleanupBatch(now.minus(Duration.ofDays(7)), 1))
                .isEqualTo(new GdeltPayloadRetentionResult(1, 0, 0));
        assertThat(retentionService.cleanupBatch(now.minus(Duration.ofDays(7)), 1).totalDeleted()).isZero();

        assertThat(count("gdelt_events")).isEqualTo(3);
        assertThat(count("gdelt_mentions")).isEqualTo(1);
        assertThat(count("gdelt_gkg")).isEqualTo(1);
        assertThat(count("gdelt_processing_errors")).isEqualTo(2);
        assertThat(payloadExists("gdelt_event_payloads", recentEvent)).isTrue();
        assertThat(payloadExists("gdelt_event_payloads", unprocessedEvent)).isTrue();
        assertThat(retentionService.health(now, Duration.ofDays(7)))
                .extracting(GdeltPayloadRetentionHealth::retainedPayloadRows)
                .containsExactly(2L, 0L, 0L);
    }

    private long insertImportFile() {
        var timestamp = Instant.parse("2026-07-01T00:00:00Z").atOffset(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, started_at, completed_at)
                VALUES ('EVENTS', 'retention-test.zip', 'https://example.org/retention-test.zip', ?,
                        'COMPLETED', ?, ?)
                """, timestamp, timestamp, timestamp);
        return jdbcTemplate.queryForObject("SELECT id FROM gdelt_import_files", Long.class);
    }

    private long insertPayload(String table, String sourceFile, long rowNumber, Instant ingestedAt) {
        jdbcTemplate.update("""
                INSERT INTO %s
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (?, ?, ?, ?, 'payload', ?)
                """.formatted(table), importFileId, sourceFile, utc(ingestedAt), rowNumber,
                utc(ingestedAt));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM " + table + " WHERE source_file = ? AND row_number = ?",
                Long.class, sourceFile, rowNumber);
    }

    private void insertEvent(long id, String sourceFile, long rowNumber, Instant ingestedAt, Instant parsedAt) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_events
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     global_event_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, importFileId, sourceFile, utc(ingestedAt), rowNumber, utc(ingestedAt), utc(parsedAt), id);
    }

    private void insertMention(long id, Instant ingestedAt, Instant parsedAt) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_mentions
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     global_event_id)
                VALUES (?, ?, 'mentions.zip', ?, 1, ?, ?, ?)
                """, id, importFileId, utc(ingestedAt), utc(ingestedAt), utc(parsedAt), id);
    }

    private void insertGkg(long id, Instant ingestedAt, Instant parsedAt) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_gkg
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     gkg_record_id, themes, persons, organizations, locations, created_at)
                VALUES (?, ?, 'gkg.zip', ?, 1, ?, ?, ?, ARRAY[]::TEXT[], ARRAY[]::TEXT[],
                        ARRAY[]::TEXT[], '[]'::JSONB, ?)
                """, id, importFileId, utc(ingestedAt), utc(ingestedAt), utc(parsedAt), "gkg-" + id,
                utc(parsedAt));
    }

    private void insertResolvedProcessingError(long sourceRowId, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_processing_errors
                    (dataset_type, source_row_id, import_file_id, source_timestamp, source_file, row_number,
                     failed_step, error_code, error_message, occurred_at, resolved_at)
                VALUES ('EVENTS', ?, ?, ?, 'events-1.zip', 1, 'PARSING', 'INVALID_ROW', 'historical', ?, ?)
                """, sourceRowId, importFileId, utc(occurredAt), utc(occurredAt),
                utc(occurredAt.plusSeconds(60)));
    }

    private void insertOpenProcessingError(long sourceRowId, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_processing_errors
                    (dataset_type, source_row_id, import_file_id, source_timestamp, source_file, row_number,
                     failed_step, error_code, error_message, occurred_at)
                VALUES ('EVENTS', ?, ?, ?, 'events-4.zip', 4, 'PARSING', 'INVALID_ROW', 'open', ?)
                """, sourceRowId, importFileId, utc(occurredAt), utc(occurredAt));
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private boolean payloadExists(String table, long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Long.class, id) == 1;
    }

    private java.time.OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
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
