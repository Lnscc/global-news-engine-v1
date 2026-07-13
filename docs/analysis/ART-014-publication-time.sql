WITH extracted AS (
    SELECT
        stage.id AS stage_id,
        stage.source_collection_identifier,
        stage.document_date,
        stage.source_timestamp,
        record.article_id,
        btrim(substring(
            split_part(raw.raw_tsv, E'\t', 27)
            FROM '(?is)<PAGE_PRECISEPUBTIMESTAMP>([^<]*)</PAGE_PRECISEPUBTIMESTAMP>'
        )) AS raw_value
    FROM gdelt_stage_gkg stage
    JOIN gdelt_raw_gkg raw ON raw.id = stage.raw_id
    LEFT JOIN gdelt_gkg_records record ON record.source_id = stage.id
), parsed AS (
    SELECT *,
           CASE WHEN raw_value ~ '^[0-9]{14}$'
                THEN to_timestamp(raw_value, 'YYYYMMDDHH24MISS')
           END AS published_at
    FROM extracted
), article_candidates AS (
    SELECT article_id,
           COUNT(*) FILTER (
               WHERE published_at <= document_date + INTERVAL '15 minutes'
           ) AS candidate_count,
           COUNT(DISTINCT published_at) FILTER (
               WHERE published_at <= document_date + INTERVAL '15 minutes'
           ) AS distinct_candidate_count
    FROM parsed
    WHERE article_id IS NOT NULL
    GROUP BY article_id
)
SELECT
    COUNT(*) AS staging_rows,
    COUNT(*) FILTER (WHERE raw_value IS NOT NULL AND raw_value <> '') AS tagged_rows,
    ROUND(100.0 * COUNT(*) FILTER (WHERE raw_value IS NOT NULL AND raw_value <> '') / COUNT(*), 2)
        AS coverage_pct,
    COUNT(*) FILTER (
        WHERE raw_value IS NOT NULL AND raw_value <> '' AND raw_value !~ '^[0-9]{14}$'
    ) AS non_compact_format,
    MIN(published_at) AS earliest,
    MAX(published_at) AS latest,
    COUNT(*) FILTER (
        WHERE published_at > document_date + INTERVAL '15 minutes'
    ) AS rejected_future,
    COUNT(*) FILTER (
        WHERE published_at <= document_date + INTERVAL '15 minutes'
    ) AS accepted,
    (SELECT COUNT(*) FROM article_candidates WHERE candidate_count > 1)
        AS articles_multiple_candidates,
    (SELECT COUNT(*) FROM article_candidates WHERE distinct_candidate_count > 1)
        AS articles_conflicting_candidates
FROM parsed;

