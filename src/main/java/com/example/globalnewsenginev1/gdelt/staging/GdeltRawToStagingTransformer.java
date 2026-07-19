package com.example.globalnewsenginev1.gdelt.staging;

import com.example.globalnewsenginev1.articles.normalization.GkgValueNormalizer;
import com.example.globalnewsenginev1.articles.normalization.NormalizedGkgValues;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageEvent;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageGkg;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageMention;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStagingResult;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltEventParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltGkgParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltMentionParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class GdeltRawToStagingTransformer {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final GdeltEventParser eventParser;
    private final GdeltMentionParser mentionParser;
    private final GdeltGkgParser gkgParser;
    private final Duration retryDelay;
    private final GkgValueNormalizer gkgValueNormalizer = new GkgValueNormalizer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public GdeltRawToStagingTransformer(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            @Value("${gdelt.staging.retry-delay:PT1M}") Duration retryDelay
    ) {
        this(jdbcTemplate, transactionTemplate, new GdeltEventParser(), new GdeltMentionParser(),
                new GdeltGkgParser(), retryDelay);
    }

    GdeltRawToStagingTransformer(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this(jdbcTemplate, transactionTemplate, new GdeltEventParser(), new GdeltMentionParser(),
                new GdeltGkgParser(), Duration.ZERO);
    }

    GdeltRawToStagingTransformer(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            GdeltEventParser eventParser,
            GdeltMentionParser mentionParser,
            GdeltGkgParser gkgParser,
            Duration retryDelay
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.eventParser = eventParser;
        this.mentionParser = mentionParser;
        this.gkgParser = gkgParser;
        this.retryDelay = retryDelay;
    }

    public GdeltStagingResult transformCompletedRawRows(int batchSize) {
        return transactionTemplate.execute(status -> {
            DatasetResult events = stageEvents(batchSize);
            DatasetResult mentions = stageMentions(batchSize);
            DatasetResult gkg = stageGkg(batchSize);
            return new GdeltStagingResult(
                    events.staged(),
                    mentions.staged(),
                    gkg.staged(),
                    events.errors() + mentions.errors() + gkg.errors());
        });
    }

    private DatasetResult stageEvents(int batchSize) {
        long staged = 0;
        long errors = 0;
        for (RawRow row : loadPendingRows(
                "gdelt_event_payloads", "gdelt_events", "id", "EVENTS", batchSize)) {
            try {
                GdeltStageEvent event = eventParser.parse(row.rawTsv());
                jdbcTemplate.update("""
                        INSERT INTO gdelt_events
                            (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                             global_event_id, event_date, actor1_code, actor1_name, actor1_country_code,
                             actor2_code, actor2_name, actor2_country_code, event_code, quad_class,
                             goldstein_scale, avg_tone, source_url)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                        row.rowNumber(), utc(row.ingestedAt()), utc(Instant.now()),
                        event.globalEventId(), event.eventDate(),
                        event.actor1Code(), event.actor1Name(), event.actor1CountryCode(),
                        event.actor2Code(), event.actor2Name(), event.actor2CountryCode(),
                        event.eventCode(), event.quadClass(), event.goldsteinScale(),
                        event.avgTone(), event.sourceUrl());
                resolveErrors("EVENTS", row.rawId());
                staged++;
            } catch (GdeltParseException exception) {
                insertError("EVENTS", row, exception);
                errors++;
            }
        }
        return new DatasetResult(staged, errors);
    }

    private DatasetResult stageMentions(int batchSize) {
        long staged = 0;
        long errors = 0;
        for (RawRow row : loadPendingRows(
                "gdelt_mention_payloads", "gdelt_mentions", "id", "MENTIONS", batchSize)) {
            try {
                GdeltStageMention mention = mentionParser.parse(row.rawTsv());
                jdbcTemplate.update("""
                        INSERT INTO gdelt_mentions
                            (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                             global_event_id, event_time_date, mention_time_date, mention_type,
                             mention_source_name, mention_identifier, confidence, mention_doc_tone)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                        row.rowNumber(), utc(row.ingestedAt()), utc(Instant.now()), mention.globalEventId(),
                        nullableUtc(mention.eventTimeDate()), nullableUtc(mention.mentionTimeDate()),
                        mention.mentionType(), mention.mentionSourceName(), mention.mentionIdentifier(),
                        mention.confidence(), mention.mentionDocTone());
                resolveErrors("MENTIONS", row.rawId());
                staged++;
            } catch (GdeltParseException exception) {
                insertError("MENTIONS", row, exception);
                errors++;
            }
        }
        return new DatasetResult(staged, errors);
    }

    private DatasetResult stageGkg(int batchSize) {
        long staged = 0;
        long errors = 0;
        for (RawRow row : loadPendingRows(
                "gdelt_gkg_payloads", "gdelt_gkg", "id", "GKG", batchSize)) {
            try {
                GdeltStageGkg gkg = gkgParser.parse(row.rawTsv());
                NormalizedGkgValues normalized = gkgValueNormalizer.normalize(
                        gkg.persons(), gkg.organizations(), gkg.locations(), gkg.tone());
                jdbcTemplate.update("""
                        INSERT INTO gdelt_gkg
                            (id, import_file_id, source_file, source_timestamp, row_number, ingested_at, parsed_at,
                             gkg_record_id, document_date, source_collection_identifier, source_common_name,
                             document_identifier, sharing_image_url, page_title, page_precise_pub_timestamp,
                             themes, persons, organizations, locations, tone_value, tone_positive_score,
                             tone_negative_score, tone_polarity, tone_activity_reference_density,
                             tone_self_group_reference_density, tone_word_count,
                             main_image_url, main_image_source, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                CAST(? AS JSON), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                        row.rowNumber(), utc(row.ingestedAt()), utc(Instant.now()),
                        gkg.gkgRecordId(), nullableUtc(gkg.documentDate()),
                        gkg.sourceCollectionIdentifier(), gkg.sourceCommonName(), gkg.documentIdentifier(),
                        gkg.sharingImageUrl(), gkg.pageTitle(), nullableUtc(gkg.pagePrecisePublicationTime()),
                        new SqlArrayValue("TEXT", normalizeThemes(gkg.themes())),
                        new SqlArrayValue("TEXT", normalized.persons().toArray()),
                        new SqlArrayValue("TEXT", normalized.organizations().toArray()),
                        locationsJson(normalized), normalized.tone().value(), normalized.tone().positiveScore(),
                        normalized.tone().negativeScore(), normalized.tone().polarity(),
                        normalized.tone().activityReferenceDensity(), normalized.tone().selfGroupReferenceDensity(),
                        normalized.tone().wordCount(), gkg.sharingImageUrl(),
                        gkg.sharingImageUrl() == null ? null : "GKG_SHARING_IMAGE", utc(Instant.now()));
                resolveErrors("GKG", row.rawId());
                staged++;
            } catch (GdeltParseException exception) {
                insertError("GKG", row, exception);
                errors++;
            }
        }
        return new DatasetResult(staged, errors);
    }

    private List<RawRow> loadPendingRows(
            String rawTable,
            String stageTable,
            String sourceIdColumn,
            String datasetType,
            int batchSize
    ) {
        String sql = """
                SELECT raw.id, raw.import_file_id, raw.source_file, raw.source_timestamp, raw.row_number,
                       raw.raw_tsv, raw.ingested_at
                FROM %s raw
                JOIN gdelt_import_files import_file ON import_file.id = raw.import_file_id
                LEFT JOIN %s stage ON stage.%s = raw.id
                WHERE import_file.status = 'COMPLETED'
                  AND stage.%s IS NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM gdelt_processing_errors error
                      WHERE error.dataset_type = ?
                        AND error.source_row_id = raw.id
                        AND error.resolved_at IS NULL
                        AND error.occurred_at > ?
                  )
                ORDER BY raw.id
                LIMIT ?
                """.formatted(rawTable, stageTable, sourceIdColumn, sourceIdColumn);
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new RawRow(
                resultSet.getLong("id"),
                resultSet.getLong("import_file_id"),
                resultSet.getString("source_file"),
                resultSet.getTimestamp("source_timestamp").toInstant(),
                resultSet.getLong("row_number"),
                resultSet.getString("raw_tsv"),
                resultSet.getTimestamp("ingested_at").toInstant()
        ), datasetType, utc(Instant.now().minus(retryDelay)), batchSize);
    }

    private void insertError(String datasetType, RawRow row, GdeltParseException exception) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_processing_errors
                    (dataset_type, source_row_id, import_file_id, source_file, source_timestamp, row_number,
                     failed_step, error_code, error_message, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PARSING', ?, ?, ?)
                """,
                datasetType, row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                row.rowNumber(), exception.code(), exception.getMessage(), utc(Instant.now()));
    }

    private void resolveErrors(String datasetType, long sourceRowId) {
        jdbcTemplate.update("""
                UPDATE gdelt_processing_errors
                SET resolved_at = ?
                WHERE dataset_type = ? AND source_row_id = ? AND resolved_at IS NULL
                """, utc(Instant.now()), datasetType, sourceRowId);
    }

    private OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private OffsetDateTime nullableUtc(Instant instant) {
        return instant == null ? null : utc(instant);
    }

    private record RawRow(
            long rawId,
            long importFileId,
            String sourceFile,
            Instant sourceTimestamp,
            long rowNumber,
            String rawTsv,
            Instant ingestedAt
    ) {
    }

    private record DatasetResult(long staged, long errors) {
    }

    private Object[] normalizeThemes(String themes) {
        if (themes == null || themes.isBlank()) return new Object[0];
        return java.util.Arrays.stream(themes.split(";", -1)).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toArray();
    }

    private String locationsJson(NormalizedGkgValues normalized) {
        try {
            return objectMapper.writeValueAsString(normalized.locations());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize normalized GKG locations", exception);
        }
    }
}
