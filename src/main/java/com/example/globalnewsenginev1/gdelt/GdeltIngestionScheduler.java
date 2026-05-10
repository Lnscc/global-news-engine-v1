package com.example.globalnewsenginev1.gdelt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "gdelt.ingestion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GdeltIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(GdeltIngestionScheduler.class);

    private final GdeltDiscoveryJob discoveryJob;
    private final GdeltDownloadJob downloadJob;
    private final GdeltParseJob parseJob;
    private final GdeltEventNormalizationJob eventNormalizationJob;
    private final GdeltMentionNormalizationJob mentionNormalizationJob;
    private final GdeltGkgNormalizationJob gkgNormalizationJob;
    private final GdeltBatchNormalizationStatusJob batchNormalizationStatusJob;
    private final int maxBatchesPerRun;

    public GdeltIngestionScheduler(
            GdeltDiscoveryJob discoveryJob,
            GdeltDownloadJob downloadJob,
            GdeltParseJob parseJob,
            GdeltEventNormalizationJob eventNormalizationJob,
            GdeltMentionNormalizationJob mentionNormalizationJob,
            GdeltGkgNormalizationJob gkgNormalizationJob,
            GdeltBatchNormalizationStatusJob batchNormalizationStatusJob,
            @Value("${gdelt.ingestion.max-batches-per-run:1}") int maxBatchesPerRun
    ) {
        this.discoveryJob = discoveryJob;
        this.downloadJob = downloadJob;
        this.parseJob = parseJob;
        this.eventNormalizationJob = eventNormalizationJob;
        this.mentionNormalizationJob = mentionNormalizationJob;
        this.gkgNormalizationJob = gkgNormalizationJob;
        this.batchNormalizationStatusJob = batchNormalizationStatusJob;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void discoverBatchesOnStartup() {
        runDiscovery();
    }

    @Scheduled(
            fixedDelayString = "${gdelt.ingestion.fixed-delay:PT15M}",
            initialDelayString = "${gdelt.ingestion.initial-delay:PT15M}"
    )
    public void discoverBatches() {
        runDiscovery();
    }

    private void runDiscovery() {
        try {
            discoveryJob.run();
            runBatches("download", downloadJob::runNextBatch);
            runBatches("parse", parseJob::runNextBatch);
            runNormalizationRounds();
            batchNormalizationStatusJob.run();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("GDELT ingestion was interrupted", ex);
        } catch (Exception ex) {
            log.warn("GDELT ingestion failed", ex);
        }
    }

    private void runBatches(String stage, BatchStep step) {
        int completed = 0;
        for (int index = 0; index < maxBatchesPerRun; index++) {
            if (!step.runNextBatch()) {
                break;
            }
            completed++;
        }
        log.info("Completed {} GDELT {} batch(es)", completed, stage);
    }

    private void runNormalizationRounds() {
        int eventRows = 0;
        int mentionRows = 0;
        int gkgRows = 0;
        for (int index = 0; index < maxBatchesPerRun; index++) {
            eventRows += eventNormalizationJob.run();
            mentionRows += mentionNormalizationJob.run();
            gkgRows += gkgNormalizationJob.run();
        }
        log.info(
                "Completed {} GDELT normalization round(s): {} events, {} mentions, {} GKG records",
                maxBatchesPerRun,
                eventRows,
                mentionRows,
                gkgRows
        );
    }

    @FunctionalInterface
    private interface BatchStep {
        boolean runNextBatch();
    }
}
