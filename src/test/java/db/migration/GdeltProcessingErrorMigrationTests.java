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

class GdeltProcessingErrorMigrationTests {

    @Test
    void migratesLegacyErrorsWithoutRawPayloadAndDropsLegacyTable() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:processing-error-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V2__create_gdelt_staging_tables.sql"));
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        var timestamp = Instant.parse("2026-07-05T12:00:00Z").atOffset(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, started_at)
                VALUES ('EVENTS', 'events.zip', 'https://example.org/events.zip', ?, 'COMPLETED', ?)
                """, timestamp, timestamp);
        Long importId = jdbcTemplate.queryForObject("SELECT id FROM gdelt_import_files", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_errors
                    (dataset_type, raw_id, import_file_id, source_file, source_timestamp, row_number,
                     raw_tsv, error_code, error_message, staged_at)
                VALUES ('EVENTS', 42, ?, 'events.zip', ?, 7, 'large raw payload',
                        'COLUMN_COUNT', 'expected 61 columns', ?)
                """, importId, timestamp, timestamp);

        try (Connection connection = dataSource.getConnection()) {
            new V14__create_gdelt_processing_errors().migrate(connection);
        }

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM gdelt_processing_errors"))
                .containsEntry("DATASET_TYPE", "EVENTS")
                .containsEntry("SOURCE_ROW_ID", 42L)
                .containsEntry("FAILED_STEP", "PARSING")
                .containsEntry("ERROR_CODE", "COLUMN_COUNT")
                .containsEntry("RESOLVED_AT", null);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE lower(table_name) = 'gdelt_processing_errors'
                  AND lower(column_name) = 'raw_tsv'
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE lower(table_name) = 'gdelt_stage_errors'
                """, Integer.class)).isZero();
    }
}
