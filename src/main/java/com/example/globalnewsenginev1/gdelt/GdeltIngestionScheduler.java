package com.example.globalnewsenginev1.gdelt;

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

    public GdeltIngestionScheduler(GdeltDiscoveryJob discoveryJob, GdeltDownloadJob downloadJob) {
        this.discoveryJob = discoveryJob;
        this.downloadJob = downloadJob;
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
            downloadJob.runNextBatch();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("GDELT ingestion was interrupted", ex);
        } catch (Exception ex) {
            log.warn("GDELT ingestion failed", ex);
        }
    }
}
