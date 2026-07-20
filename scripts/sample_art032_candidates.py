#!/usr/bin/env python3
"""Export difficult ART-032 review candidates from the real local article dataset.

This script never assigns reference labels. Similarity and structured signals are
sampling aids only; a reviewer must inspect sources and choose SAME_STORY,
DIFFERENT_STORY, or UNCERTAIN in the versioned corpus.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import psycopg
from openai import OpenAI

from art032_evaluation import normalize_title, title_hash

MODEL = "text-embedding-3-small"
MODEL_VERSION = "openai:text-embedding-3-small@2026-07-20"
INPUT_VERSION = "art031-title-nfkc-ws-v1"
BATCH_SIZE = 256
TOKEN_RE = re.compile(r"[^\W_]+", re.UNICODE)

ARTICLE_SQL = """
WITH sample AS (
  SELECT * FROM articles WHERE first_seen_at >= %s AND first_seen_at < %s
), title AS (
  SELECT DISTINCT ON (g.article_id) g.article_id,g.page_title,g.page_precise_pub_timestamp,
         g.persons,g.organizations,g.locations
  FROM gdelt_gkg g JOIN sample a ON a.id=g.article_id
  WHERE nullif(btrim(g.page_title),'') IS NOT NULL
  ORDER BY g.article_id,g.source_timestamp,g.id
), event_ids AS (
  SELECT article_id,array_agg(DISTINCT global_event_id) global_event_ids
  FROM (
    SELECT e.article_id,e.global_event_id FROM gdelt_events e JOIN sample a ON a.id=e.article_id
    UNION ALL
    SELECT m.article_id,m.global_event_id FROM gdelt_mentions m JOIN sample a ON a.id=m.article_id
  ) x GROUP BY article_id
)
SELECT a.id,a.url_hash,a.canonical_url,a.domain,a.first_seen_at,
       t.page_title,t.page_precise_pub_timestamp,t.persons,t.organizations,t.locations,
       coalesce(e.global_event_ids,'{}') global_event_ids
