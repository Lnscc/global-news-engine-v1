package com.example.globalnewsenginev1.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "gdelt.ingestion.enabled", havingValue = "true", matchIfMissing = true)
class GdeltPollingImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GdeltPollingImporter.class);

    private final GdeltMasterfileDiscovery discovery;
    private final GdeltRawImporter importer;
    private final int maxWindowsPerPoll;
    private final int maxFailedWindowsPerPoll;

    GdeltPollingImporter(
            GdeltMasterfileDiscovery discovery,
            GdeltRawImporter importer,
            @Value("${gdelt.ingestion.max-windows-per-poll:4}") int maxWindowsPerPoll,
            @Value("${gdelt.ingestion.max-failed-windows-per-poll:20}") int maxFailedWindowsPerPoll
    ) {
        this.discovery = discovery;
        this.importer = importer;
        this.maxWindowsPerPoll = maxWindowsPerPoll;
        this.maxFailedWindowsPerPoll = maxFailedWindowsPerPoll;
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
        List<Instant> windows = windowsToImport();

        for (Instant window : windows) {
            try {
                var results = importer.importWindow(window);
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
                LOGGER.warn("Failed to import GDELT window {}: {}", window, exception.getMessage());
            }
        }

        LOGGER.info(
                "GDELT polling completed: discoveredWindows={}, firstWindow={}, lastWindow={}, importedWindows={}, importedFiles={}, importedRows={}, skippedFiles={}, failedWindows={}",
                windows.size(), firstTimestamp(windows), lastTimestamp(windows), imported, importedFiles, importedRows,
                skippedFiles, failed);
    }

    private List<Instant> windowsToImport() {
        Set<Instant> timestamps = new LinkedHashSet<>(importer.failedWindowTimestamps(maxFailedWindowsPerPoll));
        discovery.discoverLatestCompleteWindows(maxWindowsPerPoll).stream()
                .map(GdeltCompleteWindow::sourceTimestamp)
                .forEach(timestamps::add);
        return new ArrayList<>(timestamps);
    }

    private Instant firstTimestamp(List<Instant> windows) {
        return windows.isEmpty() ? null : windows.getFirst();
    }

    private Instant lastTimestamp(List<Instant> windows) {
        return windows.isEmpty() ? null : windows.getLast();
    }
}
