package db.migration;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageGkg;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltGkgParser;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;

public class V12__add_gkg_publication_time extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V12__add_gkg_publication_time.class);

    private final GdeltGkgParser parser = new GdeltGkgParser();

    @Override
    public void migrate(Context context) throws Exception {
        migrate(context.getConnection());
    }

    public void migrate(Connection connection) throws Exception {
        execute(connection, "ALTER TABLE gdelt_stage_gkg "
                + "ADD COLUMN page_precise_pub_timestamp TIMESTAMP WITH TIME ZONE");
        execute(connection, "ALTER TABLE gdelt_gkg_records "
                + "ADD COLUMN page_precise_pub_timestamp TIMESTAMP WITH TIME ZONE");

        int stagedRows = 0;
        int acceptedPublicationTimes = 0;
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT stage.id, raw.raw_tsv
                FROM gdelt_stage_gkg stage
                JOIN gdelt_raw_gkg raw ON raw.id = stage.raw_id
                ORDER BY stage.id
                """);
             ResultSet rows = select.executeQuery();
             PreparedStatement updateStage = connection.prepareStatement("""
                UPDATE gdelt_stage_gkg SET page_precise_pub_timestamp = ? WHERE id = ?
                """);
             PreparedStatement updateRecord = connection.prepareStatement("""
                UPDATE gdelt_gkg_records SET page_precise_pub_timestamp = ? WHERE source_id = ?
                """)) {
            while (rows.next()) {
                long stageId = rows.getLong("id");
                GdeltStageGkg parsed = parser.parse(rows.getString("raw_tsv"));
                Instant publicationTime = parsed.pagePrecisePublicationTime();
                Object databaseValue = publicationTime == null ? null : publicationTime.atOffset(ZoneOffset.UTC);

                updateStage.setObject(1, databaseValue);
                updateStage.setLong(2, stageId);
                updateStage.addBatch();
                updateRecord.setObject(1, databaseValue);
                updateRecord.setLong(2, stageId);
                updateRecord.addBatch();
                stagedRows++;
                if (publicationTime != null) acceptedPublicationTimes++;
            }
            updateStage.executeBatch();
            updateRecord.executeBatch();
        }

        LOGGER.info("Backfilled GKG precise publication times: staged_rows={}, accepted={}",
                stagedRows, acceptedPublicationTimes);
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
