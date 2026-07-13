package com.example.globalnewsenginev1.articles.api;

import com.example.globalnewsenginev1.articles.query.ArticleDetail;
import com.example.globalnewsenginev1.articles.query.ArticlePage;
import com.example.globalnewsenginev1.articles.query.ArticleQueryService;
import com.example.globalnewsenginev1.articles.query.ArticleSignal;
import com.example.globalnewsenginev1.articles.query.ArticleSummary;
import com.example.globalnewsenginev1.articles.query.ArticleSearchCriteria;
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
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/articles")
public class ArticleController {

    private static final Set<String> ARTICLE_LIST_PARAMETERS = Set.of(
            "q", "domain", "firstSeenFrom", "firstSeenTo", "theme", "signalType",
            "direction", "offset", "limit");

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
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String firstSeenFrom,
            @RequestParam(required = false) String firstSeenTo,
            @RequestParam(required = false) String theme,
            @RequestParam(required = false) String signalType,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam Map<String, String> parameters
    ) {
        parameters.keySet().stream()
                .filter(parameter -> !ARTICLE_LIST_PARAMETERS.contains(parameter))
                .findFirst()
                .ifPresent(parameter -> {
                    throw new IllegalArgumentException("unknown parameter: " + parameter);
                });
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                q, domain, parseInstant("firstSeenFrom", firstSeenFrom),
                parseInstant("firstSeenTo", firstSeenTo), theme, signalType, direction);
        return ArticlePageResponse.from(articleQueryService.searchArticles(criteria, offset, limit));
    }

    private Instant parseInstant(String parameter, String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(parameter + " must be an ISO-8601 instant");
        }
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

    public record ArticleSummaryResponse(
            long id,
            String canonicalUrl,
            String domain,
            Instant firstSeenAt,
            String title,
            String titleSource
    ) {
        static ArticleSummaryResponse from(ArticleSummary article) {
            return new ArticleSummaryResponse(
                    article.id(), article.canonicalUrl(), article.domain(), article.firstSeenAt(),
                    article.title(), article.titleSource());
        }
    }

    public record ArticleDetailResponse(
            long id,
            String canonicalUrl,
            String domain,
            Instant firstSeenAt,
            String title,
            String titleSource,
            Instant createdAt,
            Instant updatedAt,
            List<ArticleSignalResponse> signals
    ) {
        static ArticleDetailResponse from(ArticleDetail article) {
            return new ArticleDetailResponse(
                    article.id(), article.canonicalUrl(), article.domain(), article.firstSeenAt(),
                    article.title(), article.titleSource(),
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
            List<String> themes,
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
