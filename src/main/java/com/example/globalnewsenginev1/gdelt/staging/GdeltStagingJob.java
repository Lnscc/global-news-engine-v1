package com.example.globalnewsenginev1.gdelt.staging;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStagingResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "gdelt.staging.enabled", havingValue = "true", matchIfMissing = true)
class GdeltStagingJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(GdeltStagingJob.class);

    private final GdeltRawToStagingTransformer transformer;
    private final int batchSize;
    private final int maxBatchesPerRun;

    GdeltStagingJob(
            GdeltRawToStagingTransformer transformer,
            @Value("${gdelt.staging.batch-size:1000}") int batchSize,
            @Value("${gdelt.staging.max-batches-per-run:10}") int maxBatchesPerRun
    ) {
        this.transformer = transformer;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(
            initialDelayString = "${gdelt.staging.initial-delay:PT20S}",
            fixedDelayString = "${gdelt.staging.poll-interval:PT1M}"
    )
    void stageCompletedRawRows() {
        GdeltStagingResult total = new GdeltStagingResult(0, 0, 0, 0);
        for (int batch = 0; batch < maxBatchesPerRun; batch++) {
            GdeltStagingResult current = transformer.transformCompletedRawRows(batchSize);
            total = total.plus(current);
            if (current.totalStaged() == 0 && current.errors() == 0) {
                break;
            }
        }

        if (total.totalStaged() > 0 || total.errors() > 0) {
            LOGGER.info(
                    "GDELT staging completed: eventsStaged={}, mentionsStaged={}, gkgStaged={}, errors={}",
                    total.eventsStaged(), total.mentionsStaged(), total.gkgStaged(), total.errors());
        }
    }
}
