#!/usr/bin/env python3
"""Reproduce ART-031 measurements on the local PostgreSQL article dataset."""

from __future__ import annotations

import argparse
import bisect
import csv
import hashlib
import html
import json
import math
import os
import platform
import re
import statistics
import time
import unicodedata
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

import numpy as np
import psycopg
from openai import OpenAI
from sentence_transformers import SentenceTransformer

WINDOW_START = datetime(2026, 7, 12, 20, 15, tzinfo=timezone.utc)
WINDOW_END = datetime(2026, 7, 17, 20, 0, tzinfo=timezone.utc)
MODEL_ID = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
MODEL_REVISION = "e8f8c211226b894fcb81acc59f3b34ba3efd5f42"
MODEL_DIMENSION = 384
INPUT_VERSION = "art031-title-nfkc-ws-v1"
EMBEDDING_SAMPLE_SIZE = 4_000
BATCH_SIZE = 64
OPENAI_BATCH_SIZE = 256
OPENAI_MODELS = {
    "text-embedding-3-small": {"pricePerMillionInputTokensUsd": 0.02},
    "text-embedding-3-large": {"pricePerMillionInputTokensUsd": 0.13},
}
WINDOW_HOURS = (12, 24, 72)
TOP_K = (10, 50)
GENERIC_TITLES = {
    "home", "homepage", "index", "latest", "latest news", "news", "breaking news",
    "welcome", "untitled", "article", "page not found", "access denied",
    "targeted news service", "country music news - carroll broadcasting inc.",
    "deadline", "news page", "health", "npr news", "ftvlive", "gob.bo",
}

ARTICLE_SQL = """
WITH sample_articles AS (
    SELECT * FROM articles WHERE first_seen_at >= %s AND first_seen_at < %s
), event_agg AS (
    SELECT e.article_id, count(*) event_count,
           count(DISTINCT e.global_event_id) event_global_ids,
           count(DISTINCT e.event_code) FILTER (WHERE e.event_code IS NOT NULL) event_codes,
           min(e.source_timestamp) event_first, max(e.source_timestamp) event_last,
           min(e.ingested_at) event_ingested_first, max(e.ingested_at) event_ingested_last
    FROM gdelt_events e JOIN sample_articles a ON a.id=e.article_id GROUP BY e.article_id
), mention_agg AS (
    SELECT m.article_id, count(*) mention_count,
           count(DISTINCT m.global_event_id) mention_global_ids,
           min(m.source_timestamp) mention_first, max(m.source_timestamp) mention_last,
           min(m.ingested_at) mention_ingested_first, max(m.ingested_at) mention_ingested_last
    FROM gdelt_mentions m JOIN sample_articles a ON a.id=m.article_id GROUP BY m.article_id
), gkg_agg AS (
    SELECT g.article_id, count(*) gkg_count,
           bool_or(cardinality(g.themes)>0) has_themes,
           bool_or(cardinality(g.persons)>0) has_persons,
           bool_or(cardinality(g.organizations)>0) has_organizations,
           bool_or(jsonb_array_length(g.locations)>0) has_locations,
           min(g.source_timestamp) gkg_first, max(g.source_timestamp) gkg_last,
           min(g.ingested_at) gkg_ingested_first, max(g.ingested_at) gkg_ingested_last,
           min(g.source_timestamp) FILTER (WHERE nullif(btrim(g.page_title),'') IS NOT NULL) title_first,
           min(g.source_timestamp) FILTER (WHERE g.page_precise_pub_timestamp IS NOT NULL) published_signal_first,
           min(g.ingested_at) FILTER (WHERE nullif(btrim(g.page_title),'') IS NOT NULL) title_ingested_first,
           min(g.ingested_at) FILTER (WHERE g.page_precise_pub_timestamp IS NOT NULL) published_ingested_first
    FROM gdelt_gkg g JOIN sample_articles a ON a.id=g.article_id GROUP BY g.article_id
), global_id_agg AS (
    SELECT article_id,count(DISTINCT global_event_id) global_ids
    FROM (
      SELECT e.article_id,e.global_event_id FROM gdelt_events e JOIN sample_articles a ON a.id=e.article_id
      UNION ALL
      SELECT m.article_id,m.global_event_id FROM gdelt_mentions m JOIN sample_articles a ON a.id=m.article_id
    ) ids GROUP BY article_id
)
SELECT a.id, a.url_hash, a.domain, a.first_seen_at,
       title.page_title, publication.page_precise_pub_timestamp,
       coalesce(e.event_count,0) event_count, coalesce(m.mention_count,0) mention_count,
       coalesce(g.gkg_count,0) gkg_count,
       coalesce(e.event_global_ids,0) event_global_ids,
       coalesce(m.mention_global_ids,0) mention_global_ids,
       coalesce(e.event_codes,0) event_codes,
       coalesce(g.has_themes,false) has_themes, coalesce(g.has_persons,false) has_persons,
       coalesce(g.has_organizations,false) has_organizations,
       coalesce(g.has_locations,false) has_locations,
       coalesce(ids.global_ids,0) global_ids,
       greatest(e.event_last,m.mention_last,g.gkg_last) last_signal_at,
       least(e.event_ingested_first,m.mention_ingested_first,g.gkg_ingested_first) first_ingested_at,
       greatest(e.event_ingested_last,m.mention_ingested_last,g.gkg_ingested_last) last_ingested_at,
       g.title_first, g.published_signal_first, g.title_ingested_first, g.published_ingested_first
FROM sample_articles a
LEFT JOIN event_agg e ON e.article_id=a.id
LEFT JOIN mention_agg m ON m.article_id=a.id
LEFT JOIN gkg_agg g ON g.article_id=a.id
LEFT JOIN global_id_agg ids ON ids.article_id=a.id
LEFT JOIN LATERAL (
    SELECT page_title FROM gdelt_gkg x
    WHERE x.article_id=a.id AND nullif(btrim(x.page_title),'') IS NOT NULL
    ORDER BY x.source_timestamp,x.id LIMIT 1
) title ON true
LEFT JOIN LATERAL (
    SELECT page_precise_pub_timestamp FROM gdelt_gkg x
    WHERE x.article_id=a.id AND x.page_precise_pub_timestamp IS NOT NULL
    ORDER BY x.source_timestamp,x.id LIMIT 1
) publication ON true
ORDER BY a.id
"""

