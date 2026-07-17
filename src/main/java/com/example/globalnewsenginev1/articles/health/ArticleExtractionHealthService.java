package com.example.globalnewsenginev1.articles.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleExtractionHealthService {

    private static final List<String> SIGNAL_TYPES = List.of("EVENTS", "MENTIONS", "GKG");

    private final JdbcTemplate jdbcTemplate;

    public ArticleExtractionHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ArticleExtractionHealth health() {
        Map<String, MutableHealth> healthByType = new LinkedHashMap<>();
        SIGNAL_TYPES.forEach(type -> healthByType.put(type, new MutableHealth()));

        jdbcTemplate.query("""
                SELECT signal_type, COUNT(*) AS signal_count FROM (
                    SELECT 'EVENTS' AS signal_type FROM gdelt_events WHERE article_id IS NOT NULL
                    UNION ALL SELECT 'MENTIONS' FROM gdelt_mentions WHERE article_id IS NOT NULL
                    UNION ALL SELECT 'GKG' FROM gdelt_gkg WHERE article_id IS NOT NULL
                ) signals
                GROUP BY signal_type
                """, (RowCallbackHandler) resultSet -> healthByType.get(resultSet.getString("signal_type"))
                .articleSignals = resultSet.getLong("signal_count"));

        jdbcTemplate.query("""
                SELECT signal_type, MAX(source_timestamp) AS latest_processed
                FROM (
                    SELECT 'EVENTS' AS signal_type, source_timestamp FROM gdelt_events WHERE article_id IS NOT NULL
                    UNION ALL
                    SELECT 'MENTIONS', source_timestamp FROM gdelt_mentions WHERE article_id IS NOT NULL
                    UNION ALL
                    SELECT 'GKG', source_timestamp FROM gdelt_gkg WHERE article_id IS NOT NULL
                    UNION ALL
                    SELECT signal_type, source_timestamp FROM article_extraction_errors
                    UNION ALL
                    SELECT dataset_type, source_timestamp FROM gdelt_processing_errors
                ) processed
                GROUP BY signal_type
                """, (RowCallbackHandler) resultSet -> healthByType.get(resultSet.getString("signal_type"))
                .latestProcessed = instant(resultSet.getTimestamp("latest_processed")));

        jdbcTemplate.query("""
                SELECT signal_type, error_code, COUNT(*) AS error_count
                FROM (
                    SELECT signal_type, error_code FROM article_extraction_errors
                    UNION ALL
                    SELECT dataset_type, error_code
                    FROM gdelt_processing_errors
                    WHERE resolved_at IS NULL
                ) open_errors
                GROUP BY signal_type, error_code
                ORDER BY signal_type, error_code
                """, (RowCallbackHandler) resultSet -> healthByType.get(resultSet.getString("signal_type")).errors.add(
                new ExtractionErrorCount(resultSet.getString("error_code"), resultSet.getLong("error_count"))));

        pending("EVENTS", "gdelt_events", healthByType);
        pending("MENTIONS", "gdelt_mentions", healthByType);
        pendingGkg(healthByType);

        long articlesCreatedTotal = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM articles", Long.class);
        List<SignalTypeExtractionHealth> signalTypes = healthByType.entrySet().stream()
                .map(entry -> entry.getValue().toResult(entry.getKey()))
                .toList();
        return new ArticleExtractionHealth(articlesCreatedTotal, signalTypes);
    }

    private void pending(String signalType, String stageTable, Map<String, MutableHealth> healthByType) {
        String query = """
                SELECT COUNT(*)
                FROM %s stage
                WHERE stage.article_id IS NULL
                AND NOT EXISTS (
                    SELECT 1 FROM article_extraction_errors error
                    WHERE error.signal_type = ? AND error.source_id = stage.id
                )
                """.formatted(stageTable);
        Long count = jdbcTemplate.queryForObject(query, Long.class, signalType);
        healthByType.get(signalType).pendingStageRows = count;
    }

    private void pendingGkg(Map<String, MutableHealth> healthByType) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gdelt_gkg gkg
                WHERE gkg.article_id IS NULL
                  AND NOT EXISTS (
                      SELECT 1 FROM article_extraction_errors error
                      WHERE error.signal_type = 'GKG' AND error.source_id = gkg.id
                  )
                """, Long.class);
        healthByType.get("GKG").pendingStageRows = count;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static final class MutableHealth {
        private long pendingStageRows;
        private long articleSignals;
        private Instant latestProcessed;
        private final java.util.ArrayList<ExtractionErrorCount> errors = new java.util.ArrayList<>();

        private SignalTypeExtractionHealth toResult(String signalType) {
            return new SignalTypeExtractionHealth(
                    signalType, pendingStageRows, articleSignals, latestProcessed, List.copyOf(errors));
        }
    }
}
