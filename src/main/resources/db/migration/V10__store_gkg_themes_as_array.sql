DROP VIEW article_detail_view;

ALTER TABLE gdelt_gkg_records ADD COLUMN themes TEXT ARRAY;

UPDATE gdelt_gkg_records record
SET themes = ARRAY(
    SELECT theme.theme
    FROM gdelt_gkg_themes theme
    WHERE theme.gkg_record_id = record.id
    ORDER BY theme.position
);

ALTER TABLE gdelt_gkg_records ALTER COLUMN themes SET NOT NULL;
ALTER TABLE gdelt_gkg_records DROP COLUMN themes_raw;
DROP TABLE gdelt_gkg_themes;

CREATE VIEW article_detail_view AS
SELECT
    a.id AS article_id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
    a.created_at AS article_created_at, a.updated_at AS article_updated_at,
    s.id AS signal_id, s.signal_type, s.source_id, s.source_timestamp,
    s.global_event_id, s.event_code, s.themes, s.persons, s.organizations,
    s.locations, s.tone_value, s.tone_raw, s.created_at AS signal_created_at
FROM articles a
JOIN article_signals s ON s.article_id = a.id
UNION ALL
SELECT
    a.id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
    a.created_at, a.updated_at,
    g.id, 'GKG', g.source_id, g.source_timestamp,
    NULL, NULL, CAST(g.themes AS VARCHAR), g.persons_raw, g.organizations_raw,
    g.locations_raw, g.tone_value, g.tone_raw, g.created_at
FROM articles a
JOIN gdelt_gkg_records g ON g.article_id = a.id
UNION ALL
SELECT
    a.id, a.canonical_url, a.url_hash, a.domain, a.first_seen_at,
    a.created_at, a.updated_at,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
FROM articles a
WHERE NOT EXISTS (SELECT 1 FROM article_signals s WHERE s.article_id = a.id)
  AND NOT EXISTS (SELECT 1 FROM gdelt_gkg_records g WHERE g.article_id = a.id);
