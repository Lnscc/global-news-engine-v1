package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V26__single_active_story_version extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        if (context.getConnection().getMetaData().getDatabaseProductName().equalsIgnoreCase("PostgreSQL")) {
            try (var statement = context.getConnection().createStatement()) {
                statement.execute("""
                        CREATE UNIQUE INDEX uq_story_version_active ON story_clustering_versions (status)
                        WHERE status = 'ACTIVE'
                        """);
            }
        }
    }
}
