package com.example.globalnewsenginev1.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class GdeltRawImporter {

    private static final int BATCH_SIZE = 500;
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient;
    private final URI downloadBaseUri;

    @Autowired
    GdeltRawImporter(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            @Value("${gdelt.download-base-url:http://data.gdeltproject.org/gdeltv2}") URI downloadBaseUri
    ) {
        this(jdbcTemplate, transactionTemplate, HttpClient.newHttpClient(), downloadBaseUri);
    }

    GdeltRawImporter(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            HttpClient httpClient,
            URI downloadBaseUri
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.httpClient = httpClient;
        this.downloadBaseUri = normalizeDirectoryUri(downloadBaseUri);
    }

    public List<GdeltImportResult> importWindow(Instant sourceTimestamp) {
        List<GdeltImportResult> results = new ArrayList<>();
        for (GdeltDataset dataset : GdeltDataset.values()) {
            results.add(importDataset(dataset, sourceTimestamp));
        }
        return results;
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

    GdeltImportResult importDataset(GdeltDataset dataset, Instant sourceTimestamp) {
        String sourceFile = FILE_TIMESTAMP.format(sourceTimestamp) + "." + dataset.fileSuffix();
        String sourceUrl = downloadBaseUri.resolve(sourceFile).toString();

        if (isCompleted(dataset, sourceFile)) {
            return new GdeltImportResult(sourceFile, completedRowCount(dataset, sourceFile), true);
        }

        long importFileId = markProcessing(dataset, sourceFile, sourceUrl, sourceTimestamp);
        try {
            ImportPayload payload = transactionTemplate.execute(status ->
                    streamIntoDatabase(dataset, sourceFile, sourceUrl, sourceTimestamp, importFileId));
            markCompleted(importFileId, payload);
            return new GdeltImportResult(sourceFile, payload.rowCount(), false);
        } catch (RuntimeException exception) {
            markFailed(importFileId, exception);
            throw exception;
        }
    }

    private ImportPayload streamIntoDatabase(
            GdeltDataset dataset,
            String sourceFile,
            String sourceUrl,
            Instant sourceTimestamp,
            long importFileId
    ) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl)).GET().build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IllegalStateException("GDELT download failed with HTTP " + response.statusCode() + ": " + sourceUrl);
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream body = response.body();
                 DigestInputStream digestStream = new DigestInputStream(body, digest);
                 ZipInputStream zipStream = new ZipInputStream(digestStream, StandardCharsets.UTF_8)) {
                ZipEntry entry = zipStream.getNextEntry();
                if (entry == null) {
                    throw new IllegalStateException("GDELT ZIP file is empty: " + sourceUrl);
                }
                long rowCount = insertRows(dataset, sourceFile, sourceTimestamp, importFileId, zipStream);
                drain(digestStream);
                return new ImportPayload(rowCount, HexFormat.of().formatHex(digest.digest()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read GDELT file: " + sourceUrl, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading GDELT file: " + sourceUrl, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private long insertRows(
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

    private void drain(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        while (inputStream.read(buffer) != -1) {
            // Drain the compressed response so the checksum covers the complete ZIP file.
        }
    }

    private boolean isCompleted(GdeltDataset dataset, String sourceFile) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gdelt_import_files
                WHERE dataset_type = ? AND source_file = ? AND status = 'COMPLETED'
                """, Integer.class, dataset.name(), sourceFile);
        return count != null && count > 0;
    }

    private long completedRowCount(GdeltDataset dataset, String sourceFile) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT row_count FROM gdelt_import_files
                WHERE dataset_type = ? AND source_file = ? AND status = 'COMPLETED'
                """, Long.class, dataset.name(), sourceFile);
        return count == null ? 0 : count;
    }

    private long markProcessing(GdeltDataset dataset, String sourceFile, String sourceUrl, Instant sourceTimestamp) {
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

    private void markCompleted(long importFileId, ImportPayload payload) {
        jdbcTemplate.update("""
                UPDATE gdelt_import_files
                SET checksum_sha256 = ?, status = 'COMPLETED', row_count = ?, completed_at = ?, error_message = NULL
                WHERE id = ?
                """, payload.checksumSha256(), payload.rowCount(), utc(Instant.now()), importFileId);
    }

    private void markFailed(long importFileId, RuntimeException exception) {
        jdbcTemplate.update("""
                UPDATE gdelt_import_files
                SET status = 'FAILED', completed_at = ?, error_message = ?
                WHERE id = ?
                """, utc(Instant.now()), exception.getMessage(), importFileId);
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private URI normalizeDirectoryUri(URI uri) {
        String value = uri.toString();
        return value.endsWith("/") ? uri : URI.create(value + "/");
    }

    private record ImportPayload(long rowCount, String checksumSha256) {
    }
}
