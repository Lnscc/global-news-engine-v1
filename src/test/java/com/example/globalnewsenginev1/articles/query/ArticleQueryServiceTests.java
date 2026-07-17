package com.example.globalnewsenginev1.articles.query;

import com.example.globalnewsenginev1.articles.normalization.GkgLocation;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.support.SqlArrayValue;

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
        jdbcTemplate.execute("""
                CREATE TABLE gdelt_events (
                    id BIGINT PRIMARY KEY, article_id BIGINT REFERENCES articles(id),
                    source_timestamp TIMESTAMP WITH TIME ZONE NOT NULL, global_event_id BIGINT NOT NULL,
                    event_code VARCHAR(32), avg_tone DOUBLE PRECISION)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE gdelt_mentions (
                    id BIGINT PRIMARY KEY, article_id BIGINT REFERENCES articles(id),
                    source_timestamp TIMESTAMP WITH TIME ZONE NOT NULL, global_event_id BIGINT NOT NULL,
                    mention_doc_tone DOUBLE PRECISION)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE gdelt_gkg (
                    id BIGINT PRIMARY KEY, article_id BIGINT REFERENCES articles(id),
                    source_timestamp TIMESTAMP WITH TIME ZONE NOT NULL, document_identifier TEXT,
                    page_title VARCHAR(1000), page_precise_pub_timestamp TIMESTAMP WITH TIME ZONE,
                    main_image_url VARCHAR(2048), main_image_source VARCHAR(32),
                    themes TEXT ARRAY NOT NULL,
                    persons TEXT ARRAY NOT NULL, organizations TEXT ARRAY NOT NULL,
                    locations JSON NOT NULL, tone_value DOUBLE PRECISION,
                    tone_positive_score DOUBLE PRECISION, tone_negative_score DOUBLE PRECISION,
                    tone_polarity DOUBLE PRECISION, tone_activity_reference_density DOUBLE PRECISION,
                    tone_self_group_reference_density DOUBLE PRECISION, tone_word_count INTEGER,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL)
                """);
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
        long partialMatch = insertArticle(
                "https://gamma.example/partial", "gamma.example", "2026-07-04T10:00:00Z");
        insertGkgRecord(matching, 1, "2026-07-02T10:00:00Z", "Climate NEWS today", "CLIMATE;ENERGY", null);
        insertGkgRecord(matching, 2, "2026-07-02T10:01:00Z", null, "CLIMATE", null);
        insertGkgRecord(partialMatch, 4, "2026-07-04T10:00:00Z", null, "CLIMATE_CHANGE", null);
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
                .total()).isEqualTo(2);
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
        insertGkgRecord(titledId, 1, "2026-07-02T10:01:00Z", "A GKG headline", null, null,
                "2026-07-02T09:45:00Z");
        jdbcTemplate.update("UPDATE gdelt_gkg SET main_image_url = ?, main_image_source = ? WHERE id = 1",
                "https://cdn.example.org/first.jpg", "GKG_SHARING_IMAGE");
        insertGkgRecord(titledId, 2, "2026-07-02T10:02:00Z", null, null, null,
                "2026-07-02T08:00:00Z");
        jdbcTemplate.update("UPDATE gdelt_gkg SET main_image_url = ?, main_image_source = ? WHERE id = 2",
                "https://cdn.example.org/later.jpg", "GKG_SHARING_IMAGE");

        assertThat(queryService.latestArticles(0, 20).articles())
                .containsExactly(
                        new ArticleSummary(titledId, "https://example.org/titled", "example.org",
                                Instant.parse("2026-07-02T10:00:00Z"), "A GKG headline", "GKG",
                                Instant.parse("2026-07-02T09:45:00Z"),
                                "GKG_PAGE_PRECISE_PUB_TIMESTAMP",
                                "https://cdn.example.org/first.jpg", "GKG_SHARING_IMAGE"),
                        new ArticleSummary(untitledId, "https://example.org/untitled", "example.org",
                                Instant.parse("2026-07-01T10:00:00Z"), null, null, null, null, null, null));

        ArticleDetail titled = queryService.articleDetail(titledId).orElseThrow();
        assertThat(titled.title()).isEqualTo("A GKG headline");
        assertThat(titled.titleSource()).isEqualTo("GKG");
        assertThat(titled.publishedAt()).isEqualTo(Instant.parse("2026-07-02T09:45:00Z"));
        assertThat(titled.publishedAtSource()).isEqualTo("GKG_PAGE_PRECISE_PUB_TIMESTAMP");
        assertThat(titled.mainImageUrl()).isEqualTo("https://cdn.example.org/first.jpg");
        assertThat(titled.mainImageSource()).isEqualTo("GKG_SHARING_IMAGE");
        ArticleDetail untitled = queryService.articleDetail(untitledId).orElseThrow();
        assertThat(untitled.title()).isNull();
        assertThat(untitled.titleSource()).isNull();
        assertThat(untitled.publishedAt()).isNull();
        assertThat(untitled.publishedAtSource()).isNull();
        assertThat(untitled.mainImageUrl()).isNull();
        assertThat(untitled.mainImageSource()).isNull();
    }

    @Test
    void returnsArticleDetailWithDifferentSignalTypesInChronologicalOrder() {
        long articleId = insertArticle("https://example.org/article", "example.org", "2026-07-01T10:00:00Z");
        insertGkgRecord(articleId, 3, "2026-07-01T10:15:00Z", null, "THEME_B;THEME_A", -2.5);
        jdbcTemplate.update("""
                UPDATE gdelt_gkg
                SET persons = ?, organizations = ?,
                    locations = CAST(? AS JSON), tone_positive_score = 3.0,
                    tone_negative_score = 5.5, tone_polarity = 8.5,
                    tone_activity_reference_density = 1.25,
                    tone_self_group_reference_density = 0.75, tone_word_count = 420
                WHERE id = 3
                """, new SqlArrayValue("TEXT", "Jane Doe"), new SqlArrayValue("TEXT", "Example Org"),
                "[{\"type\":4,\"name\":\"Exeter\",\"countryCode\":\"UK\",\"adm1Code\":\"UKD4\","
                        + "\"latitude\":50.7,\"longitude\":-3.53333,\"featureId\":\"-2595805\"}]");
        insertSignal(articleId, "EVENTS", 1, "2026-07-01T10:00:00Z", 123L,
                null, -1.0);
        insertSignal(articleId, "MENTIONS", 2, "2026-07-01T10:05:00Z", 123L,
                null, -1.5);

        ArticleDetail detail = queryService.articleDetail(articleId).orElseThrow();

        assertThat(detail.id()).isEqualTo(articleId);
        assertThat(detail.signals()).extracting(ArticleSignal::signalType)
                .containsExactly("EVENTS", "MENTIONS", "GKG");
        assertThat(detail.signals()).allSatisfy(signal -> assertThat(signal.id()).isEqualTo(signal.sourceId()));
        assertThat(detail.signals().getFirst().globalEventId()).isEqualTo(123L);
        assertThat(detail.signals().getLast().themes()).containsExactly("THEME_B", "THEME_A");
        assertThat(detail.signals().getLast().persons()).containsExactly("Jane Doe");
        assertThat(detail.signals().getLast().organizations()).containsExactly("Example Org");
        assertThat(detail.signals().getLast().locations()).containsExactly(
                new GkgLocation(4, "Exeter", "UK", "UKD4", 50.7, -3.53333, "-2595805"));
        assertThat(detail.signals().getLast().toneWordCount()).isEqualTo(420);
    }

    @Test
    void aggregatesTopDomainsAndIndividualThemes() {
        long first = insertArticle("https://alpha.example/one", "alpha.example", "2026-07-01T10:00:00Z");
        long second = insertArticle("https://alpha.example/two", "alpha.example", "2026-07-01T11:00:00Z");
        long third = insertArticle("https://beta.example/one", "beta.example", "2026-07-01T12:00:00Z");
        insertGkgRecord(first, 1, "2026-07-01T10:00:00Z", null, "B;A", null);
        insertGkgRecord(second, 2, "2026-07-01T11:00:00Z", null, "A;C", null);
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
        if ("EVENTS".equals(signalType)) {
            jdbcTemplate.update("""
                    INSERT INTO gdelt_events
                        (id, article_id, source_timestamp, global_event_id, event_code, avg_tone)
                    VALUES (?, ?, ?, ?, '042', ?)
                    """, sourceId, articleId, utc(timestamp), globalEventId, tone);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO gdelt_mentions
                        (id, article_id, source_timestamp, global_event_id, mention_doc_tone)
                    VALUES (?, ?, ?, ?, ?)
                    """, sourceId, articleId, utc(timestamp), globalEventId, tone);
        }
    }

    private void insertGkgRecord(long articleId, long sourceId, String sourceTimestamp,
                                 String pageTitle, String themes, Double tone) {
        insertGkgRecord(articleId, sourceId, sourceTimestamp, pageTitle, themes, tone, null);
    }

    private void insertGkgRecord(long articleId, long sourceId, String sourceTimestamp,
                                 String pageTitle, String themes, Double tone, String publishedAt) {
        Instant timestamp = Instant.parse(sourceTimestamp);
        jdbcTemplate.update("""
                INSERT INTO gdelt_gkg
                    (id, article_id, source_timestamp, document_identifier, page_title,
                     page_precise_pub_timestamp, themes, persons, organizations, locations,
                     tone_value, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                """, sourceId, articleId, utc(timestamp), "https://example.org/" + sourceId,
                pageTitle, publishedAt == null ? null : utc(Instant.parse(publishedAt)),
                new SqlArrayValue("TEXT", normalizedThemes(themes).toArray()),
                new SqlArrayValue("TEXT"), new SqlArrayValue("TEXT"), "[]", tone, utc(timestamp));
    }

    private List<String> normalizedThemes(String themes) {
        if (themes == null) return List.of();
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String theme : themes.split(";", -1)) {
            if (!theme.trim().isEmpty()) normalized.add(theme.trim());
        }
        return List.copyOf(normalized);
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
