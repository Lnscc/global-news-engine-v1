-- ART-006: URL normalization analysis queries for PostgreSQL.
-- Run against a database populated by the normal GDELT staging and article jobs.

-- Snapshot size and time range.
SELECT count(*) AS articles,
       min(first_seen_at) AS first_seen_at,
       max(first_seen_at) AS last_seen_at
FROM articles;

SELECT count(*) AS signals
FROM article_signals;

-- Exact HTTP/HTTPS variants. A result is only a candidate: matching text does
-- not prove that both schemes resolve to the same representation.
WITH scheme_variants AS (
    SELECT regexp_replace(canonical_url, '^https?://', '') AS scheme_key,
           count(*) AS variants,
           count(DISTINCT split_part(canonical_url, ':', 1)) AS schemes,
           min(domain) AS domain,
           array_agg(canonical_url ORDER BY canonical_url) AS urls
    FROM articles
    GROUP BY 1
)
SELECT domain, variants, urls
FROM scheme_variants
WHERE variants > 1 AND schemes > 1
ORDER BY variants DESC, domain, scheme_key;

-- Exact www/non-www variants while deliberately retaining the scheme, path,
-- and query. This avoids counting unrelated pages on the same host pair.
WITH host_variants AS (
    SELECT regexp_replace(
                   regexp_replace(canonical_url, '^([a-z]+://)www\\.', '\\1'),
                   '^([a-z]+://)',
                   '\\1'
           ) AS host_key,
           count(*) AS variants,
           bool_or(domain LIKE 'www.%') AS has_www,
           bool_or(domain NOT LIKE 'www.%') AS has_bare,
           array_agg(canonical_url ORDER BY canonical_url) AS urls
    FROM articles
    GROUP BY 1
)
SELECT variants, urls
FROM host_variants
WHERE variants > 1 AND has_www AND has_bare
ORDER BY variants DESC, host_key;

-- Query variants sharing scheme, host, and path. These are candidates for
-- parameter-level review, not candidates for wholesale query removal.
WITH query_variants AS (
    SELECT split_part(canonical_url, '?', 1) AS base_url,
           count(*) AS variants,
           array_agg(canonical_url ORDER BY canonical_url) AS urls
    FROM articles
    GROUP BY 1
    HAVING count(*) > 1
)
SELECT base_url, variants, urls
FROM query_variants
ORDER BY variants DESC, base_url;

-- Most frequent raw query parameter names in all three staging sources.
WITH source_urls AS (
    SELECT source_url AS raw_url FROM gdelt_events
    UNION ALL
    SELECT mention_identifier FROM gdelt_mentions
    UNION ALL
    SELECT document_identifier FROM gdelt_gkg
), parameters AS (
    SELECT lower(split_part(parameter, '=', 1)) AS name
    FROM source_urls
    CROSS JOIN LATERAL unnest(
            string_to_array(split_part(split_part(raw_url, '?', 2), '#', 1), '&')
    ) AS parameter
    WHERE raw_url LIKE '%?%'
)
SELECT name, count(*) AS occurrences
FROM parameters
GROUP BY name
ORDER BY occurrences DESC, name;

-- Raw trailing-slash prevalence. The current normalizer already removes a
-- non-root trailing slash, so articles cannot reveal pre-normalization form.
WITH source_urls AS (
    SELECT source_url AS raw_url FROM gdelt_events
    UNION ALL
    SELECT mention_identifier FROM gdelt_mentions
    UNION ALL
    SELECT document_identifier FROM gdelt_gkg
), paths AS (
    SELECT split_part(split_part(raw_url, '#', 1), '?', 1) AS path_url
    FROM source_urls
    WHERE raw_url IS NOT NULL
)
SELECT count(*) AS urls,
       count(*) FILTER (WHERE path_url ~ '/$') AS trailing_slash,
       count(*) FILTER (WHERE path_url !~ '/$') AS no_trailing_slash
FROM paths;