SIGNAL_FINGERPRINT_SQL = """
SELECT jsonb_build_object(
  'articles', (SELECT count(*) FROM articles),
  'events', (SELECT count(*) FROM gdelt_events),
  'mentions', (SELECT count(*) FROM gdelt_mentions),
  'gkg', (SELECT count(*) FROM gdelt_gkg),
  'maxArticleId', (SELECT max(id) FROM articles),
  'maxParsedAt', greatest(
      (SELECT max(parsed_at) FROM gdelt_events),
      (SELECT max(parsed_at) FROM gdelt_mentions),
      (SELECT max(parsed_at) FROM gdelt_gkg)))
"""

FEATURE_VALUE_SQL = """
WITH sample_articles AS (
  SELECT id FROM articles WHERE first_seen_at >= %s AND first_seen_at < %s
), values AS (
  SELECT 'theme' feature, x value, g.article_id
  FROM gdelt_gkg g JOIN sample_articles a ON a.id=g.article_id CROSS JOIN LATERAL unnest(g.themes) x
  UNION ALL SELECT 'person', x, g.article_id
  FROM gdelt_gkg g JOIN sample_articles a ON a.id=g.article_id CROSS JOIN LATERAL unnest(g.persons) x
  UNION ALL SELECT 'organization', x, g.article_id
  FROM gdelt_gkg g JOIN sample_articles a ON a.id=g.article_id CROSS JOIN LATERAL unnest(g.organizations) x
  UNION ALL SELECT 'location', coalesce(x->>'fullName',x->>'name',x->>'countryCode'), g.article_id
  FROM gdelt_gkg g JOIN sample_articles a ON a.id=g.article_id
  CROSS JOIN LATERAL jsonb_array_elements(g.locations) x
), frequencies AS (
  SELECT feature,value,count(DISTINCT article_id) article_frequency
  FROM values WHERE nullif(btrim(value),'') IS NOT NULL GROUP BY feature,value
)
SELECT feature,count(*) distinct_values,max(article_frequency) max_article_frequency,
       percentile_cont(0.5) within group (order by article_frequency) median_article_frequency,
       percentile_cont(0.9) within group (order by article_frequency) p90_article_frequency
FROM frequencies GROUP BY feature ORDER BY feature
"""


def title_input(value: str) -> str:
    """Deterministic embedding input: HTML decode, NFKC, collapse Unicode whitespace, trim."""
    decoded = html.unescape(value)
    normalized = unicodedata.normalize("NFKC", decoded)
    return re.sub(r"\s+", " ", normalized, flags=re.UNICODE).strip()


