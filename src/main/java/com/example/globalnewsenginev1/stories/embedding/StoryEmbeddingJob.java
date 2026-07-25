package com.example.globalnewsenginev1.stories.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "stories.embeddings.incremental.enabled", havingValue = "true",
        matchIfMissing = true)
class StoryEmbeddingJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryEmbeddingJob.class);

    private final StoryEmbeddingService service;
    private final int batchSize;
    private final int maxBatchesPerRun;

    StoryEmbeddingJob(
            StoryEmbeddingService service,
            @Value("${stories.embeddings.batch-size:100}") int batchSize,
            @Value("${stories.embeddings.max-batches-per-run:10}") int maxBatchesPerRun
    ) {
        this.service = service;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(
            initialDelayString = "${stories.embeddings.incremental.initial-delay:PT45S}",
            fixedDelayString = "${stories.embeddings.incremental.poll-interval:PT5M}"
    )
    void processIncremental() {
        int selected = 0;
        int processed = 0;
        int failed = 0;
        for (int batch = 0; batch < maxBatchesPerRun; batch++) {
            StoryEmbeddingService.ProcessingResult result = service.processIncremental(batchSize);
            selected += result.selected();
            processed += result.processed();
            failed += result.failed();
            if (result.selected() < batchSize) {
                break;
            }
        }
        if (selected > 0) {
            LOGGER.info("Story embedding incremental run completed: selected={}, processed={}, failed={}",
                    selected, processed, failed);
        }
    }
}
