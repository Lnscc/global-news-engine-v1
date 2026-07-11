package com.example.globalnewsenginev1.articles.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "articles.enabled", havingValue = "true", matchIfMissing = true)
class ArticleExtractorJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleExtractorJob.class);

    private final ArticleExtractorService extractorService;
    private final int batchSize;
    private final int maxBatchesPerRun;

    ArticleExtractorJob(
            ArticleExtractorService extractorService,
            @Value("${articles.batch-size:1000}") int batchSize,
            @Value("${articles.max-batches-per-run:10}") int maxBatchesPerRun
    ) {
        this.extractorService = extractorService;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(
            initialDelayString = "${articles.initial-delay:PT30S}",
            fixedDelayString = "${articles.poll-interval:PT1M}"
    )
    void extractArticles() {
        ArticleExtractionResult total = new ArticleExtractionResult(0, 0, 0);
        for (int batch = 0; batch < maxBatchesPerRun; batch++) {
            ArticleExtractionResult current = extractorService.extractArticles(batchSize);
            total = total.plus(current);
            if (current.totalProcessed() == 0) {
                break;
            }
        }

        if (total.totalProcessed() > 0) {
            LOGGER.info(
                    "Article extraction completed: articlesCreated={}, signalsCreated={}, errorsCreated={}",
                    total.articlesCreated(), total.signalsCreated(), total.errorsCreated());
        }
    }
}
