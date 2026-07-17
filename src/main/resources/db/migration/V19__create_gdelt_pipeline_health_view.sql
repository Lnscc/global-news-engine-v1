CREATE VIEW gdelt_pipeline_health_view AS
SELECT
    'EVENTS' AS dataset_type,
    (SELECT COUNT(*) FROM gdelt_event_payloads) AS payload_rows,
    (SELECT COUNT(*)
     FROM gdelt_event_payloads payload
     LEFT JOIN gdelt_events domain_row ON domain_row.id = payload.id
     WHERE domain_row.id IS NULL) AS pending_payload_rows,
    (SELECT COUNT(*)
     FROM gdelt_processing_errors processing_error
     WHERE processing_error.dataset_type = 'EVENTS'
       AND processing_error.resolved_at IS NULL) AS open_processing_errors,
    (SELECT COUNT(*) FROM gdelt_events) AS domain_rows
UNION ALL
SELECT
    'MENTIONS',
    (SELECT COUNT(*) FROM gdelt_mention_payloads),
    (SELECT COUNT(*)
     FROM gdelt_mention_payloads payload
     LEFT JOIN gdelt_mentions domain_row ON domain_row.id = payload.id
     WHERE domain_row.id IS NULL),
    (SELECT COUNT(*)
     FROM gdelt_processing_errors processing_error
     WHERE processing_error.dataset_type = 'MENTIONS'
       AND processing_error.resolved_at IS NULL),
    (SELECT COUNT(*) FROM gdelt_mentions)
UNION ALL
SELECT
    'GKG',
    (SELECT COUNT(*) FROM gdelt_gkg_payloads),
    (SELECT COUNT(*)
     FROM gdelt_gkg_payloads payload
     LEFT JOIN gdelt_gkg domain_row ON domain_row.id = payload.id
     WHERE domain_row.id IS NULL),
    (SELECT COUNT(*)
     FROM gdelt_processing_errors processing_error
     WHERE processing_error.dataset_type = 'GKG'
       AND processing_error.resolved_at IS NULL),
    (SELECT COUNT(*) FROM gdelt_gkg);
