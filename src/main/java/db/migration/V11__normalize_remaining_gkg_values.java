package db.migration;

import com.example.globalnewsenginev1.articles.normalization.GkgValueNormalizer;
import com.example.globalnewsenginev1.articles.normalization.NormalizedGkgValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V11__normalize_remaining_gkg_values extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V11__normalize_remaining_gkg_values.class);

    private final GkgValueNormalizer normalizer = new GkgValueNormalizer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        migrate(context.getConnection());
    }

    public void migrate(Connection connection) throws Exception {
        boolean h2 = connection.getMetaData().getDatabaseProductName().contains("H2");
        execute(connection, "DROP VIEW article_detail_view");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN persons TEXT ARRAY");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN organizations TEXT ARRAY");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN locations " + (h2 ? "JSON" : "JSONB"));
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN tone_positive_score DOUBLE PRECISION");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN tone_negative_score DOUBLE PRECISION");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN tone_polarity DOUBLE PRECISION");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN tone_activity_reference_density DOUBLE PRECISION");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN tone_self_group_reference_density DOUBLE PRECISION");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN tone_word_count INTEGER");

        String jsonCast = h2 ? "CAST(? AS JSON)" : "CAST(? AS JSONB)";
        int normalizedRecords = 0;
        int discardedLocations = 0;
        int repairedSourceDifferences = 0;
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT record.id,
                       CASE WHEN stage.id IS NULL THEN record.persons_raw ELSE stage.persons END AS persons_raw,
                       CASE WHEN stage.id IS NULL THEN record.organizations_raw ELSE stage.organizations END AS organizations_raw,
                       CASE WHEN stage.id IS NULL THEN record.locations_raw ELSE stage.locations END AS locations_raw,
                       CASE WHEN stage.id IS NULL THEN record.tone_raw ELSE stage.tone END AS tone_raw,
                       CASE WHEN stage.id IS NOT NULL AND (
                           record.persons_raw IS DISTINCT FROM stage.persons OR
                           record.organizations_raw IS DISTINCT FROM stage.organizations OR
                           record.locations_raw IS DISTINCT FROM stage.locations OR
                           record.tone_raw IS DISTINCT FROM stage.tone) THEN 1 ELSE 0 END AS source_differs
                FROM gdelt_gkg_records record
                LEFT JOIN gdelt_stage_gkg stage ON stage.id = record.source_id
                ORDER BY record.id
                """);
             ResultSet rows = select.executeQuery();
             PreparedStatement update = connection.prepareStatement("""
                UPDATE gdelt_gkg_records
                SET persons = ?, organizations = ?, locations = %s,
                    tone_value = ?, tone_positive_score = ?, tone_negative_score = ?, tone_polarity = ?,
                    tone_activity_reference_density = ?, tone_self_group_reference_density = ?, tone_word_count = ?
                WHERE id = ?
                """.formatted(jsonCast))) {
            while (rows.next()) {
                NormalizedGkgValues values = normalizer.normalize(
                        rows.getString("persons_raw"), rows.getString("organizations_raw"),
                        rows.getString("locations_raw"), rows.getString("tone_raw"));
                normalizedRecords++;
                discardedLocations += values.discardedLocationCount();
                repairedSourceDifferences += rows.getInt("source_differs");
                Array persons = connection.createArrayOf("TEXT", values.persons().toArray());
                Array organizations = connection.createArrayOf("TEXT", values.organizations().toArray());
                update.setArray(1, persons);
                update.setArray(2, organizations);
                update.setString(3, objectMapper.writeValueAsString(values.locations()));
                update.setObject(4, values.tone().value());
                update.setObject(5, values.tone().positiveScore());
                update.setObject(6, values.tone().negativeScore());
                update.setObject(7, values.tone().polarity());
                update.setObject(8, values.tone().activityReferenceDensity());
                update.setObject(9, values.tone().selfGroupReferenceDensity());
                update.setObject(10, values.tone().wordCount());
                update.setLong(11, rows.getLong("id"));
                update.addBatch();
            }
            update.executeBatch();
        }

        LOGGER.info("Normalized GKG backfill: records={}, repaired_staging_differences={}, "
                        + "discarded_location_count={}",
                normalizedRecords, repairedSourceDifferences, discardedLocations);

        execute(connection, "ALTER TABLE gdelt_gkg_records ALTER COLUMN persons SET NOT NULL");
        execute(connection, "ALTER TABLE gdelt_gkg_records ALTER COLUMN organizations SET NOT NULL");
        execute(connection, "ALTER TABLE gdelt_gkg_records ALTER COLUMN locations SET NOT NULL");
        execute(connection, "ALTER TABLE gdelt_gkg_records DROP COLUMN persons_raw");
        execute(connection, "ALTER TABLE gdelt_gkg_records DROP COLUMN organizations_raw");
        execute(connection, "ALTER TABLE gdelt_gkg_records DROP COLUMN locations_raw");
        execute(connection, "ALTER TABLE gdelt_gkg_records DROP COLUMN tone_raw");
        execute(connection, detailViewSql());
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String detailViewSql() {
        return """
                CREATE VIEW article_detail_view AS
                SELECT a.id AS article_id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
                    a.created_at AS article_created_at, a.updated_at AS article_updated_at,
                    s.id AS signal_id, s.signal_type, s.source_id, s.source_timestamp,
                    s.global_event_id, s.event_code, s.themes, s.persons, s.organizations, s.locations,
                    s.tone_value, NULL AS tone_positive_score, NULL AS tone_negative_score,
                    NULL AS tone_polarity, NULL AS tone_activity_reference_density,
                    NULL AS tone_self_group_reference_density, NULL AS tone_word_count,
                    s.created_at AS signal_created_at
                FROM articles a JOIN article_signals s ON s.article_id = a.id
                UNION ALL
                SELECT a.id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
                    a.created_at, a.updated_at, g.id, 'GKG', g.source_id, g.source_timestamp,
                    NULL, NULL, CAST(g.themes AS VARCHAR), CAST(g.persons AS VARCHAR),
                    CAST(g.organizations AS VARCHAR), CAST(g.locations AS VARCHAR),
                    g.tone_value, g.tone_positive_score, g.tone_negative_score, g.tone_polarity,
                    g.tone_activity_reference_density, g.tone_self_group_reference_density,
                    g.tone_word_count, g.created_at
                FROM articles a JOIN gdelt_gkg_records g ON g.article_id = a.id
                UNION ALL
                SELECT a.id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
                    a.created_at, a.updated_at, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
                    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
                FROM articles a
                WHERE NOT EXISTS (SELECT 1 FROM article_signals s WHERE s.article_id = a.id)
                  AND NOT EXISTS (SELECT 1 FROM gdelt_gkg_records g WHERE g.article_id = a.id)
                """;
    }
}
