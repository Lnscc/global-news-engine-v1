package com.example.globalnewsenginev1.stories.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StoryQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private static final String ARTICLE_COLUMNS = """
            article.id AS article_id,
            article.canonical_url,
            article.domain,
            article.first_seen_at,
            (SELECT g.page_title FROM gdelt_gkg g
             WHERE g.article_id = article.id AND g.page_title IS NOT NULL AND TRIM(g.page_title) <> ''
             ORDER BY g.source_timestamp, g.id LIMIT 1) AS title,
            (SELECT g.page_precise_pub_timestamp FROM gdelt_gkg g
             WHERE g.article_id = article.id AND g.page_precise_pub_timestamp IS NOT NULL
             ORDER BY g.source_timestamp, g.id LIMIT 1) AS published_at,
            (SELECT g.main_image_url FROM gdelt_gkg g
             WHERE g.article_id = article.id AND g.main_image_url IS NOT NULL
             ORDER BY g.source_timestamp, g.id LIMIT 1) AS main_image_url
            """;

    private static final String VISIBLE_STORY = """
            FROM stories story
            JOIN story_clustering_versions version
              ON version.id = story.clustering_version_id AND version.status = 'ACTIVE'
            JOIN articles article ON article.url_hash = story.representative_article_ref
            WHERE story.state <> 'SUPERSEDED'
            """;

    private static final String STORY_SELECT = """
            SELECT story.public_id, story.state, story.effective_from, story.effective_to,
            """ + ARTICLE_COLUMNS + """
            , (SELECT COUNT(*) FROM story_memberships member
               WHERE member.story_id = story.id
                 AND member.clustering_version_id = story.clustering_version_id
                 AND member.current_marker = 1) AS member_count
            """ + VISIBLE_STORY;

    private static final String MEMBER_SELECT = """
            SELECT
            """ + ARTICLE_COLUMNS + """
            , member.assignment_reason
            FROM story_memberships member
            JOIN stories story
              ON story.id = member.story_id
             AND story.clustering_version_id = member.clustering_version_id
            JOIN story_clustering_versions version
              ON version.id = story.clustering_version_id AND version.status = 'ACTIVE'
            JOIN articles article ON article.id = member.article_id
            WHERE story.public_id = ? AND story.state <> 'SUPERSEDED'
              AND member.current_marker = 1
            ORDER BY article.first_seen_at ASC, article.id ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public StoryQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StoryPage stories(int offset, int limit) {
        validatePagination(offset, limit);
        List<StorySummary> stories = jdbcTemplate.query(STORY_SELECT + """
                ORDER BY story.effective_to DESC, story.public_id ASC
                LIMIT ? OFFSET ?
                """, (resultSet, rowNum) -> summary(resultSet), limit, offset);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + VISIBLE_STORY, Long.class);
        return new StoryPage(stories, offset, limit, total);
    }

    public Optional<StoryDetail> story(UUID id) {
        List<StorySummary> stories = jdbcTemplate.query(STORY_SELECT + """
                  AND story.public_id = ?
                """, (resultSet, rowNum) -> summary(resultSet), id);
        if (stories.isEmpty()) {
            return Optional.empty();
        }
        StorySummary story = stories.getFirst();
        List<StoryMember> members = jdbcTemplate.query(MEMBER_SELECT, (resultSet, rowNum) -> new StoryMember(
                        article(resultSet), resultSet.getString("assignment_reason")), id);
        return Optional.of(new StoryDetail(
                story.id(), story.state(), story.effectiveFrom(), story.effectiveTo(),
                story.representativeArticle(), members));
    }

    private StorySummary summary(ResultSet resultSet) throws SQLException {
        return new StorySummary(
                resultSet.getObject("public_id", UUID.class),
                resultSet.getString("state"),
                instant(resultSet, "effective_from"),
                instant(resultSet, "effective_to"),
                article(resultSet),
                resultSet.getLong("member_count"));
    }

    private StoryArticle article(ResultSet resultSet) throws SQLException {
        return new StoryArticle(
                resultSet.getLong("article_id"),
                resultSet.getString("canonical_url"),
                resultSet.getString("domain"),
                instant(resultSet, "first_seen_at"),
                resultSet.getString("title"),
                nullableInstant(resultSet, "published_at"),
                resultSet.getString("main_image_url"));
    }

    private java.time.Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private java.time.Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private void validatePagination(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
