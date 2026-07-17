package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V20__move_article_links_to_domain_models extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        migrate(context.getConnection());
    }

    public void migrate(Connection connection) throws Exception {
        execute(connection, """
                ALTER TABLE gdelt_events
                ADD COLUMN article_id BIGINT REFERENCES articles(id)
                """);
        execute(connection, """
                ALTER TABLE gdelt_mentions
                ADD COLUMN article_id BIGINT REFERENCES articles(id)
                """);
        migrateAssignmentsAndDrop(connection);
    }

    void migrateAssignmentsAndDrop(Connection connection) throws Exception {
        long eventLinks = count(connection,
                "SELECT COUNT(*) FROM article_signals WHERE signal_type = 'EVENTS'");
        long mentionLinks = count(connection,
                "SELECT COUNT(*) FROM article_signals WHERE signal_type = 'MENTIONS'");

        validateSourceReferences(connection);
        validateExistingAssignments(connection);

        execute(connection, """
                UPDATE gdelt_events event
                SET article_id = (
                    SELECT signal.article_id
                    FROM article_signals signal
                    WHERE signal.signal_type = 'EVENTS' AND signal.source_id = event.id
                )
                WHERE EXISTS (
                    SELECT 1 FROM article_signals signal
                    WHERE signal.signal_type = 'EVENTS' AND signal.source_id = event.id
                )
                """);
        execute(connection, """
                UPDATE gdelt_mentions mention
                SET article_id = (
                    SELECT signal.article_id
                    FROM article_signals signal
                    WHERE signal.signal_type = 'MENTIONS' AND signal.source_id = mention.id
                )
                WHERE EXISTS (
                    SELECT 1 FROM article_signals signal
                    WHERE signal.signal_type = 'MENTIONS' AND signal.source_id = mention.id
                )
                """);

        validateBackfill(connection, eventLinks, mentionLinks);

        execute(connection, "CREATE INDEX idx_gdelt_events_article_id ON gdelt_events (article_id)");
        execute(connection, "CREATE INDEX idx_gdelt_mentions_article_id ON gdelt_mentions (article_id)");
        execute(connection, "DROP VIEW article_detail_view");
        execute(connection, "DROP VIEW article_signal_summary_view");
        createViews(connection);
        execute(connection, "DROP TABLE article_signals");
    }

    private void validateSourceReferences(Connection connection) throws Exception {
        long unsupportedTypes = count(connection, """
                SELECT COUNT(*) FROM article_signals
                WHERE signal_type NOT IN ('EVENTS', 'MENTIONS')
                """);
        long missingEvents = count(connection, """
                SELECT COUNT(*) FROM article_signals signal
                LEFT JOIN gdelt_events event ON event.id = signal.source_id
                WHERE signal.signal_type = 'EVENTS' AND event.id IS NULL
                """);
        long missingMentions = count(connection, """
                SELECT COUNT(*) FROM article_signals signal
                LEFT JOIN gdelt_mentions mention ON mention.id = signal.source_id
                WHERE signal.signal_type = 'MENTIONS' AND mention.id IS NULL
                """);
        if (unsupportedTypes != 0 || missingEvents != 0 || missingMentions != 0) {
            throw new IllegalStateException("Article link migration has invalid source references: unsupportedTypes="
                    + unsupportedTypes + ", missingEvents=" + missingEvents + ", missingMentions=" + missingMentions);
        }
    }

    private void validateExistingAssignments(Connection connection) throws Exception {
        long eventConflicts = count(connection, """
                SELECT COUNT(*) FROM article_signals signal
                JOIN gdelt_events event ON event.id = signal.source_id
                WHERE signal.signal_type = 'EVENTS'
                  AND event.article_id IS NOT NULL
                  AND event.article_id <> signal.article_id
                """);
        long mentionConflicts = count(connection, """
                SELECT COUNT(*) FROM article_signals signal
                JOIN gdelt_mentions mention ON mention.id = signal.source_id
                WHERE signal.signal_type = 'MENTIONS'
                  AND mention.article_id IS NOT NULL
                  AND mention.article_id <> signal.article_id
                """);
        if (eventConflicts != 0 || mentionConflicts != 0) {
            throw new IllegalStateException("Article link migration has conflicting assignments: events="
                    + eventConflicts + ", mentions=" + mentionConflicts);
        }
    }

    private void validateBackfill(Connection connection, long eventLinks, long mentionLinks) throws Exception {
        long migratedEventLinks = count(connection, "SELECT COUNT(*) FROM gdelt_events WHERE article_id IS NOT NULL");
        long migratedMentionLinks = count(connection,
                "SELECT COUNT(*) FROM gdelt_mentions WHERE article_id IS NOT NULL");
        long mismatchedEvents = count(connection, """
                SELECT COUNT(*) FROM article_signals signal
                JOIN gdelt_events event ON event.id = signal.source_id
                WHERE signal.signal_type = 'EVENTS' AND event.article_id <> signal.article_id
                """);
        long mismatchedMentions = count(connection, """
                SELECT COUNT(*) FROM article_signals signal
                JOIN gdelt_mentions mention ON mention.id = signal.source_id
                WHERE signal.signal_type = 'MENTIONS' AND mention.article_id <> signal.article_id
                """);
        if (eventLinks != migratedEventLinks || mentionLinks != migratedMentionLinks
                || mismatchedEvents != 0 || mismatchedMentions != 0) {
            throw new IllegalStateException("Article link migration validation failed: events=" + eventLinks + "/"
                    + migratedEventLinks + ", mentions=" + mentionLinks + "/" + migratedMentionLinks
                    + ", mismatchedEvents=" + mismatchedEvents + ", mismatchedMentions=" + mismatchedMentions);
        }
    }

    private void createViews(Connection connection) throws Exception {
        execute(connection, """
                CREATE VIEW article_signal_summary_view AS
                SELECT a.id AS article_id, a.canonical_url, a.domain, a.first_seen_at,
                       COUNT(all_signals.source_id) AS signal_count,
                       SUM(CASE WHEN all_signals.signal_type = 'EVENTS' THEN 1 ELSE 0 END) AS event_signal_count,
                       SUM(CASE WHEN all_signals.signal_type = 'MENTIONS' THEN 1 ELSE 0 END) AS mention_signal_count,
                       SUM(CASE WHEN all_signals.signal_type = 'GKG' THEN 1 ELSE 0 END) AS gkg_signal_count,
                       MIN(all_signals.source_timestamp) AS earliest_signal_at,
                       MAX(all_signals.source_timestamp) AS latest_signal_at
                FROM articles a
                LEFT JOIN (
                    SELECT article_id, 'EVENTS' AS signal_type, id AS source_id, source_timestamp
                    FROM gdelt_events WHERE article_id IS NOT NULL
                    UNION ALL
                    SELECT article_id, 'MENTIONS', id, source_timestamp
                    FROM gdelt_mentions WHERE article_id IS NOT NULL
                    UNION ALL
                    SELECT article_id, 'GKG', id, source_timestamp
                    FROM gdelt_gkg WHERE article_id IS NOT NULL
                ) all_signals ON all_signals.article_id = a.id
                GROUP BY a.id, a.canonical_url, a.domain, a.first_seen_at
                """);
        execute(connection, """
                CREATE VIEW article_detail_view AS
                SELECT a.id AS article_id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
                       a.created_at AS article_created_at, a.updated_at AS article_updated_at,
                       e.id AS signal_id, 'EVENTS' AS signal_type, e.id AS source_id, e.source_timestamp,
                       e.global_event_id, e.event_code, CAST(NULL AS VARCHAR) AS themes,
                       CAST(NULL AS VARCHAR) AS persons, CAST(NULL AS VARCHAR) AS organizations,
                       CAST(NULL AS VARCHAR) AS locations, e.avg_tone AS tone_value,
                       CAST(NULL AS DOUBLE PRECISION) AS tone_positive_score,
                       CAST(NULL AS DOUBLE PRECISION) AS tone_negative_score,
                       CAST(NULL AS DOUBLE PRECISION) AS tone_polarity,
                       CAST(NULL AS DOUBLE PRECISION) AS tone_activity_reference_density,
                       CAST(NULL AS DOUBLE PRECISION) AS tone_self_group_reference_density,
                       CAST(NULL AS INTEGER) AS tone_word_count, e.parsed_at AS signal_created_at
                FROM articles a JOIN gdelt_events e ON e.article_id = a.id
                UNION ALL
                SELECT a.id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
                       a.created_at, a.updated_at, m.id, 'MENTIONS', m.id, m.source_timestamp,
                       m.global_event_id, NULL, NULL, NULL, NULL, NULL, m.mention_doc_tone,
                       NULL, NULL, NULL, NULL, NULL, NULL, m.parsed_at
                FROM articles a JOIN gdelt_mentions m ON m.article_id = a.id
                UNION ALL
                SELECT a.id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
                       a.created_at, a.updated_at, g.id, 'GKG', g.id, g.source_timestamp,
                       NULL, NULL, CAST(g.themes AS VARCHAR), CAST(g.persons AS VARCHAR),
                       CAST(g.organizations AS VARCHAR), CAST(g.locations AS VARCHAR),
                       g.tone_value, g.tone_positive_score, g.tone_negative_score, g.tone_polarity,
                       g.tone_activity_reference_density, g.tone_self_group_reference_density,
                       g.tone_word_count, g.created_at
                FROM articles a JOIN gdelt_gkg g ON g.article_id = a.id
                UNION ALL
                SELECT a.id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
                       a.created_at, a.updated_at, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                       NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
                FROM articles a
                WHERE NOT EXISTS (SELECT 1 FROM gdelt_events e WHERE e.article_id = a.id)
                  AND NOT EXISTS (SELECT 1 FROM gdelt_mentions m WHERE m.article_id = a.id)
                  AND NOT EXISTS (SELECT 1 FROM gdelt_gkg g WHERE g.article_id = a.id)
                """);
    }

    private long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
