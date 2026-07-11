package com.example.globalnewsenginev1.articles.enrichment;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import static org.mockito.Mockito.*;

class ArticleEnrichmentWorkerTests {
    @Test
    void retriesTemporaryFailureAndContinuesWithRemainingBatch() throws Exception {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        ArticleEnrichmentRepository repository = mock(ArticleEnrichmentRepository.class);
        ArticleHttpClient client = mock(ArticleHttpClient.class);
        ArticleHtmlParser parser = new ArticleHtmlParser();
        ClaimedArticleEnrichment failed = new ClaimedArticleEnrichment(1, "https://example.org/a", 2);
        ClaimedArticleEnrichment succeeded = new ClaimedArticleEnrichment(2, "https://example.org/b", 1);
        when(repository.claimDue(2, now)).thenReturn(List.of(failed, succeeded));
        when(client.fetch(failed.canonicalUrl())).thenThrow(new ArticleCrawlException("TIMEOUT", "timeout", true));
        when(client.fetch(succeeded.canonicalUrl())).thenReturn(
                new CrawledArticlePage(URI.create(succeeded.canonicalUrl()), "<title>Worked</title>"));

        ArticleEnrichmentWorker worker = new ArticleEnrichmentWorker(repository, client, parser,
                Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(1), Duration.ofHours(1));
        worker.processDue(2);

        verify(repository).enqueueMissing(2, now);
        verify(repository).markFailed(1, "TIMEOUT", "timeout", now.plus(Duration.ofMinutes(2)), now);
        verify(repository).markSucceeded(eq(2L), argThat(result -> "Worked".equals(result.title())), eq(now));
    }

    @Test
    void permanentFailureHasNoNextAttempt() throws Exception {
        Instant now = Instant.parse("2026-07-11T10:00:00Z");
        ArticleEnrichmentRepository repository = mock(ArticleEnrichmentRepository.class);
        ArticleHttpClient client = mock(ArticleHttpClient.class);
        ClaimedArticleEnrichment claim = new ClaimedArticleEnrichment(1, "http://127.0.0.1/a", 1);
        when(repository.claimDue(1, now)).thenReturn(List.of(claim));
        when(client.fetch(claim.canonicalUrl())).thenThrow(
                new ArticleCrawlException("FORBIDDEN_ADDRESS", "private", false));

        new ArticleEnrichmentWorker(repository, client, new ArticleHtmlParser(), Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(1), Duration.ofHours(1)).processDue(1);

        verify(repository).enqueueMissing(1, now);
        verify(repository).markFailed(1, "FORBIDDEN_ADDRESS", "private", null, now);
    }
}
