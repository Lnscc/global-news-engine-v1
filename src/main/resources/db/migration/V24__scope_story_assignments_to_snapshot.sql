ALTER TABLE story_assignment_decisions
    DROP CONSTRAINT uq_story_assignment_decisions_input;

ALTER TABLE story_assignment_decisions
    ADD CONSTRAINT uq_story_assignment_decisions_input
    UNIQUE (clustering_version_id, snapshot_id, article_ref, article_input_fingerprint);
