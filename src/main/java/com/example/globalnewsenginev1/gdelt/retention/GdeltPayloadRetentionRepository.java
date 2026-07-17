package com.example.globalnewsenginev1.gdelt.retention;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Repository
class GdeltPayloadRetentionRepository {

    private final JdbcTemplate jdbcTemplate;

    GdeltPayloadRetentionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    long deleteEligible(RetentionDataset dataset, Instant cutoff, int batchSize) {
        String sql = """
                WITH eligible AS (
                    SELECT payload.id
                    FROM %s payload
                    JOIN %s domain_row ON domain_row.id = payload.id
                    WHERE domain_row.parsed_at <= ?
                    ORDER BY domain_row.parsed_at, payload.id
                    LIMIT ?
                    FOR UPDATE OF payload SKIP LOCKED
                )
                DELETE FROM %s payload
                USING eligible
                WHERE payload.id = eligible.id
                """.formatted(dataset.payloadTable, dataset.domainTable, dataset.payloadTable);
        return jdbcTemplate.update(sql, cutoff.atOffset(ZoneOffset.UTC), batchSize);
    }

    List<GdeltPayloadRetentionHealth> health(Instant cutoff) {
        return Arrays.stream(RetentionDataset.values())
                .map(dataset -> health(dataset, cutoff))
                .toList();
    }

    private GdeltPayloadRetentionHealth health(RetentionDataset dataset, Instant cutoff) {
        String sql = """
                SELECT
                    COUNT(CASE WHEN domain_row.parsed_at <= ? THEN 1 END) AS eligible_payload_rows,
                    COUNT(CASE WHEN domain_row.id IS NULL OR domain_row.parsed_at > ? THEN 1 END)
                        AS retained_payload_rows
                FROM %s payload
                LEFT JOIN %s domain_row ON domain_row.id = payload.id
                """.formatted(dataset.payloadTable, dataset.domainTable);
        var databaseCutoff = cutoff.atOffset(ZoneOffset.UTC);
        return jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> new GdeltPayloadRetentionHealth(
                dataset.datasetType,
                resultSet.getLong("eligible_payload_rows"),
                resultSet.getLong("retained_payload_rows")), databaseCutoff, databaseCutoff);
    }

    enum RetentionDataset {
        EVENTS("EVENTS", "gdelt_event_payloads", "gdelt_events"),
        MENTIONS("MENTIONS", "gdelt_mention_payloads", "gdelt_mentions"),
        GKG("GKG", "gdelt_gkg_payloads", "gdelt_gkg");

        private final String datasetType;
        private final String payloadTable;
        private final String domainTable;

        RetentionDataset(String datasetType, String payloadTable, String domainTable) {
            this.datasetType = datasetType;
            this.payloadTable = payloadTable;
            this.domainTable = domainTable;
        }
    }
}
