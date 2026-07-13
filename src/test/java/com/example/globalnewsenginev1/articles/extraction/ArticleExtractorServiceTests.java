package com.example.globalnewsenginev1.articles.extraction;

import com.example.globalnewsenginev1.articles.normalization.ArticleUrlNormalizer;
import db.migration.V11__normalize_remaining_gkg_values;
import db.migration.V12__add_gkg_publication_time;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleExtractorServiceTests {

    private JdbcTemplate jdbcTemplate;
    private ArticleExtractorService extractorService;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:articles-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V2__create_gdelt_staging_tables.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V3__create_articles.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V4__create_article_debug_views.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V7__add_gkg_article_titles.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V8__create_gkg_records.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V9__normalize_gkg_themes.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V10__store_gkg_themes_as_array.sql"));
            new V11__normalize_remaining_gkg_values().migrate(connection);
            new V12__add_gkg_publication_time().migrate(connection);
        }

        jdbcTemplate = new JdbcTemplate(dataSource);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        extractorService = new ArticleExtractorService(
                jdbcTemplate,
                transactionTemplate,
                new ArticleUrlNormalizer());
    }

    @Test
    void extractsArticlesSignalsAndErrorsIdempotently() {
        Instant later = Instant.parse("2026-07-05T12:00:00Z");
        Instant earlier = Instant.parse("2026-07-05T11:45:00Z");

        long eventRawId = insertRawEvent(later);
        long mentionRawId1 = insertRawMention(later, 1);
        long mentionRawId2 = insertRawMention(earlier, 2);
        long gkgRawId = insertRawGkg(later, "https://example.org/a?utm_source=x");
        long badGkgRawId = insertRawGkg(later, "not a url");

        insertStageEvent(eventRawId, later, "https://example.org/a?utm_source=wire", -1.25);
        insertStageMention(mentionRawId1, later, "https://example.org/a#paragraph", -1.5);
        insertStageMention(mentionRawId2, earlier, "https://EXAMPLE.org/a/", -2.5);
        insertStageGkg(gkgRawId, later, "https://example.org/a?utm_source=x",
                "-3.5,2,3,5,6,7,321", "GDELT title");
        insertStageGkg(badGkgRawId, later, "not a url", "bad-tone", "Ignored title");

        ArticleExtractionResult firstRun = extractorService.extractArticles(100);
        ArticleExtractionResult secondRun = extractorService.extractArticles(100);

        assertThat(firstRun).isEqualTo(new ArticleExtractionResult(1, 4, 1));
        assertThat(secondRun).isEqualTo(new ArticleExtractionResult(0, 0, 0));
        assertThat(countRows("articles")).isEqualTo(1);
        assertThat(countRows("article_signals")).isEqualTo(3);
        assertThat(countRows("gdelt_gkg_records")).isEqualTo(1);
        assertThat(arrayValues("SELECT themes FROM gdelt_gkg_records"))
                .containsExactly("THEME", "OTHER");
        assertThat(countRows("article_extraction_errors")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT first_seen_at FROM articles", OffsetDateTime.class).toInstant())
                .isEqualTo(earlier);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT page_precise_pub_timestamp FROM gdelt_gkg_records", OffsetDateTime.class).toInstant())
                .isEqualTo(later.minusSeconds(300));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM article_signals WHERE signal_type = 'MENTIONS'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT tone_value FROM gdelt_gkg_records
                """, Double.class)).isEqualTo(-3.5);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT tone_positive_score, tone_negative_score, tone_polarity,
                       tone_activity_reference_density, tone_self_group_reference_density, tone_word_count
                FROM gdelt_gkg_records
                """))
                .containsEntry("TONE_POSITIVE_SCORE", 2.0)
                .containsEntry("TONE_NEGATIVE_SCORE", 3.0)
                .containsEntry("TONE_POLARITY", 5.0)
                .containsEntry("TONE_ACTIVITY_REFERENCE_DENSITY", 6.0)
                .containsEntry("TONE_SELF_GROUP_REFERENCE_DENSITY", 7.0)
                .containsEntry("TONE_WORD_COUNT", 321);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT error_code FROM article_extraction_errors
                """, String.class)).isEqualTo("INVALID_URL");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT page_title, persons, organizations, locations
                FROM gdelt_gkg_records
                """))
                .containsEntry("PAGE_TITLE", "GDELT title");
        assertThat(arrayValues("SELECT persons FROM gdelt_gkg_records"))
                .containsExactly("Jane Doe");
        assertThat(jdbcTemplate.queryForObject("SELECT CAST(locations AS VARCHAR) FROM gdelt_gkg_records",
                String.class)).contains("Berlin").doesNotContain("malformed");
    }

    @Test
    void keepsTheFirstGkgTitleWhenSignalsConflict() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");
        long firstRawId = insertRawGkg(timestamp, "first");
        long secondRawId = insertRawGkg(timestamp, "second");
        insertStageGkg(firstRawId, timestamp, "https://example.org/article", "0", "First title");
        insertStageGkg(secondRawId, timestamp, "https://example.org/article", "0", "Conflicting title");

        extractorService.extractArticles(100);
        extractorService.extractArticles(100);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT page_title FROM gdelt_gkg_records ORDER BY source_timestamp, id LIMIT 1
                """, String.class))
                .isEqualTo("First title");
        assertThat(countRows("gdelt_gkg_records")).isEqualTo(2);
    }

    private long insertRawEvent(Instant sourceTimestamp) {
        long importFileId = insertImportFile("EVENTS", "20260705120000.export.CSV.zip", sourceTimestamp);
        return insertRaw("gdelt_raw_events", importFileId, "20260705120000.export.CSV.zip", sourceTimestamp, 1);
    }

    private long insertRawMention(Instant sourceTimestamp, long rowNumber) {
        long importFileId = insertImportFile("MENTIONS", "20260705120000.mentions-%s.CSV.zip".formatted(rowNumber),
                sourceTimestamp);
        return insertRaw("gdelt_raw_mentions", importFileId, "20260705120000.mentions-%s.CSV.zip".formatted(rowNumber),
                sourceTimestamp, rowNumber);
    }

    private long insertRawGkg(Instant sourceTimestamp, String suffix) {
        String sourceFile = "20260705120000.%s.gkg.csv.zip".formatted(Math.abs(suffix.hashCode()));
        long importFileId = insertImportFile("GKG", sourceFile, sourceTimestamp);
        return insertRaw("gdelt_raw_gkg", importFileId, sourceFile, sourceTimestamp, 1);
    }

    private long insertImportFile(String datasetType, String sourceFile, Instant sourceTimestamp) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_import_files
                    (dataset_type, source_file, source_url, source_timestamp, status, row_count, started_at, completed_at)
                VALUES (?, ?, ?, ?, 'COMPLETED', 1, ?, ?)
                """,
                datasetType, sourceFile, "http://localhost/" + sourceFile, utc(sourceTimestamp),
                utc(sourceTimestamp), utc(sourceTimestamp));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM gdelt_import_files WHERE dataset_type = ? AND source_file = ?
                """, Long.class, datasetType, sourceFile);
    }

    private long insertRaw(
            String tableName,
            long importFileId,
            String sourceFile,
            Instant sourceTimestamp,
            long rowNumber
    ) {
        jdbcTemplate.update("""
                INSERT INTO %s
                    (import_file_id, source_file, source_timestamp, row_number, raw_tsv, ingested_at)
                VALUES (?, ?, ?, ?, 'raw', ?)
                """.formatted(tableName),
                importFileId, sourceFile, utc(sourceTimestamp), rowNumber, utc(sourceTimestamp));
        return jdbcTemplate.queryForObject("""
                SELECT id FROM %s WHERE source_file = ? AND row_number = ?
                """.formatted(tableName), Long.class, sourceFile, rowNumber);
    }

    private void insertStageEvent(long rawId, Instant sourceTimestamp, String sourceUrl, double avgTone) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_events
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     global_event_id, event_code, avg_tone, source_url)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ?, 123, '042', ?, ?
                FROM gdelt_raw_events WHERE id = ?
                """, utc(sourceTimestamp), avgTone, sourceUrl, rawId);
    }

    private void insertStageMention(long rawId, Instant sourceTimestamp, String mentionIdentifier, double tone) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_mentions
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     global_event_id, mention_identifier, mention_doc_tone)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ?, 123, ?, ?
                FROM gdelt_raw_mentions WHERE id = ?
                """, utc(sourceTimestamp), mentionIdentifier, tone, rawId);
    }

    private void insertStageGkg(
            long rawId, Instant sourceTimestamp, String documentIdentifier, String tone, String pageTitle
    ) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_gkg
                    (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                     gkg_record_id, document_identifier, themes, persons, organizations, locations, tone,
                     page_title, page_precise_pub_timestamp, metadata_extracted)
                SELECT id, import_file_id, source_file, source_timestamp, row_number, ?,
                       '20260705120000-' || id, ?, ' THEME ; ;OTHER;THEME ',
                       'Jane Doe; Jane Doe; ', ' Example Org ;Example Org',
                       '1#Berlin#GM#GM16#52.5#13.4#-1746443;malformed', ?, ?, ?, TRUE
                FROM gdelt_raw_gkg WHERE id = ?
                """, utc(sourceTimestamp), documentIdentifier, tone, pageTitle,
                utc(sourceTimestamp.minusSeconds(300)), rawId);
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private java.util.List<String> arrayValues(String query) {
        return jdbcTemplate.queryForObject(query, (resultSet, rowNum) ->
                java.util.Arrays.stream((Object[]) resultSet.getArray(1).getArray())
                        .map(Object::toString).toList());
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
