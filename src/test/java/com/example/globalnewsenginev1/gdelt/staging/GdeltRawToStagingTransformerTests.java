package com.example.globalnewsenginev1.gdelt.staging;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStagingResult;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltParserTests;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
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
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V7__add_gkg_article_titles.sql"));
            connection.createStatement().execute("ALTER TABLE gdelt_stage_gkg "
                    + "ADD COLUMN page_precise_pub_timestamp TIMESTAMP WITH TIME ZONE");
        }

        jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transformer = new GdeltRawToStagingTransformer(jdbcTemplate, transactionTemplate);
    }

    @Test
    void stagesCompletedRawRowsAndRecordsParseErrorsIdempotently() {
        Instant sourceTimestamp = Instant.parse("2026-07-05T12:00:00Z");
        long eventImportId = insertImportFile("EVENTS", "20260705120000.export.CSV.zip", sourceTimestamp);
        long mentionImportId = insertImportFile("MENTIONS", "20260705120000.mentions.CSV.zip", sourceTimestamp);
        long gkgImportId = insertImportFile("GKG", "20260705120000.gkg.csv.zip", sourceTimestamp);
        insertRaw("gdelt_raw_events", eventImportId, "20260705120000.export.CSV.zip", sourceTimestamp, 1,
                GdeltParserTests.eventRow());
        insertRaw("gdelt_raw_events", eventImportId, "20260705120000.export.CSV.zip", sourceTimestamp, 2,
                "bad\trow");
        insertRaw("gdelt_raw_mentions", mentionImportId, "20260705120000.mentions.CSV.zip", sourceTimestamp, 1,
                GdeltParserTests.mentionRow());
        insertRaw("gdelt_raw_gkg", gkgImportId, "20260705120000.gkg.csv.zip", sourceTimestamp, 1,
                GdeltParserTests.gkgRow());

        GdeltStagingResult firstRun = transformer.transformCompletedRawRows(100);
        GdeltStagingResult secondRun = transformer.transformCompletedRawRows(100);

        assertThat(firstRun).isEqualTo(new GdeltStagingResult(1, 1, 1, 1));
        assertThat(secondRun).isEqualTo(new GdeltStagingResult(0, 0, 0, 0));
        assertThat(countRows("gdelt_stage_events")).isEqualTo(1);
        assertThat(countRows("gdelt_stage_mentions")).isEqualTo(1);
        assertThat(countRows("gdelt_stage_gkg")).isEqualTo(1);
        assertThat(countRows("gdelt_stage_errors")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_precise_pub_timestamp FROM gdelt_stage_gkg",
                OffsetDateTime.class).toInstant()).isEqualTo(Instant.parse("2026-07-05T11:55:00Z"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT error_code FROM gdelt_stage_errors WHERE dataset_type = 'EVENTS'",
                String.class)).isEqualTo("COLUMN_COUNT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT global_event_id FROM gdelt_stage_events",
                Long.class)).isEqualTo(123L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_title FROM gdelt_stage_gkg",
                String.class)).isEqualTo("First Rain Exposes Flaws In ₹28 Lakh & More");
    }

    @Test
    void backfillsMetadataForAlreadyStagedGkgRowsIdempotently() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");
        long importId = insertImportFile("GKG", "20260705120000.gkg.csv.zip", timestamp);
        long rawId = insertRaw("gdelt_raw_gkg", importId, "20260705120000.gkg.csv.zip", timestamp, 1,
                GdeltParserTests.gkgRow());
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_gkg
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     gkg_record_id, document_identifier)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ?,
                       '20260705120000-1', 'https://example.org/a'
                FROM gdelt_raw_gkg WHERE id = ?
                """, utc(timestamp), rawId);

        transformer.transformCompletedRawRows(100);
        transformer.transformCompletedRawRows(100);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT page_title, page_precise_pub_timestamp, metadata_extracted FROM gdelt_stage_gkg"))
                .containsEntry("PAGE_TITLE", "First Rain Exposes Flaws In ₹28 Lakh & More")
                .containsEntry("PAGE_PRECISE_PUB_TIMESTAMP", utc(Instant.parse("2026-07-05T11:55:00Z")))
                .containsEntry("METADATA_EXTRACTED", true);
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
