ALTER TABLE story_clustering_versions
    ADD COLUMN input_eligibility_rule_version VARCHAR(128) NOT NULL DEFAULT 'all-articles-v1';

ALTER TABLE story_article_inputs
    ADD COLUMN input_disposition VARCHAR(16) NOT NULL DEFAULT 'INCLUDE';

ALTER TABLE story_article_inputs
    ADD COLUMN exclusion_reason VARCHAR(64);

ALTER TABLE story_article_inputs
    ADD CONSTRAINT ck_story_article_inputs_disposition
        CHECK (input_disposition IN ('INCLUDE', 'EXCLUDE'));

ALTER TABLE story_article_inputs
    ADD CONSTRAINT ck_story_article_inputs_exclusion CHECK (
        (input_disposition = 'INCLUDE' AND exclusion_reason IS NULL)
        OR (input_disposition = 'EXCLUDE'
            AND exclusion_reason IS NOT NULL
            AND embedding_status = 'NOT_REQUIRED'
            AND embedding_artifact_id IS NULL)
    );

ALTER TABLE story_article_inputs
    DROP CONSTRAINT ck_story_article_inputs_title_payload;

ALTER TABLE story_article_inputs
    ADD CONSTRAINT ck_story_article_inputs_title_payload CHECK (
        (title_usability = 'USABLE'
            AND normalized_title IS NOT NULL
            AND title_input_hash IS NOT NULL
            AND (embedding_status <> 'NOT_REQUIRED' OR input_disposition = 'EXCLUDE'))
        OR (title_usability IN ('TITLE_MISSING', 'TITLE_GENERIC')
            AND title_input_hash IS NULL
            AND embedding_status = 'NOT_REQUIRED'
            AND embedding_artifact_id IS NULL)
    );

INSERT INTO story_clustering_versions (
    version_key,
    title_normalization_version,
    generic_title_rule_version,
    embedding_model_id,
    embedding_model_version,
    embedding_dimension,
    candidate_time_rule_version,
    candidate_window_hours,
    candidate_similarity_threshold,
    candidate_search_mode,
    pair_decision_rule_version,
    component_rule_version,
    feature_normalization_versions,
    status,
    created_at,
    updated_at,
    input_eligibility_rule_version
)
SELECT
    'story-mvp-title-embedding-24h-v1.1.0',
    title_normalization_version,
    generic_title_rule_version,
    embedding_model_id,
    embedding_model_version,
    embedding_dimension,
    candidate_time_rule_version,
    candidate_window_hours,
    candidate_similarity_threshold,
    candidate_search_mode,
    pair_decision_rule_version,
    component_rule_version,
    feature_normalization_versions,
    'SHADOW',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'navigation-service-exact-path-title-v1'
FROM story_clustering_versions
WHERE version_key = 'story-mvp-title-embedding-24h-v1.0.0';

INSERT INTO story_clustering_version_status_history (
    clustering_version_id,
    previous_status,
    new_status,
    reason,
    changed_at
)
SELECT id, NULL, 'SHADOW', 'ART-047 navigation/service input exclusion', created_at
FROM story_clustering_versions
WHERE version_key = 'story-mvp-title-embedding-24h-v1.1.0';
