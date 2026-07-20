#!/usr/bin/env python3
"""Run lexical and embedding baselines against the pinned ART-032 corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from openai import OpenAI

from art032_evaluation import evaluate, normalize_title
from sample_art032_candidates import MODEL, MODEL_VERSION, lexical_similarity

BATCH_SIZE = 256


def embed_articles(articles: list[dict], cache_path: Path) -> np.ndarray:
    hashes = np.array([article["titleInputHash"] for article in articles])
    if cache_path.exists():
        cache = np.load(cache_path, allow_pickle=False)
        if cache["model"].item() == MODEL and np.array_equal(cache["hashes"], hashes):
            return cache["vectors"]
    client = OpenAI(max_retries=2)
    vectors = []
    for start in range(0, len(articles), BATCH_SIZE):
        response = client.embeddings.create(
            model=MODEL,
            input=[normalize_title(article["title"]) for article in articles[start:start + BATCH_SIZE]],
        )
        vectors.extend(item.embedding for item in response.data)
    matrix = np.asarray(vectors, dtype=np.float32)
    matrix /= np.linalg.norm(matrix, axis=1, keepdims=True)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    np.savez_compressed(cache_path, model=np.array(MODEL), hashes=hashes, vectors=matrix)
    return matrix


def candidate_pairs(population_metadata_path: Path) -> tuple[set[tuple[str, str]], set[tuple[str, str]]]:
    metadata = json.loads(population_metadata_path.read_text(encoding="utf-8"))
    embedding = {tuple(sorted((item["left"], item["right"])))
                 for item in metadata["corpusCandidates"]}
    lexical = {tuple(sorted((item["left"], item["right"])))
               for item in metadata["corpusLexicalCandidates"]}
    return embedding, lexical


def predictions(corpus: dict, vectors: np.ndarray, embedding_candidates: set[tuple[str, str]],
                lexical_candidates: set[tuple[str, str]],
                embedding_threshold: float, lexical_threshold: float) -> tuple[dict, dict]:
    articles = corpus["articles"]
    indices = {article["ref"]: index for index, article in enumerate(articles)}
    lexical_pairs, embedding_pairs = [], []
    for pair in corpus["pairs"]:
        left, right = articles[indices[pair["left"]]], articles[indices[pair["right"]]]
        key = tuple(sorted((pair["left"], pair["right"])))
        embedding_candidate = key in embedding_candidates
        lexical_candidate = key in lexical_candidates
        lexical = lexical_similarity(left["title"], right["title"])
        cosine = float(vectors[indices[pair["left"]]] @ vectors[indices[pair["right"]]])
        lexical_pairs.append({"pairId": pair["id"], "sameStory": lexical_candidate and lexical >= lexical_threshold,
                              "candidate": lexical_candidate, "score": round(lexical, 6)})
        embedding_pairs.append({"pairId": pair["id"], "sameStory": embedding_candidate and cosine >= embedding_threshold,
                                "candidate": embedding_candidate, "score": round(cosine, 6)})
    return ({"pairs": lexical_pairs}, {"pairs": embedding_pairs})


def best_threshold(corpus: dict, vectors: np.ndarray,
                   embedding_candidates: set[tuple[str, str]],
                   lexical_candidates: set[tuple[str, str]],
                   baseline: str) -> tuple[float, dict]:
    clear = [pair for pair in corpus["pairs"]
             if pair["split"] == "calibration" and pair["label"] != "UNCERTAIN"]
    articles = corpus["articles"]
    indices = {article["ref"]: index for index, article in enumerate(articles)}
    scores = []
    for pair in clear:
        left, right = indices[pair["left"]], indices[pair["right"]]
        if baseline == "embedding":
            score = float(vectors[left] @ vectors[right])
        else:
            score = lexical_similarity(articles[left]["title"], articles[right]["title"])
        scores.append(score)
    thresholds = sorted({0.0, 1.0, *(round(score, 6) for score in scores)})
    best = None
    for threshold in thresholds:
        lexical_threshold = threshold if baseline == "lexical" else 2.0
        embedding_threshold = threshold if baseline == "embedding" else 2.0
        lexical, embedding = predictions(corpus, vectors, embedding_candidates, lexical_candidates,
                                         embedding_threshold, lexical_threshold)
        result = evaluate(corpus, embedding if baseline == "embedding" else lexical, "calibration")
        key = (result["pairwiseF1"], result["pairwisePrecision"], threshold)
        if best is None or key > best[0]:
            best = (key, threshold, result)
    return best[1], best[2]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus", type=Path, nargs="?", default=Path("docs/analysis/ART-032-corpus.json"))
    parser.add_argument("--cache", type=Path, default=Path(".art032/corpus-openai-text-embedding-3-small.npz"))
    parser.add_argument("--population-metadata", type=Path,
                        default=Path(".art032/candidate-population.json"))
    parser.add_argument("--calibrate", action="store_true")
    parser.add_argument("--embedding-threshold", type=float)
    parser.add_argument("--lexical-threshold", type=float)
    parser.add_argument("--output", type=Path, default=Path("docs/analysis/ART-032-baseline-results.json"))
    args = parser.parse_args()
    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    vectors = embed_articles(corpus["articles"], args.cache)
    embedding_candidates, lexical_candidates = candidate_pairs(args.population_metadata)
    if args.calibrate:
        lexical_threshold, lexical_result = best_threshold(
            corpus, vectors, embedding_candidates, lexical_candidates, "lexical")
        embedding_threshold, embedding_result = best_threshold(
            corpus, vectors, embedding_candidates, lexical_candidates, "embedding")
        result = {"calibrationOnly": True,
                  "lexical": {"threshold": lexical_threshold, "metrics": lexical_result},
                  "embedding": {"threshold": embedding_threshold, "metrics": embedding_result}}
    else:
        if args.embedding_threshold is None or args.lexical_threshold is None:
            raise SystemExit("Frozen --embedding-threshold and --lexical-threshold are required")
        lexical, embedding = predictions(corpus, vectors, embedding_candidates, lexical_candidates,
                                         args.embedding_threshold, args.lexical_threshold)
        result = {
            "generatedAt": datetime.now(timezone.utc).isoformat(), "corpusVersion": corpus["corpusVersion"],
            "articles": len(corpus["articles"]), "model": MODEL, "modelVersion": MODEL_VERSION,
            "titleInputVersion": corpus["baseline"]["titleInputVersion"],
            "titleInputSetHash": hashlib.sha256("\n".join(
                article["titleInputHash"] for article in corpus["articles"]).encode()).hexdigest(),
            "candidateWindowHours": corpus["baseline"]["candidateWindowHours"],
            "topK": corpus["baseline"]["topK"],
            "candidatePopulation": "deterministic 4,000-url-hash sample plus duplicate/sparse strata and all corpus articles",
            "embeddingCandidateTieBreak": "descending cosine, then ascending urlHash",
            "lexicalCandidateTieBreak": "descending token Jaccard, then ascending urlHash",
            "lexical": {"threshold": args.lexical_threshold,
                        "decisionRule": "within 24h lexical top-50 and token Jaccard >= frozen threshold",
                        "evaluation": evaluate(corpus, lexical, "evaluation")},
            "embedding": {"threshold": args.embedding_threshold,
                          "decisionRule": "within 24h embedding top-50 and cosine >= frozen threshold",
                          "evaluation": evaluate(corpus, embedding, "evaluation")},
        }
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
