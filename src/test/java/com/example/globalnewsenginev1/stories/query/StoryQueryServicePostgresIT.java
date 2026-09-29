package com.example.globalnewsenginev1.stories.query;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StoryQueryServicePostgresIT {

    private DataSource adminDataSource;
    private JdbcTemplate jdbc;
    private String schema;

    @BeforeEach
    void setUp() {
        adminDataSource = dataSource(null);
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "PostgreSQL is not available for story API integration tests");
        schema = "story_api_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schema);
        jdbc = new JdbcTemplate(dataSource(schema));
        jdbc.execute("CREATE TABLE story_clustering_versions (id BIGINT PRIMARY KEY, status VARCHAR(16))");
        jdbc.execute("""
                CREATE TABLE articles (id BIGINT PRIMARY KEY, canonical_url TEXT, url_hash VARCHAR(64),
                    domain TEXT, first_seen_at TIMESTAMP WITH TIME ZONE)
                """);
        jdbc.execute("""
                CREATE TABLE gdelt_gkg (id BIGINT PRIMARY KEY, article_id BIGINT,
                    source_timestamp TIMESTAMP WITH TIME ZONE, page_title TEXT,
                    page_precise_pub_timestamp TIMESTAMP WITH TIME ZONE, main_image_url TEXT)
                """);
        jdbc.execute("""
                CREATE TABLE stories (id UUID PRIMARY KEY, public_id UUID, clustering_version_id BIGINT,
                    representative_article_ref VARCHAR(64), state VARCHAR(16),
                    effective_from TIMESTAMP WITH TIME ZONE, effective_to TIMESTAMP WITH TIME ZONE)
                """);
        jdbc.execute("""
                CREATE TABLE story_memberships (story_id UUID, clustering_version_id BIGINT,
                    article_id BIGINT, current_marker SMALLINT, assignment_reason TEXT)
                """);
    }

    @AfterEach
    void tearDown() {
        if (schema != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void readsTheUpdatedActiveVersionAndRejectsReplacedIds() {
        UUID reused = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID replaced = UUID.fromString("10000000-0000-0000-0000-000000000002");
        jdbc.update("INSERT INTO story_clustering_versions VALUES (1, 'RETIRED'), (2, 'ACTIVE'), (3, 'SHADOW')");
        jdbc.update("""
                INSERT INTO articles VALUES
                  (1, 'https://example.org/old', 'old', 'example.org', ?),
                  (2, 'https://example.org/current', 'current', 'example.org', ?)
                """, time("2026-09-01T10:00:00Z"), time("2026-09-02T10:00:00Z"));
        jdbc.update("""
                INSERT INTO stories VALUES
                  (?, ?, 1, 'old', 'ACTIVE', ?, ?),
                  (?, ?, 2, 'current', 'ACTIVE', ?, ?),
                  (?, ?, 2, 'old', 'SUPERSEDED', ?, ?),
                  (?, ?, 3, 'old', 'ACTIVE', ?, ?)
                """,
                UUID.randomUUID(), reused, time("2026-09-01T10:00:00Z"), time("2026-09-01T10:00:00Z"),
                UUID.randomUUID(), reused, time("2026-09-02T10:00:00Z"), time("2026-09-02T10:00:00Z"),
                UUID.randomUUID(), replaced, time("2026-09-01T10:00:00Z"), time("2026-09-01T10:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(), time("2026-09-03T10:00:00Z"), time("2026-09-03T10:00:00Z"));
        UUID internal = jdbc.queryForObject(
                "SELECT id FROM stories WHERE clustering_version_id = 2 AND public_id = ?", UUID.class, reused);
        jdbc.update("INSERT INTO story_memberships VALUES (?, 2, 1, NULL, 'OLD')", internal);
        jdbc.update("INSERT INTO story_memberships VALUES (?, 2, 2, 1, 'MATCHED_COMPONENT')", internal);

        StoryQueryService service = new StoryQueryService(jdbc);

        assertThat(service.stories(0, 20).stories()).extracting(StorySummary::id).containsExactly(reused);
        StoryDetail detail = service.story(reused).orElseThrow();
        assertThat(detail.representativeArticle().canonicalUrl()).endsWith("/current");
        assertThat(detail.members()).extracting(member -> member.article().id()).containsExactly(2L);
        assertThat(service.story(replaced)).isEmpty();
    }

    private OffsetDateTime time(String value) {
        return OffsetDateTime.parse(value);
    }

    private DataSource dataSource(String currentSchema) {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        String url = System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne");
        if (currentSchema != null) {
            url += (url.contains("?") ? "&" : "?") + "currentSchema=" + currentSchema;
        }
        postgres.setUrl(url);
        postgres.setUser(System.getProperty("it.postgres.username", "gne"));
        postgres.setPassword(System.getProperty("it.postgres.password", "gne"));
        return postgres;
    }

    private boolean canConnect(DataSource candidate) {
        try (var ignored = candidate.getConnection()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }
}
