package com.example.globalnewsenginev1.stories.query;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StoryQueryServiceTests {

    private JdbcTemplate jdbc;
    private StoryQueryService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:story-queries-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
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
        service = new StoryQueryService(jdbc);
    }

    @Test
    void returnsOnlyCurrentVisibleStoriesWithStablePagination() {
        seedVersion(1, "ACTIVE");
        seedVersion(2, "SHADOW");
        seedArticle(1, "a", "2026-09-01T10:00:00Z");
        seedArticle(2, "b", "2026-09-02T10:00:00Z");
        seedArticle(3, "c", "2026-09-03T10:00:00Z");
        UUID older = seedStory(1, 1, "a", "ACTIVE", "2026-09-01T10:00:00Z");
        UUID newer = seedStory(2, 1, "b", "CLOSED", "2026-09-02T10:00:00Z");
        seedStory(3, 1, "c", "SUPERSEDED", "2026-09-03T10:00:00Z");
        seedStory(4, 2, "c", "ACTIVE", "2026-09-04T10:00:00Z");
        seedMember(2, 1, 2, 1, "NEW_COMPONENT");

        StoryPage first = service.stories(0, 1);
        StoryPage second = service.stories(1, 1);

        assertThat(first.total()).isEqualTo(2);
        assertThat(first.stories()).extracting(StorySummary::id).containsExactly(newer);
        assertThat(first.stories().getFirst().memberCount()).isOne();
        assertThat(second.stories()).extracting(StorySummary::id).containsExactly(older);
    }

    @Test
    void returnsCurrentMembersAndHidesSupersededIds() {
        seedVersion(1, "ACTIVE");
        seedArticle(1, "a", "2026-09-01T10:00:00Z");
        seedArticle(2, "b", "2026-09-01T11:00:00Z");
        UUID visible = seedStory(1, 1, "a", "ACTIVE", "2026-09-01T11:00:00Z");
        UUID replaced = seedStory(2, 1, "b", "SUPERSEDED", "2026-09-01T12:00:00Z");
        seedMember(1, 1, 1, null, "OLD");
        seedMember(1, 1, 2, 1, "MATCHED_COMPONENT");

        StoryDetail detail = service.story(visible).orElseThrow();

        assertThat(detail.members()).hasSize(1);
        assertThat(detail.members().getFirst().article().id()).isEqualTo(2);
        assertThat(detail.members().getFirst().assignmentReason()).isEqualTo("MATCHED_COMPONENT");
        assertThat(service.story(replaced)).isEmpty();
        assertThat(service.story(UUID.randomUUID())).isEmpty();
    }

    @Test
    void rejectsInvalidPagination() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.stories(-1, 20));
        assertThatIllegalArgumentException().isThrownBy(() -> service.stories(0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> service.stories(0, 101));
    }

    private void seedVersion(long id, String status) {
        jdbc.update("INSERT INTO story_clustering_versions VALUES (?, ?)", id, status);
    }

    private void seedArticle(long id, String ref, String seenAt) {
        jdbc.update("INSERT INTO articles VALUES (?, ?, ?, 'example.org', ?)", id,
                "https://example.org/" + ref, ref, utc(seenAt));
    }

    private UUID seedStory(long seed, long version, String representative, String state, String effectiveTo) {
        UUID internalId = new UUID(0, seed);
        UUID publicId = new UUID(1, seed);
        jdbc.update("INSERT INTO stories VALUES (?, ?, ?, ?, ?, ?, ?)", internalId, publicId, version,
                representative, state, utc("2026-09-01T10:00:00Z"), utc(effectiveTo));
        return publicId;
    }

    private void seedMember(long storySeed, long version, long article, Integer current, String reason) {
        jdbc.update("INSERT INTO story_memberships VALUES (?, ?, ?, ?, ?)",
                new UUID(0, storySeed), version, article, current, reason);
    }

    private OffsetDateTime utc(String instant) {
        return Instant.parse(instant).atOffset(ZoneOffset.UTC);
    }
}
