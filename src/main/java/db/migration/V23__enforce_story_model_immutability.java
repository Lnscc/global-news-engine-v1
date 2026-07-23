package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

public class V23__enforce_story_model_immutability extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("PostgreSQL")) {
            return;
        }

        execute(connection, """
                CREATE FUNCTION story_guard_clustering_version_update()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF (to_jsonb(NEW) - 'status' - 'updated_at')
                        IS DISTINCT FROM (to_jsonb(OLD) - 'status' - 'updated_at') THEN
                        RAISE EXCEPTION 'Used clustering version definitions are immutable';
                    END IF;
                    IF NEW.status <> OLD.status AND NOT (
                        (OLD.status = 'SHADOW' AND NEW.status = 'ACTIVE')
                        OR (OLD.status = 'ACTIVE' AND NEW.status = 'RETIRED')
                        OR (OLD.status = 'RETIRED' AND NEW.status = 'ACTIVE')
                    ) THEN
                        RAISE EXCEPTION 'Invalid clustering version status transition: % -> %',
                            OLD.status, NEW.status;
                    END IF;
                    RETURN NEW;
                END
                $$
                """);
        execute(connection, """
                CREATE TRIGGER trg_story_clustering_versions_immutable
                BEFORE UPDATE ON story_clustering_versions
                FOR EACH ROW
                EXECUTE FUNCTION story_guard_clustering_version_update()
                """);

        execute(connection, """
                CREATE FUNCTION story_guard_ready_embedding()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF OLD.status = 'READY' THEN
                        RAISE EXCEPTION 'READY embedding artifacts are immutable';
                    END IF;
                    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
                END
                $$
                """);
        execute(connection, """
                CREATE TRIGGER trg_story_embedding_artifacts_immutable
                BEFORE UPDATE OR DELETE ON story_embedding_artifacts
                FOR EACH ROW
                EXECUTE FUNCTION story_guard_ready_embedding()
                """);

        execute(connection, """
                CREATE FUNCTION story_reject_change()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
                END
                $$
                """);
        for (String table : new String[]{
                "story_clustering_version_status_history",
                "story_embedding_attempts",
                "story_pair_decisions",
                "story_assignment_decisions",
                "story_lineage",
                "story_state_changes",
                "story_publish_commits"
        }) {
            execute(connection, """
                    CREATE TRIGGER trg_%s_append_only
                    BEFORE UPDATE OR DELETE ON %s
                    FOR EACH ROW
                    EXECUTE FUNCTION story_reject_change()
                    """.formatted(table, table));
        }

        execute(connection, """
                CREATE FUNCTION story_guard_snapshot()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'Story snapshots are immutable';
                END
                $$
                """);
        execute(connection, """
                CREATE TRIGGER trg_story_snapshots_immutable
                BEFORE UPDATE OR DELETE ON story_snapshots
                FOR EACH ROW
                EXECUTE FUNCTION story_guard_snapshot()
                """);

        execute(connection, """
                CREATE FUNCTION story_guard_snapshot_member()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                DECLARE
                    affected_snapshot_id BIGINT;
                BEGIN
                    affected_snapshot_id := CASE
                        WHEN TG_OP = 'DELETE' THEN OLD.snapshot_id
                        ELSE NEW.snapshot_id
                    END;
                    IF EXISTS (
                        SELECT 1 FROM story_processing_runs WHERE snapshot_id = affected_snapshot_id
                    ) THEN
                        RAISE EXCEPTION 'Snapshot members are immutable after the first run';
                    END IF;
                    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
                END
                $$
                """);
        execute(connection, """
                CREATE TRIGGER trg_story_snapshot_members_frozen
                BEFORE INSERT OR UPDATE OR DELETE ON story_snapshot_members
                FOR EACH ROW
                EXECUTE FUNCTION story_guard_snapshot_member()
                """);

        execute(connection, """
                CREATE FUNCTION story_guard_membership_history()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    IF TG_OP = 'DELETE' THEN
                        RAISE EXCEPTION 'Story memberships cannot be deleted';
                    END IF;
                    IF OLD.current_marker = 1
                        AND NEW.current_marker IS NULL
                        AND OLD.valid_to_run_id IS NULL
                        AND NEW.valid_to_run_id IS NOT NULL
                        AND OLD.ended_at IS NULL
                        AND NEW.ended_at IS NOT NULL
                        AND (to_jsonb(NEW) - 'current_marker' - 'valid_to_run_id' - 'ended_at')
                            IS NOT DISTINCT FROM
                            (to_jsonb(OLD) - 'current_marker' - 'valid_to_run_id' - 'ended_at') THEN
                        RETURN NEW;
                    END IF;
                    RAISE EXCEPTION 'Story memberships may only transition from current to historical';
                END
                $$
                """);
        execute(connection, """
                CREATE TRIGGER trg_story_memberships_historical
                BEFORE UPDATE OR DELETE ON story_memberships
                FOR EACH ROW
                EXECUTE FUNCTION story_guard_membership_history()
                """);
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
