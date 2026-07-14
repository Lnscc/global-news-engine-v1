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

public class V13__add_gkg_sharing_image extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V13__add_gkg_sharing_image.class);
    private static final String SOURCE = "GKG_SHARING_IMAGE";
    private final GdeltGkgParser parser = new GdeltGkgParser();

    @Override
    public void migrate(Context context) throws Exception {
        migrate(context.getConnection());
    }

    public void migrate(Connection connection) throws Exception {
        execute(connection, "ALTER TABLE gdelt_stage_gkg ADD COLUMN sharing_image_url VARCHAR(2048)");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN main_image_url VARCHAR(2048)");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD COLUMN main_image_source VARCHAR(32)");
        execute(connection, "ALTER TABLE gdelt_gkg_records ADD CONSTRAINT ck_gkg_main_image_source "
                + "CHECK ((main_image_url IS NULL AND main_image_source IS NULL) OR "
                + "(main_image_url IS NOT NULL AND main_image_source = '" + SOURCE + "'))");

        int stagedRows = 0;
        int acceptedImages = 0;
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT stage.id, raw.raw_tsv
                FROM gdelt_stage_gkg stage
                JOIN gdelt_raw_gkg raw ON raw.id = stage.raw_id
                ORDER BY stage.id
                """);
             ResultSet rows = select.executeQuery();
             PreparedStatement updateStage = connection.prepareStatement("""
                UPDATE gdelt_stage_gkg SET sharing_image_url = ? WHERE id = ?
                """);
             PreparedStatement updateRecord = connection.prepareStatement("""
                UPDATE gdelt_gkg_records
                SET main_image_url = ?, main_image_source = ?
                WHERE source_id = ?
                """)) {
            while (rows.next()) {
                long stageId = rows.getLong("id");
                GdeltStageGkg parsed = parser.parse(rows.getString("raw_tsv"));
                String imageUrl = parsed.sharingImageUrl();
                updateStage.setString(1, imageUrl);
                updateStage.setLong(2, stageId);
                updateStage.addBatch();
                updateRecord.setString(1, imageUrl);
                updateRecord.setString(2, imageUrl == null ? null : SOURCE);
                updateRecord.setLong(3, stageId);
                updateRecord.addBatch();
                stagedRows++;
                if (imageUrl != null) acceptedImages++;
            }
            updateStage.executeBatch();
            updateRecord.executeBatch();
        }
        LOGGER.info("Backfilled GKG sharing images: staged_rows={}, accepted={}", stagedRows, acceptedImages);
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
