package com.example.globalnewsenginev1.articles;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GkgThemeMigrationTests {

    @Test
    void backfillsTrimmedDeduplicatedThemesAsArray() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:gkg-theme-migration-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "V1__create_gdelt_raw_tables.sql");
            execute(connection, "V2__create_gdelt_staging_tables.sql");
            execute(connection, "V3__create_articles.sql");
            execute(connection, "V4__create_article_debug_views.sql");
            execute(connection, "V7__add_gkg_article_titles.sql");
            execute(connection, "V8__create_gkg_records.sql");
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-07-12T10:00:00Z");
        jdbcTemplate.update("""
                INSERT INTO articles
                    (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                VALUES ('https://example.org/article', 'hash', 'example.org', ?, ?, ?)
                """, timestamp, timestamp, timestamp);
        Long articleId = jdbcTemplate.queryForObject("SELECT id FROM articles", Long.class);
        jdbcTemplate.update("""
                INSERT INTO gdelt_gkg_records
                    (source_id, article_id, source_timestamp, themes_raw, created_at)
                VALUES (1, ?, ?, ' CLIMATE ; ;ENERGY;CLIMATE;CLIMATE_CHANGE ', ?)
                """, articleId, timestamp, timestamp);

        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "V9__normalize_gkg_themes.sql");
            execute(connection, "V10__store_gkg_themes_as_array.sql");
        }

        java.util.List<String> themes = jdbcTemplate.queryForObject("SELECT themes FROM gdelt_gkg_records",
                (resultSet, rowNum) -> java.util.Arrays.stream(
                                (Object[]) resultSet.getArray("themes").getArray())
                        .map(Object::toString).toList());
        assertThat(themes)
                .containsExactly("CLIMATE", "ENERGY", "CLIMATE_CHANGE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'GDELT_GKG_RECORDS' AND column_name = 'THEMES_RAW'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'GDELT_GKG_THEMES'",
                Integer.class)).isZero();
    }

    private void execute(Connection connection, String migration) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/" + migration));
    }
}
