package com.example.globalnewsenginev1.articles.api;

import com.example.globalnewsenginev1.articles.query.ArticleDetail;
import com.example.globalnewsenginev1.articles.query.ArticlePage;
import com.example.globalnewsenginev1.articles.query.ArticleQueryService;
import com.example.globalnewsenginev1.articles.query.ArticleSignal;
import com.example.globalnewsenginev1.articles.query.ArticleSummary;
import com.example.globalnewsenginev1.articles.query.NamedCount;
import com.example.globalnewsenginev1.articles.health.ArticleExtractionHealth;
import com.example.globalnewsenginev1.articles.health.ArticleExtractionHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/articles")
public class ArticleController {

    private final ArticleQueryService articleQueryService;
    private final ArticleExtractionHealthService extractionHealthService;

    public ArticleController(
            ArticleQueryService articleQueryService,
            ArticleExtractionHealthService extractionHealthService
    ) {
        this.articleQueryService = articleQueryService;
        this.extractionHealthService = extractionHealthService;
    }

    @GetMapping
    public ArticlePageResponse latestArticles(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ArticlePageResponse.from(articleQueryService.latestArticles(offset, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleDetailResponse> articleDetail(@PathVariable long id) {
        return articleQueryService.articleDetail(id)
                .map(ArticleDetailResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/domains/top")
    public List<NamedCountResponse> topDomains(@RequestParam(defaultValue = "10") int limit) {
        return articleQueryService.topDomains(limit).stream().map(NamedCountResponse::from).toList();
    }

    @GetMapping("/themes/top")
    public List<NamedCountResponse> topThemes(@RequestParam(defaultValue = "10") int limit) {
        return articleQueryService.topThemes(limit).stream().map(NamedCountResponse::from).toList();
    }

    @GetMapping("/extraction/health")
    public ArticleExtractionHealth extractionHealth() {
        return extractionHealthService.health();
    }

    public record ArticlePageResponse(List<ArticleSummaryResponse> articles, int offset, int limit, long total) {
        static ArticlePageResponse from(ArticlePage page) {
            return new ArticlePageResponse(
                    page.articles().stream().map(ArticleSummaryResponse::from).toList(),
                    page.offset(), page.limit(), page.total());
        }
    }

    public record ArticleSummaryResponse(long id, String canonicalUrl, String domain, Instant firstSeenAt) {
        static ArticleSummaryResponse from(ArticleSummary article) {
            return new ArticleSummaryResponse(
                    article.id(), article.canonicalUrl(), article.domain(), article.firstSeenAt());
        }
    }

    public record ArticleDetailResponse(
            long id,
            String canonicalUrl,
            String domain,
            Instant firstSeenAt,
            Instant createdAt,
            Instant updatedAt,
            List<ArticleSignalResponse> signals
    ) {
        static ArticleDetailResponse from(ArticleDetail article) {
            return new ArticleDetailResponse(
                    article.id(), article.canonicalUrl(), article.domain(), article.firstSeenAt(),
                    article.createdAt(), article.updatedAt(),
                    article.signals().stream().map(ArticleSignalResponse::from).toList());
        }
    }

    public record ArticleSignalResponse(
            long id,
            String signalType,
            long sourceId,
            Instant sourceTimestamp,
            Long globalEventId,
            String eventCode,
            String themes,
            String persons,
            String organizations,
            String locations,
            Double toneValue,
            String toneRaw
    ) {
        static ArticleSignalResponse from(ArticleSignal signal) {
            return new ArticleSignalResponse(
                    signal.id(), signal.signalType(), signal.sourceId(), signal.sourceTimestamp(),
                    signal.globalEventId(), signal.eventCode(), signal.themes(), signal.persons(),
                    signal.organizations(), signal.locations(), signal.toneValue(), signal.toneRaw());
        }
    }

    public record NamedCountResponse(String name, long count) {
        static NamedCountResponse from(NamedCount count) {
            return new NamedCountResponse(count.name(), count.count());
        }
    }
}
