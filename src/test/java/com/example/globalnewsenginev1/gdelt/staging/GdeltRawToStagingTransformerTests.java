package com.example.globalnewsenginev1.gdelt.staging;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStagingResult;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltEventParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltGkgParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltMentionParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltParserTests;
import db.migration.V15__create_gdelt_processing_errors;
import db.migration.V16__migrate_events_to_payload_and_domain_model;
import db.migration.V17__migrate_mentions_to_payload_and_domain_model;
import db.migration.V11__normalize_remaining_gkg_values;
import db.migration.V12__add_gkg_publication_time;
import db.migration.V13__add_gkg_sharing_image;
import db.migration.V18__migrate_gkg_to_payload_and_domain_model;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltRawToStagingTransformerTests {

    private JdbcTemplate jdbcTemplate;
    private GdeltRawToStagingTransformer transformer;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:gdelt-stage-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V2__create_gdelt_staging_tables.sql"));
            new V15__create_gdelt_processing_errors().migrate(connection);
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
        }

        jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transformer = new GdeltRawToStagingTransformer(jdbcTemplate, transactionTemplate);
    }

    @Test
    void stagesCompletedRawRowsAndRecordsEveryFailedAttempt() {
        Instant sourceTimestamp = Instant.parse("2026-07-05T12:00:00Z");
        long eventImportId = insertImportFile("EVENTS", "20260705120000.export.CSV.zip", sourceTimestamp);
        long mentionImportId = insertImportFile("MENTIONS", "20260705120000.mentions.CSV.zip", sourceTimestamp);
        long gkgImportId = insertImportFile("GKG", "20260705120000.gkg.csv.zip", sourceTimestamp);
        insertRaw("gdelt_event_payloads", eventImportId, "20260705120000.export.CSV.zip", sourceTimestamp, 1,
                GdeltParserTests.eventRow());
        insertRaw("gdelt_event_payloads", eventImportId, "20260705120000.export.CSV.zip", sourceTimestamp, 2,
                "bad\trow");
        insertRaw("gdelt_mention_payloads", mentionImportId, "20260705120000.mentions.CSV.zip", sourceTimestamp, 1,
                GdeltParserTests.mentionRow());
        insertRaw("gdelt_gkg_payloads", gkgImportId, "20260705120000.gkg.csv.zip", sourceTimestamp, 1,
                GdeltParserTests.gkgRow());

        GdeltStagingResult firstRun = transformer.transformCompletedRawRows(100);
        GdeltStagingResult secondRun = transformer.transformCompletedRawRows(100);

        assertThat(firstRun).isEqualTo(new GdeltStagingResult(1, 1, 1, 1));
        assertThat(secondRun).isEqualTo(new GdeltStagingResult(0, 0, 0, 1));
        assertThat(countRows("gdelt_events")).isEqualTo(1);
        assertThat(countRows("gdelt_mentions")).isEqualTo(1);
        assertThat(countRows("gdelt_gkg")).isEqualTo(1);
        assertThat(countRows("gdelt_processing_errors")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_precise_pub_timestamp FROM gdelt_gkg",
                OffsetDateTime.class).toInstant()).isEqualTo(Instant.parse("2026-07-05T11:55:00Z"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sharing_image_url FROM gdelt_gkg", String.class))
                .isEqualTo("https://cdn.example.org/news/main.jpg?width=1200");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_code FROM gdelt_processing_errors WHERE dataset_type = 'EVENTS' LIMIT 1",
                String.class)).isEqualTo("COLUMN_COUNT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_processing_errors WHERE resolved_at IS NULL",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_processing_errors WHERE error_message LIKE '%bad%'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT global_event_id FROM gdelt_events",
                Long.class)).isEqualTo(123L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_title FROM gdelt_gkg",
                String.class)).isEqualTo("First Rain Exposes Flaws In ₹28 Lakh & More");
    }

    @Test
    void resolvesHistoricalErrorsAfterARecordCanBeParsedSuccessfully() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");
        long importId = insertImportFile("EVENTS", "20260705120000.export.CSV.zip", timestamp);
        long rawId = insertRaw("gdelt_event_payloads", importId, "20260705120000.export.CSV.zip", timestamp, 1,
                "bad\trow");

        assertThat(transformer.transformCompletedRawRows(100).errors()).isEqualTo(1);
        jdbcTemplate.update("UPDATE gdelt_event_payloads SET raw_tsv = ? WHERE id = ?",
                GdeltParserTests.eventRow(), rawId);
        jdbcTemplate.update("UPDATE gdelt_processing_errors SET occurred_at = ? WHERE source_row_id = ?",
                utc(timestamp), rawId);
        assertThat(transformer.transformCompletedRawRows(100).eventsStaged()).isEqualTo(1);

        assertThat(countRows("gdelt_processing_errors")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_processing_errors WHERE resolved_at IS NOT NULL",
                Integer.class)).isEqualTo(1);
        assertThat(countRows("gdelt_events")).isEqualTo(1);
    }

    @Test
    void retriesFailedMentionPayloadWithTheSameIdentity() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");
        long importId = insertImportFile("MENTIONS", "20260705120000.mentions.CSV.zip", timestamp);
        long payloadId = insertRaw("gdelt_mention_payloads", importId,
                "20260705120000.mentions.CSV.zip", timestamp, 1, "bad\trow");

        assertThat(transformer.transformCompletedRawRows(100).errors()).isEqualTo(1);
        jdbcTemplate.update("UPDATE gdelt_mention_payloads SET raw_tsv = ? WHERE id = ?",
                GdeltParserTests.mentionRow(), payloadId);
        jdbcTemplate.update("UPDATE gdelt_processing_errors SET occurred_at = ? WHERE source_row_id = ?",
                utc(timestamp), payloadId);

        assertThat(transformer.transformCompletedRawRows(100).mentionsStaged()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT id FROM gdelt_mentions", Long.class)).isEqualTo(payloadId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gdelt_processing_errors
                WHERE dataset_type = 'MENTIONS' AND source_row_id = ? AND resolved_at IS NOT NULL
                """, Integer.class, payloadId)).isEqualTo(1);
    }

    @Test
    void waitsForRetryDelayBeforeTryingAnOpenErrorAgain() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");
        long importId = insertImportFile("EVENTS", "20260705120000.export.CSV.zip", timestamp);
        insertRaw("gdelt_event_payloads", importId,
                "20260705120000.export.CSV.zip", timestamp, 1, "bad\trow");
        GdeltRawToStagingTransformer delayedTransformer = new GdeltRawToStagingTransformer(
                jdbcTemplate,
                new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource())),
                new GdeltEventParser(),
                new GdeltMentionParser(),
                new GdeltGkgParser(),
                Duration.ofMinutes(1));

        assertThat(delayedTransformer.transformCompletedRawRows(100).errors()).isEqualTo(1);
        assertThat(delayedTransformer.transformCompletedRawRows(100).errors()).isZero();
        assertThat(countRows("gdelt_processing_errors")).isEqualTo(1);
    }

    @Test
    void doesNotReprocessAnExistingGkgRow() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");
        long importId = insertImportFile("GKG", "20260705120000.gkg.csv.zip", timestamp);
        long rawId = insertRaw("gdelt_gkg_payloads", importId, "20260705120000.gkg.csv.zip", timestamp, 1,
                GdeltParserTests.gkgRow());
        jdbcTemplate.update("""
                INSERT INTO gdelt_gkg
                    (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                     gkg_record_id, document_identifier, sharing_image_url, page_title,
                     page_precise_pub_timestamp, themes, persons, organizations, locations, created_at)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ingested_at, ?,
                       '20260705120000-1', 'https://example.org/a',
                       'https://cdn.example.org/news/main.jpg?width=1200',
                       'First Rain Exposes Flaws In â‚¹28 Lakh & More', ?,
                       ARRAY[], ARRAY[], ARRAY[], CAST('[]' AS JSON), ?
                FROM gdelt_gkg_payloads WHERE id = ?
                """, utc(timestamp), utc(Instant.parse("2026-07-05T11:55:00Z")), utc(timestamp), rawId);

        transformer.transformCompletedRawRows(100);
        transformer.transformCompletedRawRows(100);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT sharing_image_url, page_title, page_precise_pub_timestamp "
                        + "FROM gdelt_gkg"))
                .containsEntry("SHARING_IMAGE_URL", "https://cdn.example.org/news/main.jpg?width=1200")
                .containsKey("PAGE_TITLE")
                .containsEntry("PAGE_PRECISE_PUB_TIMESTAMP", utc(Instant.parse("2026-07-05T11:55:00Z")));
    }

    private long insertImportFile(String datasetType, String sourceFile, Instant sourceTimestamp) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, row_count, started_at, completed_at)
                VALUES (?, ?, ?, ?, 'COMPLETED', 1, ?, ?)
                """,
                datasetType, sourceFile, "http://localhost/" + sourceFile, utc(sourceTimestamp),
                utc(sourceTimestamp), utc(sourceTimestamp));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM gdelt_import_files WHERE dataset_type = ? AND source_file = ?
                """, Long.class, datasetType, sourceFile);
    }

    private long insertRaw(
            String tableName,
            long importFileId,
            String sourceFile,
            Instant sourceTimestamp,
            long rowNumber,
            String rawTsv
    ) {
        jdbcTemplate.update("""
                INSERT INTO %s
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(tableName),
                importFileId, sourceFile, utc(sourceTimestamp), rowNumber, rawTsv, utc(sourceTimestamp));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM %s WHERE source_file = ? AND row_number = ?
                """.formatted(tableName), Long.class, sourceFile, rowNumber);
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
