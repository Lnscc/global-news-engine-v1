package com.example.globalnewsenginev1.articles.extraction;

import com.example.globalnewsenginev1.articles.normalization.ArticleUrlNormalizationException;
import com.example.globalnewsenginev1.articles.normalization.ArticleUrlNormalizer;
import com.example.globalnewsenginev1.articles.normalization.GkgValueNormalizer;
import com.example.globalnewsenginev1.articles.normalization.NormalizedArticleUrl;
import com.example.globalnewsenginev1.articles.normalization.NormalizedGkgValues;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ArticleExtractorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArticleExtractorService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ArticleUrlNormalizer urlNormalizer;
    private final ObjectMapper objectMapper;
    private final GkgValueNormalizer gkgValueNormalizer;

    public ArticleExtractorService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            ArticleUrlNormalizer urlNormalizer
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.urlNormalizer = urlNormalizer;
        this.objectMapper = new ObjectMapper();
        this.gkgValueNormalizer = new GkgValueNormalizer();
    }

    public ArticleExtractionResult extractArticles(int batchSize) {
        return transactionTemplate.execute(status -> {
            ArticleExtractionResult events = extractEvents(batchSize);
            ArticleExtractionResult mentions = extractMentions(batchSize);
            ArticleExtractionResult gkg = extractGkg(batchSize);
            return events.plus(mentions).plus(gkg);
        });
    }

    private ArticleExtractionResult extractEvents(int batchSize) {
        Counters counters = new Counters();
        jdbcTemplate.query("""
                SELECT stage.id, stage.source_timestamp, stage.source_url, stage.global_event_id,
                       stage.event_code, stage.avg_tone
                FROM gdelt_stage_events stage
                LEFT JOIN article_signals signal
                    ON signal.signal_type = 'EVENTS' AND signal.source_id = stage.id
                LEFT JOIN article_extraction_errors error
                    ON error.signal_type = 'EVENTS' AND error.source_id = stage.id
                WHERE signal.source_id IS NULL
                  AND error.source_id IS NULL
                ORDER BY stage.id
                LIMIT ?
                """, resultSet -> {
            StageSignal signal = new StageSignal(
                    "EVENTS",
                    resultSet.getLong("id"),
                    resultSet.getTimestamp("source_timestamp").toInstant(),
                    resultSet.getString("source_url"),
                    nullableLong(resultSet, "global_event_id"),
                    resultSet.getString("event_code"),
                    null,
                    null,
                    null,
                    null,
                    nullableDouble(resultSet, "avg_tone"),
                    null);
            process(signal, counters);
        }, batchSize);
        return counters.result();
    }

    private ArticleExtractionResult extractMentions(int batchSize) {
        Counters counters = new Counters();
        jdbcTemplate.query("""
                SELECT stage.id, stage.source_timestamp, stage.mention_identifier, stage.global_event_id,
                       stage.mention_doc_tone
                FROM gdelt_stage_mentions stage
                LEFT JOIN article_signals signal
                    ON signal.signal_type = 'MENTIONS' AND signal.source_id = stage.id
                LEFT JOIN article_extraction_errors error
                    ON error.signal_type = 'MENTIONS' AND error.source_id = stage.id
                WHERE signal.source_id IS NULL
                  AND error.source_id IS NULL
                ORDER BY stage.id
                LIMIT ?
                """, resultSet -> {
            StageSignal signal = new StageSignal(
                    "MENTIONS",
                    resultSet.getLong("id"),
                    resultSet.getTimestamp("source_timestamp").toInstant(),
                    resultSet.getString("mention_identifier"),
                    nullableLong(resultSet, "global_event_id"),
                    null,
                    null,
                    null,
                    null,
                    null,
                    nullableDouble(resultSet, "mention_doc_tone"),
                    null);
            process(signal, counters);
        }, batchSize);
        return counters.result();
    }

    private ArticleExtractionResult extractGkg(int batchSize) {
        Counters counters = new Counters();
        jdbcTemplate.query("""
                SELECT stage.id, stage.source_timestamp, stage.document_identifier, stage.page_title,
                       stage.page_precise_pub_timestamp, stage.sharing_image_url,
                       stage.themes, stage.persons, stage.organizations, stage.locations, stage.tone
                FROM gdelt_stage_gkg stage
                LEFT JOIN gdelt_gkg_records record ON record.source_id = stage.id
                LEFT JOIN article_extraction_errors error
                    ON error.signal_type = 'GKG' AND error.source_id = stage.id
                WHERE record.source_id IS NULL
                  AND error.source_id IS NULL
                ORDER BY stage.id
                LIMIT ?
                """, resultSet -> {
            GkgStageRecord record = new GkgStageRecord(
                    resultSet.getLong("id"),
                    resultSet.getTimestamp("source_timestamp").toInstant(),
                    resultSet.getString("document_identifier"),
                    resultSet.getString("page_title"),
                    nullableInstant(resultSet, "page_precise_pub_timestamp"),
                    resultSet.getString("sharing_image_url"),
                    resultSet.getString("themes"),
                    resultSet.getString("persons"),
                    resultSet.getString("organizations"),
                    resultSet.getString("locations"),
                    resultSet.getString("tone"));
            processGkg(record, counters);
        }, batchSize);
        return counters.result();
    }

    private void process(StageSignal signal, Counters counters) {
        try {
            NormalizedArticleUrl normalizedUrl = urlNormalizer.normalize(signal.rawUrl());
            ArticleUpsert article = upsertArticle(normalizedUrl, signal.sourceTimestamp());
            insertSignal(article.articleId(), signal);
            counters.signalsCreated++;
            if (article.created()) {
                counters.articlesCreated++;
            }
        } catch (ArticleUrlNormalizationException exception) {
            insertExtractionError(signal, exception.code(), exception.getMessage());
            counters.errorsCreated++;
        }
    }

    private void processGkg(GkgStageRecord record, Counters counters) {
        try {
            NormalizedArticleUrl normalizedUrl = urlNormalizer.normalize(record.documentIdentifier());
            ArticleUpsert article = upsertArticle(normalizedUrl, record.sourceTimestamp());
            List<String> themes = normalizeThemes(record.themesRaw());
            NormalizedGkgValues values = gkgValueNormalizer.normalize(
                    record.personsRaw(), record.organizationsRaw(), record.locationsRaw(), record.toneRaw());
            jdbcTemplate.update("""
                    INSERT INTO gdelt_gkg_records
                        (source_id, article_id, source_timestamp, document_identifier, page_title,
                         page_precise_pub_timestamp,
                         main_image_url, main_image_source,
                         themes, persons, organizations, locations, tone_value,
                         tone_positive_score, tone_negative_score, tone_polarity,
                         tone_activity_reference_density, tone_self_group_reference_density,
                         tone_word_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, ?)
                    """, record.sourceId(), article.articleId(), utc(record.sourceTimestamp()),
                    record.documentIdentifier(), record.pageTitle(), nullableUtc(record.pagePrecisePublicationTime()),
                    record.sharingImageUrl(), record.sharingImageUrl() == null ? null : "GKG_SHARING_IMAGE",
                    new SqlArrayValue("TEXT", themes.toArray()),
                    new SqlArrayValue("TEXT", values.persons().toArray()),
                    new SqlArrayValue("TEXT", values.organizations().toArray()),
                    locationsJson(values), values.tone().value(), values.tone().positiveScore(),
                    values.tone().negativeScore(), values.tone().polarity(),
                    values.tone().activityReferenceDensity(), values.tone().selfGroupReferenceDensity(),
                    values.tone().wordCount(),
                    utc(Instant.now()));
            if (values.discardedLocationCount() > 0) {
                LOGGER.warn("Discarded malformed GKG locations: source_id={}, discarded_location_count={}",
                        record.sourceId(), values.discardedLocationCount());
            }
            counters.signalsCreated++;
            if (article.created()) counters.articlesCreated++;
        } catch (ArticleUrlNormalizationException exception) {
            insertExtractionError(new StageSignal("GKG", record.sourceId(), record.sourceTimestamp(),
                    record.documentIdentifier(), null, null, null, null, null, null, null, null),
                    exception.code(), exception.getMessage());
            counters.errorsCreated++;
        }
    }

    private List<String> normalizeThemes(String themesRaw) {
        if (themesRaw == null || themesRaw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> normalizedThemes = new LinkedHashSet<>();
        for (String rawTheme : themesRaw.split(";", -1)) {
            String theme = rawTheme.trim();
            if (!theme.isEmpty()) {
                normalizedThemes.add(theme);
            }
        }
        return List.copyOf(normalizedThemes);
    }

    private ArticleUpsert upsertArticle(NormalizedArticleUrl normalizedUrl, Instant sourceTimestamp) {
        Long articleId = findArticleId(normalizedUrl.urlHash());
        Instant now = Instant.now();
        if (articleId == null) {
            jdbcTemplate.update("""
                    INSERT INTO articles
                        (canonical_url, url_hash, domain, first_seen_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    normalizedUrl.canonicalUrl(), normalizedUrl.urlHash(), normalizedUrl.domain(),
                    utc(sourceTimestamp), utc(now), utc(now));
            return new ArticleUpsert(findArticleId(normalizedUrl.urlHash()), true);
        }

        jdbcTemplate.update("""
                UPDATE articles
                SET first_seen_at = CASE WHEN first_seen_at > ? THEN ? ELSE first_seen_at END,
                    updated_at = ?
                WHERE id = ?
                """, utc(sourceTimestamp), utc(sourceTimestamp), utc(now), articleId);
        return new ArticleUpsert(articleId, false);
    }

    private Long findArticleId(String urlHash) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM articles WHERE url_hash = ?",
                    Long.class,
                    urlHash);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private void insertSignal(long articleId, StageSignal signal) {
        jdbcTemplate.update("""
                INSERT INTO article_signals
                    (article_id, signal_type, source_id, source_timestamp, global_event_id, event_code,
                     themes, persons, organizations, locations, tone_value, tone_raw, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                articleId, signal.signalType(), signal.sourceId(), utc(signal.sourceTimestamp()),
                signal.globalEventId(), signal.eventCode(), signal.themes(), signal.persons(),
                signal.organizations(), signal.locations(), signal.toneValue(), signal.toneRaw(),
                utc(Instant.now()));
    }

    private void insertExtractionError(StageSignal signal, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO article_extraction_errors
                    (signal_type, source_id, source_timestamp, raw_url, error_code, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                signal.signalType(), signal.sourceId(), utc(signal.sourceTimestamp()), signal.rawUrl(),
                errorCode, errorMessage, utc(Instant.now()));
    }

    private String locationsJson(NormalizedGkgValues values) {
        try {
            return objectMapper.writeValueAsString(values.locations());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize normalized GKG locations", exception);
        }
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        java.sql.Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime nullableUtc(Instant instant) {
        return instant == null ? null : utc(instant);
    }

    private record ArticleUpsert(Long articleId, boolean created) {
    }

    private record GkgStageRecord(long sourceId, Instant sourceTimestamp, String documentIdentifier,
                                  String pageTitle, Instant pagePrecisePublicationTime,
                                  String sharingImageUrl,
                                  String themesRaw, String personsRaw,
                                  String organizationsRaw, String locationsRaw, String toneRaw) { }

    private record StageSignal(
            String signalType,
            long sourceId,
            Instant sourceTimestamp,
            String rawUrl,
            Long globalEventId,
            String eventCode,
            String themes,
            String persons,
            String organizations,
            String locations,
            Double toneValue,
            String toneRaw
    ) {
    }

    private static class Counters {
        private long articlesCreated;
        private long signalsCreated;
        private long errorsCreated;

        ArticleExtractionResult result() {
            return new ArticleExtractionResult(articlesCreated, signalsCreated, errorsCreated);
        }
    }
}
