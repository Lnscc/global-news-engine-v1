package com.example.globalnewsenginev1.articles.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ArticleQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_AGGREGATE_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public ArticleQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ArticlePage latestArticles(int offset, int limit) {
        validatePagination(offset, limit);
        List<ArticleSummary> articles = jdbcTemplate.query("""
                SELECT id, canonical_url, domain, first_seen_at
                FROM articles
                ORDER BY first_seen_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (resultSet, rowNum) -> new ArticleSummary(
                resultSet.getLong("id"),
                resultSet.getString("canonical_url"),
                resultSet.getString("domain"),
                instant(resultSet, "first_seen_at")), limit, offset);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM articles", Long.class);
        return new ArticlePage(articles, offset, limit, total);
    }

    public Optional<ArticleDetail> articleDetail(long articleId) {
        List<ArticleDetailRow> articles = jdbcTemplate.query("""
                SELECT id, canonical_url, domain, first_seen_at, created_at, updated_at
                FROM articles
                WHERE id = ?
                """, (resultSet, rowNum) -> new ArticleDetailRow(
                resultSet.getLong("id"),
                resultSet.getString("canonical_url"),
                resultSet.getString("domain"),
                instant(resultSet, "first_seen_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at")), articleId);
        return articles.stream().findFirst().map(article -> new ArticleDetail(
                article.id(), article.canonicalUrl(), article.domain(), article.firstSeenAt(),
                article.createdAt(), article.updatedAt(), signalsFor(articleId)));
    }

    public List<NamedCount> topDomains(int limit) {
        validateAggregateLimit(limit);
        return jdbcTemplate.query("""
                SELECT domain, COUNT(*) AS article_count
                FROM articles
                GROUP BY domain
                ORDER BY article_count DESC, domain ASC
                LIMIT ?
                """, (resultSet, rowNum) -> new NamedCount(
                resultSet.getString("domain"), resultSet.getLong("article_count")), limit);
    }

    public List<NamedCount> topThemes(int limit) {
        validateAggregateLimit(limit);
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT themes
                FROM article_signals
                WHERE themes IS NOT NULL AND themes <> ''
                """, (RowCallbackHandler) resultSet -> Arrays.stream(resultSet.getString("themes").split(";"))
                .map(String::trim)
                .filter(theme -> !theme.isEmpty())
                .forEach(theme -> counts.merge(theme, 1L, Long::sum)));
        return counts.entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(NamedCount::count).reversed()
                        .thenComparing(NamedCount::name))
                .limit(limit)
                .toList();
    }

    private List<ArticleSignal> signalsFor(long articleId) {
        return jdbcTemplate.query("""
                SELECT id, signal_type, source_id, source_timestamp, global_event_id, event_code,
                       themes, persons, organizations, locations, tone_value, tone_raw
                FROM article_signals
                WHERE article_id = ?
                ORDER BY source_timestamp ASC, id ASC
                """, (resultSet, rowNum) -> new ArticleSignal(
                resultSet.getLong("id"),
                resultSet.getString("signal_type"),
                resultSet.getLong("source_id"),
                instant(resultSet, "source_timestamp"),
                nullableLong(resultSet, "global_event_id"),
                resultSet.getString("event_code"),
                resultSet.getString("themes"),
                resultSet.getString("persons"),
                resultSet.getString("organizations"),
                resultSet.getString("locations"),
                nullableDouble(resultSet, "tone_value"),
                resultSet.getString("tone_raw")), articleId);
    }

    private void validatePagination(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private void validateAggregateLimit(int limit) {
        if (limit < 1 || limit > MAX_AGGREGATE_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_AGGREGATE_LIMIT);
        }
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private record ArticleDetailRow(
            long id,
            String canonicalUrl,
            String domain,
            java.time.Instant firstSeenAt,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
    }
}
