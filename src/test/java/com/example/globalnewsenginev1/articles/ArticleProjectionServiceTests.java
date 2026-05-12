package com.example.globalnewsenginev1.articles;

import com.example.globalnewsenginev1.ingestion.SourceBatch;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleProjectionServiceTests {

    @Test
    void createsArticleFromCandidate() {
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        ArticleCandidate candidate = candidate(1L, "https://Example.test/news/#section", LocalDateTime.of(2026, 5, 9, 13, 30));

        when(articleRepository.findByCanonicalUrl("https://example.test/news")).thenReturn(Optional.empty());
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleProjectionService service = new ArticleProjectionService(articleRepository);

        boolean projected = service.project(candidate);

        assertThat(projected).isTrue();
        verify(articleRepository).save(any(Article.class));
    }

    @Test
    void deduplicatesByCanonicalUrlAndUpdatesLastSeenAt() {
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        ArticleCandidate firstCandidate = candidate(1L, "https://example.test/news", LocalDateTime.of(2026, 5, 9, 13, 30));
        ArticleCandidate laterCandidate = candidate(2L, "https://EXAMPLE.test/news/", LocalDateTime.of(2026, 5, 9, 13, 45));
        Article existing = new Article("https://example.test/news", firstCandidate);

        when(articleRepository.findByCanonicalUrl("https://example.test/news")).thenReturn(Optional.of(existing));

        ArticleProjectionService service = new ArticleProjectionService(articleRepository);

        boolean projected = service.project(laterCandidate);

        assertThat(projected).isTrue();
        assertThat(existing.getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 5, 9, 13, 30));
        assertThat(existing.getLastSeenAt()).isEqualTo(LocalDateTime.of(2026, 5, 9, 13, 45));
        verify(articleRepository).save(existing);
    }

    @Test
    void skipsInvalidArticleUrls() {
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        ArticleCandidate candidate = candidate(1L, "not-a-url", LocalDateTime.of(2026, 5, 9, 13, 30));

        ArticleProjectionService service = new ArticleProjectionService(articleRepository);

        boolean projected = service.project(candidate);

        assertThat(projected).isFalse();
        verify(articleRepository, never()).save(any(Article.class));
    }

    private ArticleCandidate candidate(Long id, String documentIdentifier, LocalDateTime publishedAt) {
        return new ArticleCandidate(
                id,
                new SourceBatch("GDELT", "20260509133000"),
                publishedAt,
                "example.test",
                documentIdentifier,
                "WB_678_DIGITAL_GOVERNMENT;TAX_FNCACT_PRESIDENT",
                "Jane Doe;John Smith",
                "Example Org",
                new BigDecimal("-2.5"),
                new BigDecimal("1.0"),
                new BigDecimal("3.5"),
                new BigDecimal("4.5"),
                new BigDecimal("850"),
                "https://example.test/image.jpg",
                "https://example.test/related.jpg",
                "<PAGE_TITLE>Example</PAGE_TITLE>"
        );
    }
}
