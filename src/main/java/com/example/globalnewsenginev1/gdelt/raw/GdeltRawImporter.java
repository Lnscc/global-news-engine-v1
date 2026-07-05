package com.example.globalnewsenginev1.gdelt.raw;

import com.example.globalnewsenginev1.gdelt.GdeltDataset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class GdeltRawImporter {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final GdeltRawImportRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final GdeltRawDownloader downloader;
    private final URI downloadBaseUri;

    @Autowired
    GdeltRawImporter(
            GdeltRawImportRepository repository,
            TransactionTemplate transactionTemplate,
            GdeltRawDownloader downloader,
            @Value("${gdelt.download-base-url:http://data.gdeltproject.org/gdeltv2}") URI downloadBaseUri
    ) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.downloader = downloader;
        this.downloadBaseUri = normalizeDirectoryUri(downloadBaseUri);
    }

    GdeltRawImporter(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            HttpClient httpClient,
            URI downloadBaseUri
    ) {
        this.repository = new GdeltRawImportRepository(jdbcTemplate);
        this.transactionTemplate = transactionTemplate;
        this.downloader = new GdeltRawDownloader(httpClient);
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
        return repository.failedWindowTimestamps(limit);
    }

    GdeltImportResult importDataset(GdeltDataset dataset, Instant sourceTimestamp) {
        String sourceFile = FILE_TIMESTAMP.format(sourceTimestamp) + "." + dataset.fileSuffix();
        String sourceUrl = downloadBaseUri.resolve(sourceFile).toString();

        if (repository.isCompleted(dataset, sourceFile)) {
            return new GdeltImportResult(sourceFile, repository.completedRowCount(dataset, sourceFile), true);
        }

        long importFileId = repository.markProcessing(dataset, sourceFile, sourceUrl, sourceTimestamp);
        try {
            ImportPayload payload = transactionTemplate.execute(status ->
                    streamIntoDatabase(dataset, sourceFile, sourceUrl, sourceTimestamp, importFileId));
            repository.markCompleted(importFileId, payload);
            return new GdeltImportResult(sourceFile, payload.rowCount(), false);
        } catch (RuntimeException exception) {
            repository.markFailed(importFileId, exception);
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
        return downloader.download(sourceUrl, (InputStream inputStream) ->
                repository.insertRows(dataset, sourceFile, sourceTimestamp, importFileId, inputStream));
    }

    private URI normalizeDirectoryUri(URI uri) {
        String value = uri.toString();
        return value.endsWith("/") ? uri : URI.create(value + "/");
    }

    record ImportPayload(long rowCount, String checksumSha256) {
    }
}
