package com.example.globalnewsenginev1.articles.query;

import com.example.globalnewsenginev1.articles.normalization.GkgLocation;
import com.example.globalnewsenginev1.articles.normalization.GkgValueNormalizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ArticleQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_AGGREGATE_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GkgValueNormalizer gkgValueNormalizer = new GkgValueNormalizer();

    public ArticleQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public ArticlePage latestArticles(int offset, int limit) {
        return searchArticles(ArticleSearchCriteria.defaults(), offset, limit);
    }

    public ArticlePage searchArticles(ArticleSearchCriteria criteria, int offset, int limit) {
        validatePagination(offset, limit);
        validateCriteria(criteria);

        List<Object> parameters = new ArrayList<>();
        String whereClause = whereClause(criteria, parameters);
        String direction = criteria.direction().toUpperCase(java.util.Locale.ROOT);
        String query = """
                SELECT articles.id, canonical_url, domain, first_seen_at,
                       (SELECT g.page_title FROM gdelt_gkg_records g
                        WHERE g.article_id = articles.id AND g.page_title IS NOT NULL AND TRIM(g.page_title) <> ''
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS title,
                       (SELECT g.page_precise_pub_timestamp FROM gdelt_gkg_records g
                        WHERE g.article_id = articles.id AND g.page_precise_pub_timestamp IS NOT NULL
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS published_at,
                       (SELECT g.main_image_url FROM gdelt_gkg_records g
                        WHERE g.article_id = articles.id AND g.main_image_url IS NOT NULL
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS main_image_url
                FROM articles
                """ + whereClause + " ORDER BY first_seen_at " + direction + ", id " + direction + " " + """
                LIMIT ? OFFSET ?
                """;
        parameters.add(limit);
        parameters.add(offset);
        List<ArticleSummary> articles = jdbcTemplate.query(query, (resultSet, rowNum) -> new ArticleSummary(
                resultSet.getLong("id"),
                resultSet.getString("canonical_url"),
                resultSet.getString("domain"),
                instant(resultSet, "first_seen_at"),
                resultSet.getString("title"),
                resultSet.getString("title") == null ? null : "GKG",
                nullableInstant(resultSet, "published_at"),
                resultSet.getTimestamp("published_at") == null
                        ? null : "GKG_PAGE_PRECISE_PUB_TIMESTAMP",
                resultSet.getString("main_image_url"),
                resultSet.getString("main_image_url") == null ? null : "GKG_SHARING_IMAGE"), parameters.toArray());
        parameters.remove(parameters.size() - 1);
        parameters.remove(parameters.size() - 1);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM articles " + whereClause, Long.class, parameters.toArray());
        return new ArticlePage(articles, offset, limit, total);
    }

    private String whereClause(ArticleSearchCriteria criteria, List<Object> parameters) {
        List<String> predicates = new ArrayList<>();
        if (hasText(criteria.query())) {
            predicates.add("EXISTS (SELECT 1 FROM gdelt_gkg_records g WHERE g.article_id = articles.id "
                    + "AND g.page_title IS NOT NULL AND TRIM(g.page_title) <> '' "
                    + "AND LOWER(g.page_title) LIKE ? ESCAPE '!')");
            parameters.add("%" + escapeLike(criteria.query().toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        if (hasText(criteria.domain())) {
            predicates.add("domain = ?");
            parameters.add(criteria.domain());
        }
        if (criteria.firstSeenFrom() != null) {
            predicates.add("first_seen_at >= ?");
            parameters.add(Timestamp.from(criteria.firstSeenFrom()));
        }
        if (criteria.firstSeenTo() != null) {
            predicates.add("first_seen_at < ?");
            parameters.add(Timestamp.from(criteria.firstSeenTo()));
        }
        if (hasText(criteria.theme())) {
            predicates.add("EXISTS (SELECT 1 FROM gdelt_gkg_records g "
                    + "WHERE g.article_id = articles.id AND ? = ANY(g.themes))");
            parameters.add(criteria.theme());
        }
        if (criteria.signalType() != null) {
            if ("GKG".equals(criteria.signalType())) {
                predicates.add("EXISTS (SELECT 1 FROM gdelt_gkg_records g WHERE g.article_id = articles.id)");
            } else {
                predicates.add("EXISTS (SELECT 1 FROM article_signals s WHERE s.article_id = articles.id "
                        + "AND s.signal_type = ?)");
                parameters.add(criteria.signalType());
            }
        }
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private void validateCriteria(ArticleSearchCriteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("search criteria must not be null");
        }
        if (!List.of("asc", "desc").contains(criteria.direction())) {
            throw new IllegalArgumentException("direction must be asc or desc");
        }
        if (criteria.signalType() != null
                && !List.of("EVENTS", "MENTIONS", "GKG").contains(criteria.signalType())) {
            throw new IllegalArgumentException("signalType must be EVENTS, MENTIONS or GKG");
        }
        if (criteria.firstSeenFrom() != null && criteria.firstSeenTo() != null
                && !criteria.firstSeenFrom().isBefore(criteria.firstSeenTo())) {
            throw new IllegalArgumentException("firstSeenFrom must be before firstSeenTo");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public Optional<ArticleDetail> articleDetail(long articleId) {
        List<ArticleDetailRow> articles = jdbcTemplate.query("""
                SELECT articles.id, canonical_url, domain, first_seen_at,
                       (SELECT g.page_title FROM gdelt_gkg_records g
                        WHERE g.article_id = articles.id AND g.page_title IS NOT NULL AND TRIM(g.page_title) <> ''
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS title,
                       (SELECT g.page_precise_pub_timestamp FROM gdelt_gkg_records g
                        WHERE g.article_id = articles.id AND g.page_precise_pub_timestamp IS NOT NULL
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS published_at,
                       (SELECT g.main_image_url FROM gdelt_gkg_records g
                        WHERE g.article_id = articles.id AND g.main_image_url IS NOT NULL
                        ORDER BY g.source_timestamp, g.id LIMIT 1) AS main_image_url,
                       created_at, updated_at
                FROM articles
                WHERE articles.id = ?
                """, (resultSet, rowNum) -> new ArticleDetailRow(
                resultSet.getLong("id"),
                resultSet.getString("canonical_url"),
                resultSet.getString("domain"),
                instant(resultSet, "first_seen_at"),
                resultSet.getString("title"),
                resultSet.getString("title") == null ? null : "GKG",
                nullableInstant(resultSet, "published_at"),
                resultSet.getTimestamp("published_at") == null
                        ? null : "GKG_PAGE_PRECISE_PUB_TIMESTAMP",
                resultSet.getString("main_image_url"),
                resultSet.getString("main_image_url") == null ? null : "GKG_SHARING_IMAGE",
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at")), articleId);
        return articles.stream().findFirst().map(article -> new ArticleDetail(
                article.id(), article.canonicalUrl(), article.domain(), article.firstSeenAt(),
                article.title(), article.titleSource(),
                article.publishedAt(), article.publishedAtSource(),
                article.mainImageUrl(), article.mainImageSource(),
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
        jdbcTemplate.query("SELECT themes FROM gdelt_gkg_records", (RowCallbackHandler) resultSet ->
                arrayValues(resultSet, "themes").forEach(theme -> counts.merge(theme, 1L, Long::sum)));
        return counts.entrySet().stream()
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(NamedCount::count).reversed()
                        .thenComparing(NamedCount::name))
                .limit(limit)
                .toList();
    }

    private List<ArticleSignal> signalsFor(long articleId) {
        List<ArticleSignal> signals = new ArrayList<>(jdbcTemplate.query("""
                SELECT id, signal_type, source_id, source_timestamp, global_event_id, event_code,
                       themes, persons, organizations, locations, tone_value, tone_raw
                FROM article_signals
                WHERE article_id = ?
                """, (resultSet, rowNum) -> new ArticleSignal(
                resultSet.getLong("id"),
                resultSet.getString("signal_type"),
                resultSet.getLong("source_id"),
                instant(resultSet, "source_timestamp"),
                nullableLong(resultSet, "global_event_id"),
                resultSet.getString("event_code"),
                normalizeThemes(resultSet.getString("themes")),
                gkgValueNormalizer.normalizeNames(resultSet.getString("persons")),
                gkgValueNormalizer.normalizeNames(resultSet.getString("organizations")),
                gkgValueNormalizer.normalizeLocations(resultSet.getString("locations")).locations(),
                nullableDouble(resultSet, "tone_value"),
                null, null, null, null, null, null), articleId));
        signals.addAll(jdbcTemplate.query("""
                SELECT id, source_id, source_timestamp, themes, persons, organizations, locations,
                       tone_value, tone_positive_score, tone_negative_score, tone_polarity,
                       tone_activity_reference_density, tone_self_group_reference_density, tone_word_count
                FROM gdelt_gkg_records
                WHERE article_id = ?
                """, (resultSet, rowNum) -> new ArticleSignal(
                resultSet.getLong("id"),
                "GKG",
                resultSet.getLong("source_id"),
                instant(resultSet, "source_timestamp"),
                null,
                null,
                arrayValues(resultSet, "themes"),
                arrayValues(resultSet, "persons"),
                arrayValues(resultSet, "organizations"),
                locations(resultSet.getString("locations")),
                nullableDouble(resultSet, "tone_value"),
                nullableDouble(resultSet, "tone_positive_score"),
                nullableDouble(resultSet, "tone_negative_score"),
                nullableDouble(resultSet, "tone_polarity"),
                nullableDouble(resultSet, "tone_activity_reference_density"),
                nullableDouble(resultSet, "tone_self_group_reference_density"),
                nullableInteger(resultSet, "tone_word_count")), articleId));
        return signals.stream()
                .sorted(Comparator.comparing(ArticleSignal::sourceTimestamp).thenComparingLong(ArticleSignal::id))
                .toList();
    }

    private List<String> normalizeThemes(String themes) {
        if (themes == null || themes.isBlank()) return List.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        Arrays.stream(themes.split(";", -1)).map(String::trim)
                .filter(theme -> !theme.isEmpty()).forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private List<String> arrayValues(ResultSet resultSet, String column) throws SQLException {
        java.sql.Array sqlArray = resultSet.getArray(column);
        if (sqlArray == null) return List.of();
        return Arrays.stream((Object[]) sqlArray.getArray()).map(Object::toString).toList();
    }

    private List<GkgLocation> locations(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) node = objectMapper.readTree(node.textValue());
            return objectMapper.convertValue(node, new TypeReference<>() { });
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored GKG locations are not valid JSON", exception);
        }
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

    private java.time.Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        java.sql.Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private record ArticleDetailRow(
            long id,
            String canonicalUrl,
            String domain,
            java.time.Instant firstSeenAt,
            String title,
            String titleSource,
            java.time.Instant publishedAt,
            String publishedAtSource,
            String mainImageUrl,
            String mainImageSource,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
    }
}