def title_hash(value: str) -> str:
    return hashlib.sha256(title_input(value).encode("utf-8")).hexdigest()


def normalized_title_hash(value: str) -> str:
    """Hash an already normalized embedding input without transforming it again."""
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def percentile(values: list[float], p: float) -> float | None:
    if not values:
        return None
    return float(np.percentile(np.asarray(values, dtype=np.float64), p))


def distribution(values: list[float]) -> dict:
    return {
        "count": len(values), "min": min(values) if values else None,
        "p10": percentile(values, 10), "median": percentile(values, 50),
        "p90": percentile(values, 90), "p95": percentile(values, 95),
        "p99": percentile(values, 99), "max": max(values) if values else None,
    }


def coverage(rows: list[dict], predicate) -> dict:
    count = sum(bool(predicate(row)) for row in rows)
    return {"count": count, "percent": round(100.0 * count / len(rows), 4) if rows else 0.0}


def load_rows(connection) -> list[dict]:
    with connection.cursor() as cursor:
        cursor.execute(ARTICLE_SQL, (WINDOW_START, WINDOW_END))
        names = [column.name for column in cursor.description]
        return [dict(zip(names, row)) for row in cursor.fetchall()]


def load_pairs(path: Path, articles_by_id: dict[int, dict]) -> list[dict]:
    pairs = []
    with path.open(encoding="utf-8", newline="") as handle:
        for pair in csv.DictReader(handle):
            left_id, right_id = int(pair["left_article_id"]), int(pair["right_article_id"])
            if left_id not in articles_by_id or right_id not in articles_by_id:
                raise RuntimeError(f"{pair['pair_id']} is outside the pinned sample window")
            pair["left_article_id"], pair["right_article_id"] = left_id, right_id
            pair["left_title"] = articles_by_id[left_id]["page_title"]
            pair["right_title"] = articles_by_id[right_id]["page_title"]
            pairs.append(pair)
    return pairs


def temporal_candidate_sizes(rows: list[dict]) -> dict:
    timestamps = sorted(row["first_seen_at"].timestamp() for row in rows if row["page_title"])
    result = {}
    for hours in WINDOW_HOURS:
        seconds = hours * 3600
        sizes = []
        for timestamp in timestamps:
            left = bisect.bisect_left(timestamps, timestamp - seconds)
            right = bisect.bisect_right(timestamps, timestamp + seconds)
            sizes.append(right - left - 1)
        result[str(hours)] = distribution(sizes)
    return result


