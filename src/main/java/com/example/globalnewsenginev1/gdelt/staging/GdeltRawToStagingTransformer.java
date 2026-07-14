package com.example.globalnewsenginev1.gdelt.staging;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageEvent;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageGkg;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageMention;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStagingResult;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltEventParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltGkgParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltMentionParser;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltParseException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    public GdeltRawToStagingTransformer(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this(jdbcTemplate, transactionTemplate, new GdeltEventParser(), new GdeltMentionParser(), new GdeltGkgParser());
    }

    GdeltRawToStagingTransformer(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            GdeltEventParser eventParser,
            GdeltMentionParser mentionParser,
            GdeltGkgParser gkgParser
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.eventParser = eventParser;
        this.mentionParser = mentionParser;
        this.gkgParser = gkgParser;
    }

    public GdeltStagingResult transformCompletedRawRows(int batchSize) {
        return transactionTemplate.execute(status -> {
            backfillGkgMetadata(batchSize);
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
        for (RawRow row : loadPendingRows("gdelt_raw_events", "gdelt_stage_events", "EVENTS", batchSize)) {
            try {
                GdeltStageEvent event = eventParser.parse(row.rawTsv());
                jdbcTemplate.update("""
                        INSERT INTO gdelt_stage_events
                            (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                             global_event_id, event_date, actor1_code, actor1_name, actor1_country_code,
                             actor2_code, actor2_name, actor2_country_code, event_code, quad_class,
                             goldstein_scale, avg_tone, source_url)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                        row.rowNumber(), utc(Instant.now()), event.globalEventId(), event.eventDate(),
                        event.actor1Code(), event.actor1Name(), event.actor1CountryCode(),
                        event.actor2Code(), event.actor2Name(), event.actor2CountryCode(),
                        event.eventCode(), event.quadClass(), event.goldsteinScale(),
                        event.avgTone(), event.sourceUrl());
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
        for (RawRow row : loadPendingRows("gdelt_raw_mentions", "gdelt_stage_mentions", "MENTIONS", batchSize)) {
            try {
                GdeltStageMention mention = mentionParser.parse(row.rawTsv());
                jdbcTemplate.update("""
                        INSERT INTO gdelt_stage_mentions
                            (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                             global_event_id, event_time_date, mention_time_date, mention_type,
                             mention_source_name, mention_identifier, confidence, mention_doc_tone)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                        row.rowNumber(), utc(Instant.now()), mention.globalEventId(),
                        nullableUtc(mention.eventTimeDate()), nullableUtc(mention.mentionTimeDate()),
                        mention.mentionType(), mention.mentionSourceName(), mention.mentionIdentifier(),
                        mention.confidence(), mention.mentionDocTone());
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
        for (RawRow row : loadPendingRows("gdelt_raw_gkg", "gdelt_stage_gkg", "GKG", batchSize)) {
            try {
                GdeltStageGkg gkg = gkgParser.parse(row.rawTsv());
                jdbcTemplate.update("""
                        INSERT INTO gdelt_stage_gkg
                            (raw_id, import_file_id, source_file, source_timestamp, row_number, staged_at,
                             gkg_record_id, document_date, source_collection_identifier, source_common_name,
                             document_identifier, themes, persons, organizations, locations, tone,
                             sharing_image_url, page_title, page_precise_pub_timestamp, metadata_extracted)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                        """,
                        row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                        row.rowNumber(), utc(Instant.now()), gkg.gkgRecordId(), nullableUtc(gkg.documentDate()),
                        gkg.sourceCollectionIdentifier(), gkg.sourceCommonName(), gkg.documentIdentifier(),
                        gkg.themes(), gkg.persons(), gkg.organizations(), gkg.locations(), gkg.tone(),
                        gkg.sharingImageUrl(),
                        gkg.pageTitle(), nullableUtc(gkg.pagePrecisePublicationTime()));
                staged++;
            } catch (GdeltParseException exception) {
                insertError("GKG", row, exception);
                errors++;
            }
        }
        return new DatasetResult(staged, errors);
    }

    private void backfillGkgMetadata(int batchSize) {
        List<GkgMetadataRow> rows = jdbcTemplate.query("""
                SELECT stage.id, raw.raw_tsv
                FROM gdelt_stage_gkg stage
                JOIN gdelt_raw_gkg raw ON raw.id = stage.raw_id
                WHERE stage.metadata_extracted = FALSE
                ORDER BY stage.id
                LIMIT ?
                """, (resultSet, rowNum) -> new GkgMetadataRow(
                resultSet.getLong("id"), resultSet.getString("raw_tsv")), batchSize);
        for (GkgMetadataRow row : rows) {
            GdeltStageGkg gkg = gkgParser.parse(row.rawTsv());
            jdbcTemplate.update("""
                    UPDATE gdelt_stage_gkg
                    SET sharing_image_url = ?, page_title = ?, page_precise_pub_timestamp = ?, metadata_extracted = TRUE
                    WHERE id = ?
                    """, gkg.sharingImageUrl(), gkg.pageTitle(),
                    nullableUtc(gkg.pagePrecisePublicationTime()), row.stageId());
        }
    }

    private List<RawRow> loadPendingRows(String rawTable, String stageTable, String datasetType, int batchSize) {
        String sql = """
                SELECT raw.id, raw.import_file_id, raw.source_file, raw.source_timestamp, raw.row_number, raw.raw_tsv
                FROM %s raw
                JOIN gdelt_import_files import_file ON import_file.id = raw.import_file_id
                LEFT JOIN %s stage ON stage.raw_id = raw.id
                LEFT JOIN gdelt_stage_errors error
                    ON error.dataset_type = ? AND error.raw_id = raw.id
                WHERE import_file.status = 'COMPLETED'
                  AND stage.raw_id IS NULL
                  AND error.raw_id IS NULL
                ORDER BY raw.id
                LIMIT ?
                """.formatted(rawTable, stageTable);
        return jdbcTemplate.query(sql, (resultSet, rowNum) -> new RawRow(
                resultSet.getLong("id"),
                resultSet.getLong("import_file_id"),
                resultSet.getString("source_file"),
                resultSet.getTimestamp("source_timestamp").toInstant(),
                resultSet.getLong("row_number"),
                resultSet.getString("raw_tsv")
        ), datasetType, batchSize);
    }

    private void insertError(String datasetType, RawRow row, GdeltParseException exception) {
        jdbcTemplate.update("""
                INSERT INTO gdelt_stage_errors
                    (dataset_type, raw_id, import_file_id, source_file, source_timestamp, row_number, raw_tsv,
                     error_code, error_message, staged_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                datasetType, row.rawId(), row.importFileId(), row.sourceFile(), utc(row.sourceTimestamp()),
                row.rowNumber(), row.rawTsv(), exception.code(), exception.getMessage(), utc(Instant.now()));
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
            String rawTsv
    ) {
    }

    private record DatasetResult(long staged, long errors) {
    }

    private record GkgMetadataRow(long stageId, String rawTsv) {
    }
}