FROM sample a JOIN title t ON t.article_id=a.id
LEFT JOIN event_ids e ON e.article_id=a.id
ORDER BY a.url_hash,a.id
"""


def stable_ref(url_hash: str) -> str:
    return f"article-{url_hash[:16]}"


def json_value(value):
    if isinstance(value, datetime):
        return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
    return value


def load_articles(connection, start: datetime, end: datetime) -> list[dict]:
    with connection.cursor() as cursor:
        cursor.execute(ARTICLE_SQL, (start, end))
        names = [column.name for column in cursor.description]
        rows = [dict(zip(names, row)) for row in cursor.fetchall()]
    for row in rows:
        row["ref"] = stable_ref(row["url_hash"])
        row["title_input"] = normalize_title(row["page_title"])
        row["title_input_hash"] = title_hash(row["page_title"])
        row["persons"] = sorted(row["persons"] or [])
        row["organizations"] = sorted(row["organizations"] or [])
        row["global_event_ids"] = sorted(row["global_event_ids"] or [])
    return rows


def choose_embedding_sample(rows: list[dict], size: int) -> list[dict]:
    """Use a deterministic hash sample plus duplicate-title and sparse-title strata."""
    chosen = {row["id"]: row for row in rows[:size]}
    by_title: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        by_title[row["title_input"].casefold()].append(row)
    duplicate_groups = sorted((group for group in by_title.values()
                               if len({row["domain"] for row in group}) >= 3),
                              key=lambda group: (-len(group), group[0]["url_hash"]))
    for group in duplicate_groups[:40]:
        for row in group[:5]:
            chosen[row["id"]] = row
    for row in rows:
        if len(TOKEN_RE.findall(row["title_input"])) < 4:
            chosen[row["id"]] = row
            if sum(len(TOKEN_RE.findall(item["title_input"])) < 4 for item in chosen.values()) >= 100:
                break
    return sorted(chosen.values(), key=lambda row: (row["first_seen_at"], row["url_hash"]))


def load_or_create_embeddings(rows: list[dict], cache: Path) -> tuple[np.ndarray, dict]:
    hashes = np.array([row["title_input_hash"] for row in rows])
    if cache.exists():
        data = np.load(cache, allow_pickle=False)
        if data["model"].item() == MODEL and np.array_equal(data["hashes"], hashes):
            return data["vectors"], {"cacheHit": True, "inputTokens": int(data["input_tokens"])}
    client = OpenAI(max_retries=2)
    vectors: list[list[float]] = []
    input_tokens = 0
    for start in range(0, len(rows), BATCH_SIZE):
        response = client.embeddings.create(
            model=MODEL,
            input=[row["title_input"] for row in rows[start:start + BATCH_SIZE]],
        )
        vectors.extend(item.embedding for item in response.data)
        input_tokens += response.usage.prompt_tokens
    matrix = np.asarray(vectors, dtype=np.float32)
    matrix /= np.linalg.norm(matrix, axis=1, keepdims=True)
    cache.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(cache, model=np.array(MODEL), hashes=hashes, vectors=matrix,
                        input_tokens=np.array(input_tokens))
    return matrix, {"cacheHit": False, "inputTokens": input_tokens}


def lexical_similarity(left: str, right: str) -> float:
    left_tokens = set(token.casefold() for token in TOKEN_RE.findall(left))
    right_tokens = set(token.casefold() for token in TOKEN_RE.findall(right))
    return len(left_tokens & right_tokens) / len(left_tokens | right_tokens) if left_tokens | right_tokens else 0.0


def overlap(left, right) -> list:
    return sorted(set(left or []) & set(right or []))


def candidate_record(left: dict, right: dict, cosine: float, rank: int) -> dict:
    return {
        "id": "candidate-" + hashlib.sha256(
            "|".join(sorted((left["url_hash"], right["url_hash"]))).encode()).hexdigest()[:16],
        "left": left["ref"], "right": right["ref"],
        "signals": {
            "embeddingCosine": round(float(cosine), 6),
            "embeddingRank": rank,
            "lexicalJaccard": round(lexical_similarity(left["title_input"], right["title_input"]), 6),
            "hoursApart": round(abs((left["first_seen_at"] - right["first_seen_at"]).total_seconds()) / 3600, 4),
            "sameDomain": left["domain"] == right["domain"],
            "sameNormalizedTitle": left["title_input"].casefold() == right["title_input"].casefold(),
            "sharedGlobalEventIds": overlap(left["global_event_ids"], right["global_event_ids"]),
            "sharedPersons": overlap(left["persons"], right["persons"]),
            "sharedOrganizations": overlap(left["organizations"], right["organizations"]),
        },
    }


def top_k_candidates(rows: list[dict], vectors: np.ndarray, window_hours: int,
                     top_k: int) -> list[dict]:
    timestamps = np.array([row["first_seen_at"].timestamp() for row in rows])
    window_seconds = window_hours * 3600
    unique: dict[tuple[int, int], dict] = {}
    for index, row in enumerate(rows):
        eligible = np.flatnonzero((np.abs(timestamps - timestamps[index]) <= window_seconds)
                                  & (np.arange(len(rows)) != index))
        if not len(eligible):
            continue
        scores = vectors[eligible] @ vectors[index]
        count = min(top_k, len(eligible))
        top_positions = np.argpartition(scores, -count)[-count:]
        ordered = top_positions[np.argsort(scores[top_positions])[::-1]]
        for rank, position in enumerate(ordered, 1):
            other_index = int(eligible[position])
            key = tuple(sorted((index, other_index)))
            record = candidate_record(row, rows[other_index], float(scores[position]), rank)
            previous = unique.get(key)
            if previous is None or rank < previous["signals"]["embeddingRank"]:
                unique[key] = record
    return list(unique.values())


def top_k_lexical_candidates(rows: list[dict], corpus_refs: set[str], window_hours: int,
                             top_k: int) -> list[dict]:
    timestamps = [row["first_seen_at"].timestamp() for row in rows]
    tokens = [set(token.casefold() for token in TOKEN_RE.findall(row["title_input"])) for row in rows]
    unique: dict[tuple[str, str], dict] = {}
    for index, row in enumerate(rows):
        if row["ref"] not in corpus_refs:
            continue
        scored = []
        for other_index, other in enumerate(rows):
            if index == other_index or abs(timestamps[index] - timestamps[other_index]) > window_hours * 3600:
                continue
            union = tokens[index] | tokens[other_index]
            score = len(tokens[index] & tokens[other_index]) / len(union) if union else 0.0
            scored.append((score, other["url_hash"], other_index))
        scored.sort(key=lambda item: (-item[0], item[1]))
        for rank, (score, _, other_index) in enumerate(scored[:top_k], 1):
            other = rows[other_index]
            if other["ref"] not in corpus_refs:
                continue
            key = tuple(sorted((row["ref"], other["ref"])))
            record = {"left": row["ref"], "right": other["ref"],
                      "lexicalJaccard": round(score, 6), "lexicalRank": rank}
            previous = unique.get(key)
            if previous is None or rank < previous["lexicalRank"]:
                unique[key] = record
    return list(unique.values())


def article_export(row: dict) -> dict:
    return {
        "ref": row["ref"], "urlHash": row["url_hash"], "canonicalUrl": row["canonical_url"],
        "domain": row["domain"], "title": row["page_title"],
        "titleInputHash": row["title_input_hash"],
        "firstSeenAt": json_value(row["first_seen_at"]),
        "publishedAt": json_value(row["page_precise_pub_timestamp"]),
    }


def stratify(candidates: list[dict], limit: int) -> dict[str, list[dict]]:
    ordered = sorted(candidates, key=lambda item: (-item["signals"]["embeddingCosine"], item["id"]))
    hard = [item for item in ordered
            if item["signals"]["embeddingCosine"] >= 0.65
            and item["signals"]["lexicalJaccard"] <= 0.50
            and not item["signals"]["sameNormalizedTitle"]
            and not item["signals"]["sharedGlobalEventIds"]
            and (item["signals"]["sharedPersons"] or item["signals"]["sharedOrganizations"])]
    conflicts = [item for item in ordered
                 if (item["signals"]["sharedGlobalEventIds"] and item["signals"]["embeddingCosine"] < 0.72)
                 or (not item["signals"]["sharedGlobalEventIds"] and item["signals"]["embeddingCosine"] >= 0.84)]
    overlap_band = [item for item in ordered if 0.50 <= item["signals"]["embeddingCosine"] <= 0.85]
    low_positive = sorted((item for item in candidates
                           if item["signals"]["sameNormalizedTitle"]
                           or item["signals"]["lexicalJaccard"] >= 0.8),
                          key=lambda item: (item["signals"]["embeddingCosine"], item["id"]))
    return {"hardHighSimilarity": hard[:limit], "signalConflicts": conflicts[:limit],
            "similarityOverlap": overlap_band[:limit], "lowSimilarityPositive": low_positive[:limit]}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dsn", default=os.environ.get("ART032_DSN", "postgresql://gne:gne@localhost:5432/gne"))
    parser.add_argument("--start", default="2026-07-12T20:15:00+00:00")
    parser.add_argument("--end", default="2026-07-20T18:45:00+00:00")
    parser.add_argument("--sample-size", type=int, default=4000)
    parser.add_argument("--corpus", type=Path, default=Path("docs/analysis/ART-032-corpus.json"))
    parser.add_argument("--window-hours", type=int, default=24)
    parser.add_argument("--top-k", type=int, default=50)
    parser.add_argument("--stratum-limit", type=int, default=250)
    parser.add_argument("--cache", type=Path, default=Path(".art032/openai-text-embedding-3-small.npz"))
    parser.add_argument("--output", type=Path, default=Path(".art032/review-candidates.json"))
    parser.add_argument("--population-output", type=Path,
                        default=Path(".art032/candidate-population.json"))
    args = parser.parse_args()
    start, end = datetime.fromisoformat(args.start), datetime.fromisoformat(args.end)
    with psycopg.connect(args.dsn) as connection:
        population = load_articles(connection, start, end)
    rows = choose_embedding_sample(population, args.sample_size)
    corpus_refs: set[str] = set()
    if args.corpus.exists():
        corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
        corpus_refs = {article["ref"] for article in corpus["articles"]}
        rows_by_ref = {row["ref"]: row for row in rows}
        for row in population:
            if row["ref"] in corpus_refs:
                rows_by_ref[row["ref"]] = row
        rows = sorted(rows_by_ref.values(), key=lambda row: (row["first_seen_at"], row["url_hash"]))
    vectors, usage = load_or_create_embeddings(rows, args.cache)
    candidates = top_k_candidates(rows, vectors, args.window_hours, args.top_k)
    lexical_candidates = top_k_lexical_candidates(
        rows, corpus_refs, args.window_hours, args.top_k) if corpus_refs else []
    strata = stratify(candidates, args.stratum_limit)
    used_refs = {ref for values in strata.values() for item in values for ref in (item["left"], item["right"])}
    output = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "warning": "Candidate signals are not reference labels; every pair requires independent review.",
        "provenance": {"start": start.isoformat(), "endExclusive": end.isoformat(),
                       "populationArticlesWithTitle": len(population), "embeddingSample": len(rows)},
        "baseline": {"model": MODEL, "modelVersion": MODEL_VERSION,
                     "titleInputVersion": INPUT_VERSION, "windowHours": args.window_hours,
                     "topK": args.top_k, **usage},
        "candidatePopulation": [
            {"ref": row["ref"], "urlHash": row["url_hash"],
             "titleInputHash": row["title_input_hash"],
             "firstSeenAt": json_value(row["first_seen_at"]), "title": row["page_title"]}
            for row in rows
        ],
        "articles": [article_export(row) for row in rows if row["ref"] in used_refs],
        "strata": strata,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    args.population_output.write_text(json.dumps({
        "model": MODEL, "modelVersion": MODEL_VERSION,
        "titleInputVersion": INPUT_VERSION,
        "candidatePopulation": output["candidatePopulation"],
        "corpusCandidates": [item for item in candidates
                             if item["left"] in corpus_refs and item["right"] in corpus_refs],
        "corpusLexicalCandidates": lexical_candidates,
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}: {len(rows)} embedded articles, {len(candidates)} unique candidates")


if __name__ == "__main__":
    main()
