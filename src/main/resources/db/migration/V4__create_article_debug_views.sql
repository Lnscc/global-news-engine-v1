CREATE VIEW article_signal_summary_view AS
SELECT
    a.id AS article_id,
    a.canonical_url,
    a.domain,
    a.first_seen_at,
    COUNT(s.id) AS signal_count,
    SUM(CASE WHEN s.signal_type = 'EVENT' THEN 1 ELSE 0 END) AS event_signal_count,
    SUM(CASE WHEN s.signal_type = 'MENTION' THEN 1 ELSE 0 END) AS mention_signal_count,
    SUM(CASE WHEN s.signal_type = 'GKG' THEN 1 ELSE 0 END) AS gkg_signal_count,
    MIN(s.source_timestamp) AS earliest_signal_at,
    MAX(s.source_timestamp) AS latest_signal_at
FROM articles a
LEFT JOIN article_signals s ON s.article_id = a.id
GROUP BY a.id, a.canonical_url, a.domain, a.first_seen_at;

CREATE VIEW article_detail_view AS
SELECT
    a.id AS article_id,
    a.canonical_url,
    a.url_hash,
    a.domain,
    a.first_seen_at,
    a.created_at AS article_created_at,
    a.updated_at AS article_updated_at,
    s.id AS signal_id,
    s.signal_type,
    s.source_id,
    s.source_timestamp,
    s.global_event_id,
    s.event_code,
    s.themes,
    s.persons,
    s.organizations,
    s.locations,
    s.tone_value,
    s.tone_raw,
    s.created_at AS signal_created_at
FROM articles a
LEFT JOIN article_signals s ON s.article_id = a.id;
