package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.articles.ArticleProjectionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private final ArticleProjectionJob articleProjectionJob;

    public GdeltIngestionScheduler(
            GdeltDiscoveryJob discoveryJob,
            GdeltDownloadJob downloadJob,
            GdeltParseJob parseJob,
            GdeltEventNormalizationJob eventNormalizationJob,
            GdeltMentionNormalizationJob mentionNormalizationJob,
            GdeltGkgNormalizationJob gkgNormalizationJob,
            GdeltBatchNormalizationStatusJob batchNormalizationStatusJob,
            ArticleProjectionJob articleProjectionJob
    ) {
        this.discoveryJob = discoveryJob;
        this.downloadJob = downloadJob;
        this.parseJob = parseJob;
        this.eventNormalizationJob = eventNormalizationJob;
        this.mentionNormalizationJob = mentionNormalizationJob;
        this.gkgNormalizationJob = gkgNormalizationJob;
        this.batchNormalizationStatusJob = batchNormalizationStatusJob;
        this.articleProjectionJob = articleProjectionJob;
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
            int projectedArticles = articleProjectionJob.run();
            log.info("Projected {} article row(s)", projectedArticles);
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
