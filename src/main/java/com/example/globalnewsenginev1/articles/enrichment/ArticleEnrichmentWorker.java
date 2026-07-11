package com.example.globalnewsenginev1.articles.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ArticleEnrichmentWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleEnrichmentWorker.class);
    private final ArticleEnrichmentRepository repository;
    private final ArticleHttpClient httpClient;
    private final ArticleHtmlParser parser;
    private final Clock clock;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;

    @Autowired
    ArticleEnrichmentWorker(ArticleEnrichmentRepository repository, ArticleHttpClient httpClient,
            ArticleHtmlParser parser,
            @Value("${articles.enrichment.retry-base-delay:PT1M}") Duration retryBaseDelay,
            @Value("${articles.enrichment.retry-max-delay:PT1H}") Duration retryMaxDelay) {
        this(repository, httpClient, parser, Clock.systemUTC(), retryBaseDelay, retryMaxDelay);
    }

    ArticleEnrichmentWorker(ArticleEnrichmentRepository repository, ArticleHttpClient httpClient,
            ArticleHtmlParser parser, Clock clock, Duration retryBaseDelay, Duration retryMaxDelay) {
        this.repository = repository; this.httpClient = httpClient; this.parser = parser; this.clock = clock;
        this.retryBaseDelay = retryBaseDelay; this.retryMaxDelay = retryMaxDelay;
    }

    public int processDue(int batchSize) {
        Instant now = clock.instant();
        repository.enqueueMissing(batchSize, now);
        List<ClaimedArticleEnrichment> claims = repository.claimDue(batchSize, now);
        for (ClaimedArticleEnrichment claim : claims) process(claim);
        return claims.size();
    }

    private void process(ClaimedArticleEnrichment claim) {
        try {
            ArticleEnrichmentResult result = parser.parse(httpClient.fetch(claim.canonicalUrl()));
            repository.markSucceeded(claim.articleId(), result, clock.instant());
        } catch (ArticleCrawlException ex) {
            Instant now = clock.instant();
            Instant retryAt = ex.retryable() ? now.plus(backoff(claim.attemptCount())) : null;
            repository.markFailed(claim.articleId(), ex.code(), ex.getMessage(), retryAt, now);
            LOGGER.warn("Article enrichment failed: articleId={}, code={}, retryable={}",
                    claim.articleId(), ex.code(), ex.retryable());
        } catch (RuntimeException ex) {
            Instant now = clock.instant();
            repository.markFailed(claim.articleId(), "INTERNAL_ERROR", "Unexpected enrichment failure",
                    now.plus(backoff(claim.attemptCount())), now);
            LOGGER.error("Unexpected article enrichment failure: articleId={}", claim.articleId(), ex);
        }
    }

    private Duration backoff(int attempt) {
        long multiplier = 1L << Math.min(20, Math.max(0, attempt - 1));
        try {
            Duration delay = retryBaseDelay.multipliedBy(multiplier);
            return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
        } catch (ArithmeticException ex) { return retryMaxDelay; }
    }
}
