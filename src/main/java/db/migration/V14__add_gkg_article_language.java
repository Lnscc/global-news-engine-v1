package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Reserves the identity of a migration that was applied during development and
 * later superseded when the GDELT tables were consolidated.
 *
 * <p>The former migration added language columns to tables that are no longer
 * part of the current model. Keeping its Flyway identity allows databases that
 * already recorded version 14 to validate without replaying obsolete schema
 * changes. Fresh databases intentionally perform no work at this version.</p>
 */
public class V14__add_gkg_article_language extends BaseJavaMigration {

    @Override
    public void migrate(Context context) {
        // Compatibility marker for the historical V14 migration.
    }
}
