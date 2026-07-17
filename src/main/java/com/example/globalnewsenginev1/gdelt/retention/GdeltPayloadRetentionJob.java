package com.example.globalnewsenginev1.gdelt.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@ConditionalOnProperty(name = "gdelt.retention.enabled", havingValue = "true", matchIfMissing = true)
class GdeltPayloadRetentionJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(GdeltPayloadRetentionJob.class);

    private final GdeltPayloadRetentionService retentionService;
    private final Duration retentionPeriod;
    private final int batchSize;
    private final int maxBatchesPerRun;

    GdeltPayloadRetentionJob(
            GdeltPayloadRetentionService retentionService,
            @Value("${gdelt.retention.period:PT168H}") Duration retentionPeriod,
            @Value("${gdelt.retention.batch-size:1000}") int batchSize,
            @Value("${gdelt.retention.max-batches-per-run:10}") int maxBatchesPerRun
    ) {
        GdeltPayloadRetentionService.validateRetentionPeriod(retentionPeriod);
        if (batchSize <= 0 || maxBatchesPerRun <= 0) {
            throw new IllegalArgumentException("Retention batch limits must be greater than zero");
        }
        this.retentionService = retentionService;
        this.retentionPeriod = retentionPeriod;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(
            initialDelayString = "${gdelt.retention.initial-delay:PT2M}",
            fixedDelayString = "${gdelt.retention.poll-interval:PT1H}"
    )
    void cleanupPayloads() {
        Instant cutoff = Instant.now().minus(retentionPeriod);
        GdeltPayloadRetentionResult total = new GdeltPayloadRetentionResult(0, 0, 0);
        for (int batch = 0; batch < maxBatchesPerRun; batch++) {
            GdeltPayloadRetentionResult current = retentionService.cleanupBatch(cutoff, batchSize);
            total = total.plus(current);
            if (current.totalDeleted() == 0) {
                break;
            }
        }

        LOGGER.info(
                "GDELT payload retention completed: eventsDeleted={}, mentionsDeleted={}, gkgDeleted={}, cutoff={}",
                total.eventsDeleted(), total.mentionsDeleted(), total.gkgDeleted(), cutoff);
    }
}
