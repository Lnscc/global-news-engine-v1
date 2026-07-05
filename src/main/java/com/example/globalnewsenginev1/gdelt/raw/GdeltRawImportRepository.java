package com.example.globalnewsenginev1.gdelt.raw;

import com.example.globalnewsenginev1.gdelt.GdeltDataset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
class GdeltRawImportRepository {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    GdeltRawImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<Instant> failedWindowTimestamps(int limit) {
        return jdbcTemplate.query("""
                SELECT DISTINCT source_timestamp
                FROM gdelt_import_files
                WHERE status = 'FAILED'
                ORDER BY source_timestamp
                LIMIT ?
                """, (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(), limit);
    }

    boolean isCompleted(GdeltDataset dataset, String sourceFile) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gdelt_import_files
                WHERE dataset_type = ? AND source_file = ? AND status = 'COMPLETED'
                """, Integer.class, dataset.name(), sourceFile);
        return count != null && count > 0;
    }

    long completedRowCount(GdeltDataset dataset, String sourceFile) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT row_count FROM gdelt_import_files
                WHERE dataset_type = ? AND source_file = ? AND status = 'COMPLETED'
                """, Long.class, dataset.name(), sourceFile);
        return count == null ? 0 : count;
    }

    long markProcessing(GdeltDataset dataset, String sourceFile, String sourceUrl, Instant sourceTimestamp) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM gdelt_import_files WHERE dataset_type = ? AND source_file = ?
                """, (resultSet, rowNum) -> resultSet.getLong(1), dataset.name(), sourceFile);
        Instant startedAt = Instant.now();
        if (!ids.isEmpty()) {
            long id = ids.getFirst();
            jdbcTemplate.update("""
                    UPDATE gdelt_import_files
                    SET source_url = ?, source_timestamp = ?, checksum_sha256 = NULL, status = 'PROCESSING',
                        row_count = 0, started_at = ?, completed_at = NULL, error_message = NULL
                    WHERE id = ?
                    """, sourceUrl, utc(sourceTimestamp), utc(startedAt), id);
            return id;
        }
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, started_at)
                VALUES (?, ?, ?, ?, 'PROCESSING', ?)
                """, dataset.name(), sourceFile, sourceUrl, utc(sourceTimestamp), utc(startedAt));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM gdelt_import_files WHERE dataset_type = ? AND source_file = ?
                """, Long.class, dataset.name(), sourceFile);
    }

    long insertRows(
            GdeltDataset dataset,
            String sourceFile,
            Instant sourceTimestamp,
            long importFileId,
            InputStream inputStream
    ) throws IOException {
        String sql = """
                INSERT INTO %s
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.formatted(dataset.tableName());
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        Instant ingestedAt = Instant.now();
        long rowNumber = 0;

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            rowNumber++;
            batch.add(new Object[]{importFileId, sourceFile, utc(sourceTimestamp), rowNumber, line, utc(ingestedAt)});
            if (batch.size() == BATCH_SIZE) {
                jdbcTemplate.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            jdbcTemplate.batchUpdate(sql, batch);
        }
        return rowNumber;
    }

    void markCompleted(long importFileId, GdeltRawImporter.ImportPayload payload) {
        jdbcTemplate.update("""
                UPDATE gdelt_import_files
                SET checksum_sha256 = ?, status = 'COMPLETED', row_count = ?, completed_at = ?, error_message = NULL
                WHERE id = ?
                """, payload.checksumSha256(), payload.rowCount(), utc(Instant.now()), importFileId);
    }

    void markFailed(long importFileId, RuntimeException exception) {
        jdbcTemplate.update("""
                UPDATE gdelt_import_files
                SET status = 'FAILED', completed_at = ?, error_message = ?
                WHERE id = ?
                """, utc(Instant.now()), exception.getMessage(), importFileId);
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
