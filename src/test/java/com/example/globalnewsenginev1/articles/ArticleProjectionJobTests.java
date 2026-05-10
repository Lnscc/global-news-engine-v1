package com.example.globalnewsenginev1.articles;

import com.example.globalnewsenginev1.gdelt.GdeltGkg;
import com.example.globalnewsenginev1.gdelt.GdeltGkgRecord;
import com.example.globalnewsenginev1.gdelt.GdeltGkgRepository;
import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleProjectionJobTests {

    @Test
    void createsArticleFromUnprojectedGkgRow() {
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        GdeltGkg gkg = gkg("20260509133000-1", "https://Example.test/news/#section", LocalDateTime.of(2026, 5, 9, 13, 30));

        when(gkgRepository.findUnprojectedArticleRows(any(Pageable.class))).thenReturn(List.of(gkg));
        when(articleRepository.findByCanonicalUrl("https://example.test/news")).thenReturn(Optional.empty());
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleProjectionJob job = new ArticleProjectionJob(gkgRepository, articleRepository, 1000);

        int projected = job.run();

        assertThat(projected).isEqualTo(1);
        assertThat(gkg.getArticleProjectedAt()).isNotNull();
        verify(articleRepository).save(any(Article.class));
    }

    @Test
    void deduplicatesByCanonicalUrlAndUpdatesLastSeenAt() {
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        GdeltGkg firstGkg = gkg("20260509133000-1", "https://example.test/news", LocalDateTime.of(2026, 5, 9, 13, 30));
        GdeltGkg laterGkg = gkg("20260509134500-1", "https://EXAMPLE.test/news/", LocalDateTime.of(2026, 5, 9, 13, 45));
        Article existing = new Article("https://example.test/news", firstGkg);

        when(gkgRepository.findUnprojectedArticleRows(any(Pageable.class))).thenReturn(List.of(laterGkg));
        when(articleRepository.findByCanonicalUrl("https://example.test/news")).thenReturn(Optional.of(existing));

        ArticleProjectionJob job = new ArticleProjectionJob(gkgRepository, articleRepository, 1000);

        int projected = job.run();

        assertThat(projected).isEqualTo(1);
        assertThat(existing.getFirstSeenAt()).isEqualTo(LocalDateTime.of(2026, 5, 9, 13, 30));
        assertThat(existing.getLastSeenAt()).isEqualTo(LocalDateTime.of(2026, 5, 9, 13, 45));
        assertThat(laterGkg.getArticleProjectedAt()).isNotNull();
        verify(articleRepository).save(existing);
    }

    @Test
    void marksRowsWithInvalidArticleUrlsAsProjectedWithoutSavingArticle() {
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        ArticleRepository articleRepository = mock(ArticleRepository.class);
        GdeltGkg gkg = gkg("20260509133000-1", "not-a-url", LocalDateTime.of(2026, 5, 9, 13, 30));

        when(gkgRepository.findUnprojectedArticleRows(any(Pageable.class))).thenReturn(List.of(gkg));

        ArticleProjectionJob job = new ArticleProjectionJob(gkgRepository, articleRepository, 1000);

        int projected = job.run();

        assertThat(projected).isZero();
        assertThat(gkg.getArticleProjectedAt()).isNotNull();
        verify(articleRepository, never()).save(any(Article.class));
    }

    private GdeltGkg gkg(String recordId, String documentIdentifier, LocalDateTime date) {
        SourceBatch batch = new SourceBatch("GDELT", "20260509133000");
        batch.putFile("GKG", "http://example.test/gkg.zip", 1, "abc");
        RawSourceFile file = batch.findFile("GKG").orElseThrow();
        StagingRow row = new StagingRow(batch, file, 1, "raw");
        return new GdeltGkg(row, new GdeltGkgRecord(
                recordId,
                date,
                1,
                "example.test",
                documentIdentifier,
                "AFFECT#5#crisis#1",
                "AFFECT#5#crisis#1",
                "WB_678_DIGITAL_GOVERNMENT;TAX_FNCACT_PRESIDENT",
                "WB_678_DIGITAL_GOVERNMENT,123;TAX_FNCACT_PRESIDENT,456",
                "4#Berlin#GM#GM16#52.5167#13.3833#-1746443",
                "4#Berlin#GM#GM16#52.5167#13.3833#-1746443",
                "Jane Doe;John Smith",
                "Jane Doe,120;John Smith,240",
                "Example Org",
                "Example Org,360",
                new BigDecimal("-2.5"),
                new BigDecimal("1.0"),
                new BigDecimal("3.5"),
                new BigDecimal("4.5"),
                new BigDecimal("10.0"),
                new BigDecimal("0.5"),
                new BigDecimal("850"),
                "20260509",
                "wc:850,c1.1:2.0",
                "https://example.test/image.jpg",
                "https://example.test/related.jpg",
                "https://example.test/social.jpg",
                "https://example.test/video",
                "120|quote",
                "Berlin,12",
                "5,protest",
                "srclc:eng",
                "<PAGE_TITLE>Example</PAGE_TITLE>"
        ));
    }
}
