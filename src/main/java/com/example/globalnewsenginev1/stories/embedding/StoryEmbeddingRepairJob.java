package com.example.globalnewsenginev1.stories.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "stories.embeddings.repair.enabled", havingValue = "true",
        matchIfMissing = true)
class StoryEmbeddingRepairJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryEmbeddingRepairJob.class);

    private final StoryEmbeddingService service;
    private final int batchSize;

    StoryEmbeddingRepairJob(
            StoryEmbeddingService service,
            @Value("${stories.embeddings.repair.batch-size:500}") int batchSize
    ) {
        this.service = service;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${stories.embeddings.repair.cron:0 15 3 * * *}",
            zone = "${stories.embeddings.repair.zone:UTC}")
    void repair() {
        StoryEmbeddingService.ProcessingResult result = service.repair(batchSize);
        LOGGER.info("Story embedding repair run completed: selected={}, processed={}, failed={}",
                result.selected(), result.processed(), result.failed());
    }
}