def bucket_volumes(rows: list[dict]) -> dict:
    result = {}
    sample_seconds = (WINDOW_END - WINDOW_START).total_seconds()
    for minutes in (15, 60, 360):
        bucket_count = math.ceil(sample_seconds / (minutes * 60))
        volumes = [0] * bucket_count
        for row in rows:
            index = int((row["first_seen_at"] - WINDOW_START).total_seconds() // (minutes * 60))
            volumes[index] += 1
        result[f"{minutes}m"] = distribution(volumes)
    return result


def daily_breakdown(rows: list[dict]) -> list[dict]:
    days = []
    day = WINDOW_START.date()
    while day <= (WINDOW_END - timedelta(microseconds=1)).date():
        selected = [row for row in rows if row["first_seen_at"].date() == day]
        days.append({
            "date": day.isoformat(), "articles": len(selected),
            "title": coverage(selected, lambda r: r["page_title"]),
            "publishedAt": coverage(selected, lambda r: r["page_precise_pub_timestamp"] is not None),
            "EVENTS": coverage(selected, lambda r: r["event_count"] > 0),
            "MENTIONS": coverage(selected, lambda r: r["mention_count"] > 0),
            "GKG": coverage(selected, lambda r: r["gkg_count"] > 0),
        })
        day += timedelta(days=1)
    return days


def select_embedding_corpus(rows: list[dict], pairs: list[dict]) -> tuple[list[dict], list[str], int]:
    labeled_ids = {pair[key] for pair in pairs for key in ("left_article_id", "right_article_id")}
    titled = [row for row in rows if row["page_title"]]
    hashed = sorted(titled, key=lambda row: (row["url_hash"], row["id"]))[:EMBEDDING_SAMPLE_SIZE]
    selected = {row["id"]: row for row in hashed}
    selected.update({article_id: next(row for row in titled if row["id"] == article_id) for article_id in labeled_ids})
    corpus = sorted(selected.values(), key=lambda row: row["id"])
    return corpus, [title_input(row["page_title"]) for row in corpus], len(titled)


def evaluate_embeddings(corpus: list[dict], pairs: list[dict], embeddings: np.ndarray) -> dict:
    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    if np.any(norms == 0):
        raise RuntimeError("Embedding response contained a zero vector")
    embeddings = embeddings / norms
    index_by_id = {row["id"]: index for index, row in enumerate(corpus)}
    times = np.asarray([row["first_seen_at"].timestamp() for row in corpus])
    similarities = embeddings @ embeddings.T
    pair_scores = []
    recall = {f"{hours}h@{k}": [] for hours in WINDOW_HOURS for k in TOP_K}
    for pair in pairs:
        li, ri = index_by_id[pair["left_article_id"]], index_by_id[pair["right_article_id"]]
        scored_pair = dict(pair)
        scored_pair["cosine_similarity"] = float(similarities[li, ri])
        scored_pair["time_distance_hours"] = abs(times[li] - times[ri]) / 3600
        pair_scores.append(scored_pair)
        if pair["label"] != "POSITIVE":
            continue
        for hours in WINDOW_HOURS:
            for k in TOP_K:
                hits = []
                for source, target in ((li, ri), (ri, li)):
                    eligible = np.flatnonzero((np.abs(times - times[source]) <= hours * 3600) &
                                               (np.arange(len(corpus)) != source))
                    ranked = eligible[np.argsort(-similarities[source, eligible], kind="stable")[:k]]
                    hits.append(int(target in ranked))
                recall[f"{hours}h@{k}"].extend(hits)

    label_distributions = {
        label: distribution([pair["cosine_similarity"] for pair in pair_scores if pair["label"] == label])
        for label in ("POSITIVE", "HARD_NEGATIVE", "AMBIGUOUS")
    }
    positives = [pair["cosine_similarity"] for pair in pair_scores if pair["label"] == "POSITIVE"]
    negatives = [pair["cosine_similarity"] for pair in pair_scores if pair["label"] == "HARD_NEGATIVE"]
    auc = sum(positive > negative for positive in positives for negative in negatives)
    auc += 0.5 * sum(positive == negative for positive in positives for negative in negatives)
    return {
        "similarityDistributions": label_distributions,
        "separation": {
            "positiveVsHardNegativeAuc": auc / (len(positives) * len(negatives)),
            "positiveMinMinusHardNegativeMax": min(positives) - max(negatives),
        },
        "positiveRecall": {key: {"hits": sum(values), "queries": len(values),
                                  "recall": sum(values) / len(values)} for key, values in recall.items()},
        "pairs": pair_scores,
    }


def embedding_analysis(rows: list[dict], pairs: list[dict], model_path: Path) -> dict:
    corpus, texts, titled_count = select_embedding_corpus(rows, pairs)

    model = SentenceTransformer(str(model_path), device="cpu", local_files_only=True)
    started = time.perf_counter()
    failures = 0
    try:
        embeddings = model.encode(texts, batch_size=BATCH_SIZE, normalize_embeddings=True,
                                  show_progress_bar=True, convert_to_numpy=True)
    except Exception:
        failures = len(texts)
        raise
    duration = time.perf_counter() - started
    if embeddings.shape[1] != MODEL_DIMENSION:
        raise RuntimeError(f"Expected {MODEL_DIMENSION} dimensions, got {embeddings.shape[1]}")
    result = {
        "model": {"id": MODEL_ID, "revision": MODEL_REVISION, "dimension": MODEL_DIMENSION,
                  "path": str(model_path), "sentenceTransformers": "5.1.2"},
        "runtime": {"device": "cpu", "platform": platform.platform(),
                    "python": platform.python_version(), "batchSize": BATCH_SIZE,
                    "articles": len(texts), "seconds": duration,
                    "articlesPerSecond": len(texts) / duration, "failures": failures,
                    "failurePercent": 100.0 * failures / len(texts),
                    "estimatedFullTitledSampleSeconds": titled_count / (len(texts) / duration),
                    "estimatedOneMillionSeconds": 1_000_000 / (len(texts) / duration),
                    "externalApiCostUsd": 0.0},
        "sample": {"method": f"lowest {EMBEDDING_SAMPLE_SIZE} url_hash values plus all labeled articles",
                   "size": len(corpus)},
    }
    result.update(evaluate_embeddings(corpus, pairs, embeddings))
    return result


def openai_embedding_analysis(rows: list[dict], pairs: list[dict], cache_path: Path,
                              model_id: str) -> dict:
    price_per_million = OPENAI_MODELS[model_id]["pricePerMillionInputTokensUsd"]
    corpus, texts, titled_count = select_embedding_corpus(rows, pairs)
    ids = np.asarray([row["id"] for row in corpus], dtype=np.int64)
    input_hashes = [normalized_title_hash(text) for text in texts]
    cache_hit = False
    input_tokens = 0
    request_count = 0
    api_model_returned = None
    started = time.perf_counter()

    if cache_path.exists():
        with np.load(cache_path, allow_pickle=False) as cached:
            metadata = json.loads(str(cached["metadata"].item()))
            if (metadata["model"] != model_id or metadata["inputVersion"] != INPUT_VERSION or
                    metadata["articleIds"] != ids.tolist() or metadata["inputHashes"] != input_hashes):
                raise RuntimeError(f"OpenAI cache metadata does not match current inputs: {cache_path}")
            embeddings = cached["embeddings"].astype(np.float32)
            input_tokens = metadata["inputTokens"]
            request_count = metadata["requests"]
            api_model_returned = metadata["apiModelReturned"]
            original_duration = metadata["apiSeconds"]
            cache_hit = True
    else:
        client = OpenAI(max_retries=2, timeout=60.0)
        batches = []
        for offset in range(0, len(texts), OPENAI_BATCH_SIZE):
            response = client.embeddings.create(
                model=model_id,
                input=texts[offset:offset + OPENAI_BATCH_SIZE],
                encoding_format="float",
            )
            request_count += 1
            input_tokens += response.usage.prompt_tokens
            api_model_returned = response.model
            batches.extend(item.embedding for item in sorted(response.data, key=lambda item: item.index))
        embeddings = np.asarray(batches, dtype=np.float32)
        original_duration = time.perf_counter() - started
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        metadata = {
            "model": model_id, "apiModelReturned": api_model_returned,
            "inputVersion": INPUT_VERSION, "articleIds": ids.tolist(), "inputHashes": input_hashes,
            "inputTokens": input_tokens, "requests": request_count, "apiSeconds": original_duration,
        }
        np.savez_compressed(cache_path, embeddings=embeddings,
                            metadata=np.asarray(json.dumps(metadata, separators=(",", ":"))))

    elapsed = time.perf_counter() - started
    result = {
        "model": {"id": model_id, "apiModelReturned": api_model_returned,
                  "dimension": int(embeddings.shape[1]), "provider": "OpenAI",
                  "immutableRevisionAvailable": False},
        "runtime": {"device": "OpenAI API", "batchSize": OPENAI_BATCH_SIZE,
                    "articles": len(texts), "seconds": original_duration,
                    "articlesPerSecond": len(texts) / original_duration, "failures": 0,
                    "failurePercent": 0.0, "requests": request_count, "inputTokens": input_tokens,
                    "configuredMaxRetries": 2, "terminalFailures": 0, "cacheHit": cache_hit,
                    "cacheLoadSeconds": elapsed if cache_hit else None,
                    "estimatedFullTitledSampleSeconds": titled_count / (len(texts) / original_duration),
                    "estimatedOneMillionSeconds": 1_000_000 / (len(texts) / original_duration),
                    "pricePerMillionInputTokensUsd": price_per_million,
                    "externalApiCostUsd": input_tokens / 1_000_000 * price_per_million},
        "sample": {"method": f"lowest {EMBEDDING_SAMPLE_SIZE} url_hash values plus all labeled articles",
                   "size": len(corpus), "identicalToLocalBaseline": True},
        "vectorNormalization": "OpenAI vectors are L2-normalized; defensively renormalized before scoring",
    }
    result.update(evaluate_embeddings(corpus, pairs, embeddings))
    return result


def analyze(connection, rows: list[dict], pairs_path: Path, model_path: Path,
            include_openai: bool, openai_cache: Path,
            include_openai_large: bool, openai_large_cache: Path) -> dict:
    articles_by_id = {row["id"]: row for row in rows}
    pairs = load_pairs(pairs_path, articles_by_id)
    titles = [title_input(row["page_title"]) for row in rows if row["page_title"]]
    folded = [title.casefold() for title in titles]
    counts = Counter(folded)
    publication_offsets = [
        (row["page_precise_pub_timestamp"] - row["first_seen_at"]).total_seconds() / 3600
        for row in rows if row["page_precise_pub_timestamp"] is not None
    ]
    signal_lateness = [
        (row["last_signal_at"] - row["first_seen_at"]).total_seconds() / 3600
        for row in rows if row["last_signal_at"] is not None
    ]
    title_lateness = [
        (row["title_first"] - row["first_seen_at"]).total_seconds() / 3600
        for row in rows if row["title_first"] is not None
    ]
    publication_metadata_lateness = [
        (row["published_signal_first"] - row["first_seen_at"]).total_seconds() / 3600
        for row in rows if row["published_signal_first"] is not None
    ]
    ingestion_spans = [
        (row["last_ingested_at"] - row["first_ingested_at"]).total_seconds() / 3600
        for row in rows if row["first_ingested_at"] is not None and row["last_ingested_at"] is not None
    ]
    title_ingestion_lateness = [
        (row["title_ingested_first"] - row["first_ingested_at"]).total_seconds() / 3600
        for row in rows if row["title_ingested_first"] is not None and row["first_ingested_at"] is not None
    ]
    publication_ingestion_lateness = [
        (row["published_ingested_first"] - row["first_ingested_at"]).total_seconds() / 3600
        for row in rows if row["published_ingested_first"] is not None and row["first_ingested_at"] is not None
    ]
    with connection.cursor() as cursor:
        cursor.execute(SIGNAL_FINGERPRINT_SQL)
        fingerprint = cursor.fetchone()[0]
        cursor.execute(FEATURE_VALUE_SQL, (WINDOW_START, WINDOW_END))
        feature_values = [dict(zip([column.name for column in cursor.description], row))
                          for row in cursor.fetchall()]

    embeddings = embedding_analysis(rows, pairs, model_path)
    if include_openai:
        embeddings["openaiComparison"] = openai_embedding_analysis(
            rows, pairs, openai_cache, "text-embedding-3-small")
    if include_openai_large:
        embeddings["openaiLargeComparison"] = openai_embedding_analysis(
            rows, pairs, openai_large_cache, "text-embedding-3-large")

    return {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sample": {"start": WINDOW_START.isoformat(), "endExclusive": WINDOW_END.isoformat(),
                   "selection": "all articles with first_seen_at in the half-open UTC interval",
                   "articles": len(rows), "domains": len({row['domain'] for row in rows}),
                   "databaseFingerprint": fingerprint},
        "titleInput": {"version": INPUT_VERSION,
                       "normalization": "HTML5 entity decode; Unicode NFKC; collapse Unicode whitespace to U+0020; trim; preserve case and punctuation",
                       "hash": "lowercase hexadecimal SHA-256 of UTF-8 normalized title",
                       "example": {"raw": "  Caf&eacute;\n  update  ",
                                   "input": title_input("  Caf&eacute;\n  update  "),
                                   "sha256": title_hash("  Caf&eacute;\n  update  ")}},
        "coverage": {
            "title": coverage(rows, lambda r: r["page_title"]),
            "publishedAt": coverage(rows, lambda r: r["page_precise_pub_timestamp"] is not None),
            "firstSeenAt": coverage(rows, lambda r: r["first_seen_at"] is not None),
            "EVENTS": coverage(rows, lambda r: r["event_count"] > 0),
            "MENTIONS": coverage(rows, lambda r: r["mention_count"] > 0),
            "GKG": coverage(rows, lambda r: r["gkg_count"] > 0),
            "themes": coverage(rows, lambda r: r["has_themes"]),
            "persons": coverage(rows, lambda r: r["has_persons"]),
            "organizations": coverage(rows, lambda r: r["has_organizations"]),
            "locations": coverage(rows, lambda r: r["has_locations"]),
        },
        "titles": {"nonEmpty": len(titles), "uniqueNormalized": len(counts),
                   "duplicateRows": sum(count for count in counts.values() if count > 1),
                   "duplicateGroups": sum(count > 1 for count in counts.values()),
                   "excessDuplicateRows": sum(count - 1 for count in counts.values() if count > 1),
                   "generic": sum(value in GENERIC_TITLES for value in folded),
                   "shortUnderFourTokens": sum(len(value.split()) < 4 for value in titles),
                   "topDuplicates": counts.most_common(20)},
        "time": {"publishedMinusFirstSeenHours": distribution(publication_offsets),
                 "absolutePublishedFirstSeenHours": distribution([abs(value) for value in publication_offsets]),
                 "publishedMoreThan24HoursBeforeFirstSeen": coverage(
                     [dict(value=value) for value in publication_offsets], lambda r: r["value"] < -24),
                 "publishedMoreThan7DaysBeforeFirstSeen": coverage(
                     [dict(value=value) for value in publication_offsets], lambda r: r["value"] < -168),
                 "publishedAfterFirstSeen": coverage(
                     [dict(value=value) for value in publication_offsets], lambda r: r["value"] > 0),
                 "lastSignalMinusFirstSeenHours": distribution(signal_lateness),
                 "titleAvailableMinusFirstSeenHours": distribution(title_lateness),
                 "publicationMetadataMinusFirstSeenHours": distribution(publication_metadata_lateness),
                 "ingestionSpanHours": distribution(ingestion_spans),
                 "titleIngestionDelayHours": distribution(title_ingestion_lateness),
                 "publicationMetadataIngestionDelayHours": distribution(publication_ingestion_lateness)},
        "lateArrival": {
            "anySignalAfter1Hour": coverage([dict(value=value) for value in ingestion_spans],
                                             lambda r: r["value"] > 1),
            "anySignalAfter24Hours": coverage([dict(value=value) for value in ingestion_spans],
                                               lambda r: r["value"] > 24),
            "titleAfter24Hours": coverage([dict(value=value) for value in title_ingestion_lateness],
                                           lambda r: r["value"] > 24),
            "publicationMetadataAfter24Hours": coverage(
                [dict(value=value) for value in publication_ingestion_lateness], lambda r: r["value"] > 24)},
        "signalMultiplicity": {
            "eventRows": distribution([row["event_count"] for row in rows]),
            "mentionRows": distribution([row["mention_count"] for row in rows]),
            "gkgRows": distribution([row["gkg_count"] for row in rows]),
            "eventGlobalIds": distribution([row["event_global_ids"] for row in rows]),
            "mentionGlobalIds": distribution([row["mention_global_ids"] for row in rows]),
            "eventCodes": distribution([row["event_codes"] for row in rows]),
            "globalIdsAcrossEventsAndMentions": distribution([row["global_ids"] for row in rows]),
            "multipleEventGlobalIds": coverage(rows, lambda r: r["event_global_ids"] > 1),
            "multipleMentionGlobalIds": coverage(rows, lambda r: r["mention_global_ids"] > 1),
            "multipleEventCodes": coverage(rows, lambda r: r["event_codes"] > 1),
            "multipleGlobalIdsAcrossEventsAndMentions": coverage(rows, lambda r: r["global_ids"] > 1)},
        "featureValueFrequency": feature_values,
        "dailyBreakdown": daily_breakdown(rows),
        "articleVolumeByBucket": bucket_volumes(rows),
        "temporalCandidateSizes": temporal_candidate_sizes(rows),
        "embeddings": embeddings,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dsn", default=os.environ.get("ART031_DSN", "postgresql://gne:gne@localhost:5432/gne"))
    parser.add_argument("--pairs", type=Path, default=Path("docs/analysis/ART-031-pairs.csv"))
    parser.add_argument("--model-path", type=Path, default=Path(".art031/models/paraphrase-multilingual-MiniLM-L12-v2"))
    parser.add_argument("--openai", action="store_true", help="also evaluate text-embedding-3-small")
    parser.add_argument("--openai-cache", type=Path,
                        default=Path(".art031/openai-text-embedding-3-small.npz"))
    parser.add_argument("--openai-large", action="store_true",
                        help="also evaluate text-embedding-3-large")
    parser.add_argument("--openai-large-cache", type=Path,
                        default=Path(".art031/openai-text-embedding-3-large.npz"))
    parser.add_argument("--output", type=Path, default=Path("docs/analysis/ART-031-results.json"))
    args = parser.parse_args()
    if not args.model_path.exists():
        raise SystemExit(f"Missing pinned model at {args.model_path}")
    with psycopg.connect(args.dsn) as connection:
        result = analyze(connection, load_rows(connection), args.pairs, args.model_path,
                         args.openai, args.openai_cache,
                         args.openai_large, args.openai_large_cache)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, default=str, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}")


if __name__ == "__main__":
    main()
