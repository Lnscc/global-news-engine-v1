package com.example.globalnewsenginev1.articles;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleDatabaseViewsTests {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:article-views-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V4__create_article_debug_views.sql"));
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void exposesArticleSummaryAndDetailIncludingArticlesWithoutSignals() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-11T10:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO articles
                    (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """,
                "https://example.com/one", "hash-one", "example.com", timestamp, timestamp, timestamp,
                "https://example.com/two", "hash-two", "example.com", timestamp, timestamp, timestamp);
        Long articleId = jdbcTemplate.queryForObject(
                "SELECT id FROM articles WHERE url_hash = 'hash-one'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, global_event_id,
                     event_code, themes, tone_value, created_at)
                VALUES (?, 'EVENT', 101, ?, 9001, '010', 'THEME_A', -1.5, ?),
                       (?, 'GKG', 102, ?, NULL, NULL, 'THEME_B', 2.5, ?)
                """, articleId, timestamp, timestamp, articleId, timestamp.plusMinutes(5), timestamp);

        Map<String, Object> summary = jdbcTemplate.queryForMap("""
                SELECT signal_count, event_signal_count, mention_signal_count, gkg_signal_count
                FROM article_signal_summary_view
                WHERE article_id = ?
                """, articleId);
        assertThat(((Number) summary.get("SIGNAL_COUNT")).longValue()).isEqualTo(2);
        assertThat(((Number) summary.get("EVENT_SIGNAL_COUNT")).longValue()).isEqualTo(1);
        assertThat(((Number) summary.get("MENTION_SIGNAL_COUNT")).longValue()).isZero();
        assertThat(((Number) summary.get("GKG_SIGNAL_COUNT")).longValue()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForList("""
                SELECT canonical_url, signal_type, source_id, themes
                FROM article_detail_view
                WHERE article_id = ?
                ORDER BY source_id
                """, articleId))
                .hasSize(2)
                .extracting(row -> row.get("SIGNAL_TYPE"))
                .containsExactly("EVENT", "GKG");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_detail_view
                WHERE canonical_url = 'https://example.com/two' AND signal_id IS NULL
                """, Long.class)).isEqualTo(1);
    }
}
