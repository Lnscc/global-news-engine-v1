package com.example.globalnewsenginev1.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(name = "gdelt.ingestion.enabled", havingValue = "true", matchIfMissing = true)
class GdeltPollingImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GdeltPollingImporter.class);

    private final GdeltMasterfileDiscovery discovery;
    private final GdeltRawImporter importer;
    private final int maxWindowsPerPoll;

    GdeltPollingImporter(
            GdeltMasterfileDiscovery discovery,
            GdeltRawImporter importer,
            @Value("${gdelt.ingestion.max-windows-per-poll:4}") int maxWindowsPerPoll
    ) {
        this.discovery = discovery;
        this.importer = importer;
        this.maxWindowsPerPoll = maxWindowsPerPoll;
    }

    @Scheduled(
            initialDelayString = "${gdelt.ingestion.initial-delay:PT10S}",
            fixedDelayString = "${gdelt.ingestion.poll-interval:PT5M}"
    )
    void importLatestCompleteWindows() {
        int imported = 0;
        long importedFiles = 0;
        long importedRows = 0;
        int skippedFiles = 0;
        int failed = 0;
        List<GdeltCompleteWindow> windows = discovery.discoverLatestCompleteWindows(maxWindowsPerPoll);

        for (GdeltCompleteWindow window : windows) {
            try {
                var results = importer.importWindow(window.sourceTimestamp());
                long skippedInWindow = results.stream().filter(GdeltImportResult::skipped).count();
                skippedFiles += skippedInWindow;
                importedFiles += results.size() - skippedInWindow;
                importedRows += results.stream()
                        .filter(result -> !result.skipped())
                        .mapToLong(GdeltImportResult::rowCount)
                        .sum();
                if (results.stream().anyMatch(result -> !result.skipped())) {
                    imported++;
                }
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn("Failed to import GDELT window {}: {}", window.sourceTimestamp(), exception.getMessage());
            }
        }

        LOGGER.info(
                "GDELT polling completed: discoveredWindows={}, firstWindow={}, lastWindow={}, importedWindows={}, importedFiles={}, importedRows={}, skippedFiles={}, failedWindows={}",
                windows.size(), firstTimestamp(windows), lastTimestamp(windows), imported, importedFiles, importedRows,
                skippedFiles, failed);
    }

    private Instant firstTimestamp(List<GdeltCompleteWindow> windows) {
        return windows.isEmpty() ? null : windows.getFirst().sourceTimestamp();
    }

    private Instant lastTimestamp(List<GdeltCompleteWindow> windows) {
        return windows.isEmpty() ? null : windows.getLast().sourceTimestamp();
    }
}
