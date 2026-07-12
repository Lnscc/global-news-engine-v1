package com.example.globalnewsenginev1.articles.query;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ArticleQueryServiceTests {

    private JdbcTemplate jdbcTemplate;
    private ArticleQueryService queryService;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:article-queries-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("ALTER TABLE articles ADD COLUMN title VARCHAR(1000)");
        jdbcTemplate.execute("ALTER TABLE articles ADD COLUMN title_source VARCHAR(32)");
        queryService = new ArticleQueryService(jdbcTemplate);
    }

    @Test
    void returnsAnEmptyPageForAnEmptyDatabase() {
        assertThat(queryService.latestArticles(0, 20))
                .isEqualTo(new ArticlePage(List.of(), 0, 20, 0));
        assertThat(queryService.topDomains(10)).isEmpty();
        assertThat(queryService.topThemes(10)).isEmpty();
        assertThat(queryService.articleDetail(42)).isEmpty();
    }

    @Test
    void returnsLatestArticlesWithStablePagination() {
        long firstId = insertArticle("https://alpha.example/old", "alpha.example", "2026-07-01T10:00:00Z");
        long secondId = insertArticle("https://beta.example/new-1", "beta.example", "2026-07-02T10:00:00Z");
        long thirdId = insertArticle("https://beta.example/new-2", "beta.example", "2026-07-02T10:00:00Z");

        ArticlePage firstPage = queryService.latestArticles(0, 2);
        ArticlePage secondPage = queryService.latestArticles(2, 2);

        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.articles()).extracting(ArticleSummary::id)
                .containsExactly(thirdId, secondId);
        assertThat(secondPage.articles()).extracting(ArticleSummary::id)
                .containsExactly(firstId);
    }

    @Test
    void filtersArticlesIndividuallyAndInCombination() {
        long matching = insertArticle("https://alpha.example/match", "alpha.example", "2026-07-02T10:00:00Z");
        long other = insertArticle("https://beta.example/other", "beta.example", "2026-07-03T10:00:00Z");
        jdbcTemplate.update("UPDATE articles SET title = ? WHERE id = ?", "Climate NEWS today", matching);
        jdbcTemplate.update("UPDATE articles SET title = ? WHERE id = ?", "Other headline", other);
        insertSignal(matching, "GKG", 1, "2026-07-02T10:00:00Z", null, "CLIMATE;ENERGY", null);
        insertSignal(matching, "GKG", 2, "2026-07-02T10:01:00Z", null, "CLIMATE", null);
        insertSignal(other, "EVENTS", 3, "2026-07-03T10:00:00Z", 123L, "CLIMATE_CHANGE", null);

        ArticleSearchCriteria combined = new ArticleSearchCriteria(
                "news", "alpha.example", Instant.parse("2026-07-02T10:00:00Z"),
                Instant.parse("2026-07-03T10:00:00Z"), "CLIMATE", "GKG", "desc");

        assertThat(queryService.searchArticles(combined, 0, 20).articles())
                .extracting(ArticleSummary::id).containsExactly(matching);
        assertThat(queryService.searchArticles(
                new ArticleSearchCriteria(null, null, null, null, "CLIMATE", null, "desc"), 0, 20)
                .articles()).extracting(ArticleSummary::id).containsExactly(matching);
        assertThat(queryService.searchArticles(
                new ArticleSearchCriteria(null, null, null, null, null, "GKG", "desc"), 0, 20)
                .total()).isEqualTo(1);
    }

    @Test
    void appliesFilteredTotalPaginationAndAscendingStableOrder() {
        long first = insertArticle("https://example.org/one", "example.org", "2026-07-02T10:00:00Z");
        long second = insertArticle("https://example.org/two", "example.org", "2026-07-02T10:00:00Z");
        insertArticle("https://other.org/three", "other.org", "2026-07-01T10:00:00Z");

        ArticlePage page = queryService.searchArticles(
                new ArticleSearchCriteria(null, "example.org", null, null, null, null, "asc"), 1, 1);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.articles()).extracting(ArticleSummary::id).containsExactly(second);
        assertThat(first).isLessThan(second);
    }

    @Test
    void returnsNullableGkgMetadataInSummaryAndDetail() {
        long titledId = insertArticle("https://example.org/titled", "example.org", "2026-07-02T10:00:00Z");
        long untitledId = insertArticle("https://example.org/untitled", "example.org", "2026-07-01T10:00:00Z");
        jdbcTemplate.update("UPDATE articles SET title = ?, title_source = ? WHERE id = ?",
                "A GKG headline", "GKG", titledId);

        assertThat(queryService.latestArticles(0, 20).articles())
                .containsExactly(
                        new ArticleSummary(titledId, "https://example.org/titled", "example.org",
                                Instant.parse("2026-07-02T10:00:00Z"), "A GKG headline", "GKG"),
                        new ArticleSummary(untitledId, "https://example.org/untitled", "example.org",
                                Instant.parse("2026-07-01T10:00:00Z"), null, null));

        ArticleDetail titled = queryService.articleDetail(titledId).orElseThrow();
        assertThat(titled.title()).isEqualTo("A GKG headline");
        assertThat(titled.titleSource()).isEqualTo("GKG");
        ArticleDetail untitled = queryService.articleDetail(untitledId).orElseThrow();
        assertThat(untitled.title()).isNull();
        assertThat(untitled.titleSource()).isNull();
    }

    @Test
    void returnsArticleDetailWithDifferentSignalTypesInChronologicalOrder() {
        long articleId = insertArticle("https://example.org/article", "example.org", "2026-07-01T10:00:00Z");
        insertSignal(articleId, "GKG", 3, "2026-07-01T10:15:00Z", null,
                "THEME_B;THEME_A", -2.5);
        insertSignal(articleId, "EVENTS", 1, "2026-07-01T10:00:00Z", 123L,
                null, -1.0);
        insertSignal(articleId, "MENTIONS", 2, "2026-07-01T10:05:00Z", 123L,
                null, -1.5);

        ArticleDetail detail = queryService.articleDetail(articleId).orElseThrow();

        assertThat(detail.id()).isEqualTo(articleId);
        assertThat(detail.signals()).extracting(ArticleSignal::signalType)
                .containsExactly("EVENTS", "MENTIONS", "GKG");
        assertThat(detail.signals().getFirst().globalEventId()).isEqualTo(123L);
        assertThat(detail.signals().getLast().themes()).isEqualTo("THEME_B;THEME_A");
    }

    @Test
    void aggregatesTopDomainsAndIndividualThemes() {
        long first = insertArticle("https://alpha.example/one", "alpha.example", "2026-07-01T10:00:00Z");
        long second = insertArticle("https://alpha.example/two", "alpha.example", "2026-07-01T11:00:00Z");
        long third = insertArticle("https://beta.example/one", "beta.example", "2026-07-01T12:00:00Z");
        insertSignal(first, "GKG", 1, "2026-07-01T10:00:00Z", null, "B;A", null);
        insertSignal(second, "GKG", 2, "2026-07-01T11:00:00Z", null, "A;C", null);
        insertSignal(third, "EVENTS", 3, "2026-07-01T12:00:00Z", 123L, null, null);

        assertThat(queryService.topDomains(10)).containsExactly(
                new NamedCount("alpha.example", 2),
                new NamedCount("beta.example", 1));
        assertThat(queryService.topThemes(2)).containsExactly(
                new NamedCount("A", 2),
                new NamedCount("B", 1));
    }

    @Test
    void rejectsInvalidLimitsAndOffsets() {
        assertThatIllegalArgumentException().isThrownBy(() -> queryService.latestArticles(-1, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> queryService.latestArticles(0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> queryService.latestArticles(0, 101));
        assertThatIllegalArgumentException().isThrownBy(() -> queryService.topDomains(0));
        assertThatIllegalArgumentException().isThrownBy(() -> queryService.topThemes(101));
        assertThatIllegalArgumentException().isThrownBy(() -> queryService.searchArticles(
                new ArticleSearchCriteria(null, null, null, null, null, "UNKNOWN", "desc"), 0, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> queryService.searchArticles(
                new ArticleSearchCriteria(null, null, null, null, null, null, "sideways"), 0, 20));
    }

    private long insertArticle(String url, String domain, String firstSeenAt) {
        Instant timestamp = Instant.parse(firstSeenAt);
        jdbcTemplate.update("""
                INSERT INTO articles
                    (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, url, Integer.toHexString(url.hashCode()), domain, utc(timestamp), utc(timestamp), utc(timestamp));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM articles WHERE canonical_url = ?", Long.class, url);
    }

    private void insertSignal(
            long articleId,
            String signalType,
            long sourceId,
            String sourceTimestamp,
            Long globalEventId,
            String themes,
            Double tone
    ) {
        Instant timestamp = Instant.parse(sourceTimestamp);
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, global_event_id, event_code,
                     themes, tone_value, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, articleId, signalType, sourceId, utc(timestamp), globalEventId,
                "EVENTS".equals(signalType) ? "042" : null, themes, tone, utc(timestamp));
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
