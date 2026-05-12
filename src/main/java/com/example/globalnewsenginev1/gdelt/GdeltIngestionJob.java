package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.gdelt.discovery.GdeltDiscoveryJob;
import com.example.globalnewsenginev1.gdelt.download.GdeltDownloadJob;
import com.example.globalnewsenginev1.gdelt.normalization.GdeltBatchNormalizationStatusJob;
import com.example.globalnewsenginev1.gdelt.normalization.GdeltEventNormalizationJob;
import com.example.globalnewsenginev1.gdelt.normalization.GdeltGkgNormalizationJob;
import com.example.globalnewsenginev1.gdelt.normalization.GdeltMentionNormalizationJob;
import com.example.globalnewsenginev1.gdelt.parser.GdeltParseJob;
import com.example.globalnewsenginev1.ingestion.IngestionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@ConditionalOnProperty(prefix = "gdelt.ingestion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GdeltIngestionJob implements IngestionJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltIngestionJob.class);

    private final GdeltDiscoveryJob discoveryJob;
    private final GdeltDownloadJob downloadJob;
    private final GdeltParseJob parseJob;
    private final GdeltEventNormalizationJob eventNormalizationJob;
    private final GdeltMentionNormalizationJob mentionNormalizationJob;
    private final GdeltGkgNormalizationJob gkgNormalizationJob;
    private final GdeltBatchNormalizationStatusJob batchNormalizationStatusJob;

    public GdeltIngestionJob(
            GdeltDiscoveryJob discoveryJob,
            GdeltDownloadJob downloadJob,
            GdeltParseJob parseJob,
            GdeltEventNormalizationJob eventNormalizationJob,
            GdeltMentionNormalizationJob mentionNormalizationJob,
            GdeltGkgNormalizationJob gkgNormalizationJob,
            GdeltBatchNormalizationStatusJob batchNormalizationStatusJob
    ) {
        this.discoveryJob = discoveryJob;
        this.downloadJob = downloadJob;
        this.parseJob = parseJob;
        this.eventNormalizationJob = eventNormalizationJob;
        this.mentionNormalizationJob = mentionNormalizationJob;
        this.gkgNormalizationJob = gkgNormalizationJob;
        this.batchNormalizationStatusJob = batchNormalizationStatusJob;
    }

    @Override
    public void runIngestion() {
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
        while (step.runNextBatch()) {
            completed++;
        }
        log.info("Completed {} GDELT {} batch(es)", completed, stage);
    }

    private void runNormalizationRounds() {
        int eventRows = eventNormalizationJob.run();
        int mentionRows = mentionNormalizationJob.run();
        int gkgRows = gkgNormalizationJob.run();
        log.info(
                "Completed GDELT normalization: {} events, {} mentions, {} GKG records",
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
