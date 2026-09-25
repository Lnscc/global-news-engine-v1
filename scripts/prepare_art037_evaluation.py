#!/usr/bin/env python3
"""Export frozen ART-032 vectors for the Java partition test, separately per split."""

import argparse
import base64
import hashlib
import json
from pathlib import Path

import numpy as np

from art032_evaluation import SPLITS, validate_corpus


def prepare(corpus_path: Path, cache_path: Path) -> dict:
    corpus_bytes = corpus_path.read_bytes()
    corpus = json.loads(corpus_bytes)
    errors = validate_corpus(corpus)
    if errors:
        raise ValueError("Invalid corpus: " + "; ".join(errors))
    articles = corpus["articles"]
    with np.load(cache_path, allow_pickle=False) as cache:
        if cache["model"].item() != corpus["baseline"]["embeddingModel"]:
            raise ValueError("Cached embedding model differs from corpus")
        if not np.array_equal(cache["hashes"], [a["titleInputHash"] for a in articles]):
            raise ValueError("Cached title inputs differ from corpus")
        vectors = cache["vectors"]
    if vectors.shape != (len(articles), 1536) or not np.isfinite(vectors).all():
        raise ValueError("Invalid cached vector dimensions or values")
    if np.any(np.linalg.norm(vectors, axis=1) == 0):
        raise ValueError("Zero-norm cached vector")

    refs = {split: set() for split in SPLITS}
    for pair in corpus["pairs"]:
        refs[pair["split"]].update((pair["left"], pair["right"]))
    for story in corpus["referenceStories"]:
        refs[story["split"]].update(story["articleRefs"])
    if refs["calibration"] & refs["evaluation"]:
        raise ValueError("Article/story leakage across splits")

    splits = {}
    for split in sorted(SPLITS):
        inputs = []
        for index, article in enumerate(articles):
            if article["ref"] not in refs[split]:
                continue
            vector = np.asarray(vectors[index], dtype=">f4").tobytes()
            effective_at = article.get("publishedAt") or article["firstSeenAt"]
            fingerprint = hashlib.sha256(json.dumps(
                [article["urlHash"], article["titleInputHash"], effective_at],
                separators=(",", ":")).encode()).hexdigest()
            inputs.append({"id": index + 1, "corpusRef": article["ref"],
                           "articleRef": article["urlHash"], "fingerprint": fingerprint,
                           "effectiveAt": effective_at,
                           "timeSource": "PUBLISHED_AT" if article.get("publishedAt") else "FIRST_SEEN_AT",
                           "titleInputHash": article["titleInputHash"],
                           "vectorHash": hashlib.sha256(vector).hexdigest(),
                           "vectorBytes": base64.b64encode(vector).decode()})
        splits[split] = inputs
    return {"corpusFileHash": hashlib.sha256(corpus_bytes).hexdigest(),
            "cacheFileHash": hashlib.sha256(cache_path.read_bytes()).hexdigest(),
            "corpusVersion": corpus["corpusVersion"], "splits": splits}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, default=Path("docs/analysis/ART-032-corpus.json"))
    parser.add_argument("--cache", type=Path, default=Path(".art032/corpus-openai-text-embedding-3-small.npz"))
    parser.add_argument("--output", type=Path, default=Path("target/art037-inputs.json"))
    args = parser.parse_args()
    result = prepare(args.corpus, args.cache)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Exported {args.output}: " + ", ".join(f"{s}={len(v)}" for s, v in result["splits"].items()))
