package com.example.globalnewsenginev1.articles.enrichment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "articles.enrichment.enabled", havingValue = "true", matchIfMissing = true)
class ArticleEnrichmentJob {
    private final ArticleEnrichmentWorker worker;
    private final int batchSize;

    ArticleEnrichmentJob(ArticleEnrichmentWorker worker,
            @Value("${articles.enrichment.batch-size:20}") int batchSize) {
        this.worker = worker; this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${articles.enrichment.initial-delay:PT45S}",
            fixedDelayString = "${articles.enrichment.poll-interval:PT2M}")
    void enrichArticles() { worker.processDue(batchSize); }
}
