package com.example.globalnewsenginev1.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
        int skippedFiles = 0;
        int failed = 0;

        for (GdeltCompleteWindow window : discovery.discoverLatestCompleteWindows(maxWindowsPerPoll)) {
            try {
                var results = importer.importWindow(window.sourceTimestamp());
                skippedFiles += results.stream().filter(GdeltImportResult::skipped).count();
                if (results.stream().anyMatch(result -> !result.skipped())) {
                    imported++;
                }
            } catch (RuntimeException exception) {
                failed++;
                LOGGER.warn("Failed to import GDELT window {}: {}", window.sourceTimestamp(), exception.getMessage());
            }
        }

        LOGGER.info("GDELT polling completed: importedWindows={}, skippedFiles={}, failedWindows={}",
                imported, skippedFiles, failed);
    }
}
