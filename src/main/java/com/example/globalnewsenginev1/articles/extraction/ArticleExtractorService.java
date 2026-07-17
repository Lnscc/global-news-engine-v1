package com.example.globalnewsenginev1.articles.extraction;

import com.example.globalnewsenginev1.articles.normalization.ArticleUrlNormalizationException;
import com.example.globalnewsenginev1.articles.normalization.ArticleUrlNormalizer;
import com.example.globalnewsenginev1.articles.normalization.NormalizedArticleUrl;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class ArticleExtractorService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ArticleUrlNormalizer urlNormalizer;

    public ArticleExtractorService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            ArticleUrlNormalizer urlNormalizer
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.urlNormalizer = urlNormalizer;
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
                SELECT event.id, event.source_timestamp, event.source_url, event.global_event_id,
                       event.event_code, event.avg_tone
                FROM gdelt_events event
                LEFT JOIN article_signals signal
                    ON signal.signal_type = 'EVENTS' AND signal.source_id = event.id
                LEFT JOIN article_extraction_errors error
                    ON error.signal_type = 'EVENTS' AND error.source_id = event.id
                WHERE signal.source_id IS NULL
                  AND error.source_id IS NULL
                ORDER BY event.id
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
                SELECT mention.id, mention.source_timestamp, mention.mention_identifier, mention.global_event_id,
                       mention.mention_doc_tone
                FROM gdelt_mentions mention
                LEFT JOIN article_signals signal
                    ON signal.signal_type = 'MENTIONS' AND signal.source_id = mention.id
                LEFT JOIN article_extraction_errors error
                    ON error.signal_type = 'MENTIONS' AND error.source_id = mention.id
                WHERE signal.source_id IS NULL
                  AND error.source_id IS NULL
                ORDER BY mention.id
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
                SELECT gkg.id, gkg.source_timestamp, gkg.document_identifier
                FROM gdelt_gkg gkg
                LEFT JOIN article_extraction_errors error
                    ON error.signal_type = 'GKG' AND error.source_id = gkg.id
                WHERE gkg.article_id IS NULL
                  AND error.source_id IS NULL
                ORDER BY gkg.id
                LIMIT ?
                """, resultSet -> {
            GkgStageRecord record = new GkgStageRecord(
                    resultSet.getLong("id"),
                    resultSet.getTimestamp("source_timestamp").toInstant(),
                    resultSet.getString("document_identifier"));
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
            jdbcTemplate.update("UPDATE gdelt_gkg SET article_id = ? WHERE id = ? AND article_id IS NULL",
                    article.articleId(), record.sourceId());
            counters.signalsCreated++;
            if (article.created()) counters.articlesCreated++;
        } catch (ArticleUrlNormalizationException exception) {
            insertExtractionError(new StageSignal("GKG", record.sourceId(), record.sourceTimestamp(),
                    record.documentIdentifier(), null, null, null, null, null, null, null, null),
                    exception.code(), exception.getMessage());
            counters.errorsCreated++;
        }
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

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime nullableUtc(Instant instant) {
        return instant == null ? null : utc(instant);
    }

    private record ArticleUpsert(Long articleId, boolean created) {
    }

    private record GkgStageRecord(long sourceId, Instant sourceTimestamp, String documentIdentifier) { }

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
