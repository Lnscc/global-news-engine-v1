#!/usr/bin/env python3
"""Validate and evaluate the versioned ART-032 story reference corpus.

The module deliberately uses only the Python standard library. Database access and
embedding generation belong to ``sample_art032_candidates.py``; reference labels
must always be reviewed independently from those candidate signals.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

LABELS = {"SAME_STORY", "DIFFERENT_STORY", "UNCERTAIN"}
SPLITS = {"calibration", "evaluation"}
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def normalize_title(value: str) -> str:
    """Apply the frozen ART-031/032 title-input normalization."""
    return " ".join(unicodedata.normalize("NFKC", value).split())


def title_hash(value: str) -> str:
    return hashlib.sha256(normalize_title(value).encode("utf-8")).hexdigest()


def _required(value: dict[str, Any], fields: tuple[str, ...], context: str,
              errors: list[str]) -> None:
    for field in fields:
        if field not in value or value[field] in (None, "", []):
            errors.append(f"{context}: missing {field}")


def validate_corpus(corpus: dict[str, Any], enforce_minimum: bool = True) -> list[str]:
    """Return all corpus-contract violations without mutating the input."""
    errors: list[str] = []
    _required(corpus, ("schemaVersion", "corpusVersion", "provenance", "articles",
                       "pairs", "referenceStories", "baseline"), "corpus", errors)
    articles = corpus.get("articles", [])
    pairs = corpus.get("pairs", [])
    stories = corpus.get("referenceStories", [])

    refs: set[str] = set()
    url_hashes: set[str] = set()
    for index, article in enumerate(articles):
        context = f"articles[{index}]"
        _required(article, ("ref", "urlHash", "canonicalUrl", "domain", "title",
                            "titleInputHash", "firstSeenAt"), context, errors)
        ref = article.get("ref")
        url_hash = article.get("urlHash")
        if ref in refs:
            errors.append(f"{context}: duplicate ref {ref}")
        refs.add(ref)
        if url_hash in url_hashes:
            errors.append(f"{context}: duplicate urlHash {url_hash}")
        url_hashes.add(url_hash)
        if url_hash and not SHA256.fullmatch(url_hash):
            errors.append(f"{context}: urlHash is not lowercase SHA-256")
        if article.get("title") and article.get("titleInputHash") != title_hash(article["title"]):
            errors.append(f"{context}: titleInputHash does not match normalized title")

    pair_ids: set[str] = set()
    pair_keys: set[tuple[str, str]] = set()
    definite = same = hard_negative = 0
    article_splits: dict[str, set[str]] = defaultdict(set)
    for index, pair in enumerate(pairs):
        context = f"pairs[{index}]"
        _required(pair, ("id", "left", "right", "label", "split", "category",
                         "rationale", "verification"), context, errors)
        pair_id = pair.get("id")
        if pair_id in pair_ids:
            errors.append(f"{context}: duplicate id {pair_id}")
        pair_ids.add(pair_id)
        left, right = pair.get("left"), pair.get("right")
        if left == right:
            errors.append(f"{context}: pair references the same article twice")
        for ref in (left, right):
            if ref not in refs:
                errors.append(f"{context}: unknown article ref {ref}")
        key = tuple(sorted((left or "", right or "")))
        if key in pair_keys:
            errors.append(f"{context}: duplicate unordered article pair")
        pair_keys.add(key)
        label, split = pair.get("label"), pair.get("split")
        if label not in LABELS:
            errors.append(f"{context}: invalid label {label}")
        if split not in SPLITS:
            errors.append(f"{context}: invalid split {split}")
        if split in SPLITS:
            article_splits[left].add(split)
            article_splits[right].add(split)
        verification = pair.get("verification", {})
        _required(verification, ("method", "checkedAt", "evidence"),
                  f"{context}.verification", errors)
        if label != "UNCERTAIN":
            definite += 1
            same += label == "SAME_STORY"
            hard_negative += label == "DIFFERENT_STORY" and bool(pair.get("hardNegative"))

    for ref, splits in article_splits.items():
        if len(splits) > 1:
            errors.append(f"article {ref} leaks across calibration/evaluation: {sorted(splits)}")

    story_ids: set[str] = set()
    story_articles: dict[str, str] = {}
    for index, story in enumerate(stories):
        context = f"referenceStories[{index}]"
        _required(story, ("id", "split", "articleRefs", "rationale"), context, errors)
        story_id, split = story.get("id"), story.get("split")
        if story_id in story_ids:
            errors.append(f"{context}: duplicate id {story_id}")
        story_ids.add(story_id)
        if split not in SPLITS:
            errors.append(f"{context}: invalid split {split}")
        story_refs = story.get("articleRefs", [])
        if len(story_refs) < 3:
            errors.append(f"{context}: fewer than three articles")
        for ref in story_refs:
            if ref not in refs:
                errors.append(f"{context}: unknown article ref {ref}")
            if ref in story_articles:
                errors.append(f"{context}: {ref} also belongs to {story_articles[ref]}")
            story_articles[ref] = story_id
            if split in SPLITS and article_splits.get(ref, {split}) != {split}:
                errors.append(f"{context}: story/article split mismatch for {ref}")

    baseline = corpus.get("baseline", {})
    _required(baseline, ("titleInputVersion", "embeddingModel", "embeddingModelVersion",
                         "candidateWindowHours", "topK", "frozenDecisionRule"),
              "baseline", errors)
    if enforce_minimum:
        if definite < 100:
            errors.append(f"minimum: {definite} definite pairs, need at least 100")
        if same < 25:
            errors.append(f"minimum: {same} SAME_STORY pairs, need at least 25")
        if hard_negative < 25:
            errors.append(f"minimum: {hard_negative} hard negatives, need at least 25")
        if sum(len(story.get("articleRefs", [])) >= 3 for story in stories) < 10:
            errors.append("minimum: need at least ten reference stories with three articles")
    return errors


def _safe_div(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 0.0


def evaluate(corpus: dict[str, Any], predictions: dict[str, Any], split: str) -> dict[str, Any]:
    """Evaluate pair predictions; UNCERTAIN reference pairs are reported but excluded."""
    by_id = {item["pairId"]: item for item in predictions.get("pairs", [])}
    selected = [pair for pair in corpus["pairs"] if pair["split"] == split]
    clear = [pair for pair in selected if pair["label"] != "UNCERTAIN"]
    missing = [pair["id"] for pair in clear if pair["id"] not in by_id]
    if missing:
        raise ValueError(f"missing predictions for: {', '.join(missing)}")

    tp = fp = fn = tn = candidate_tp = 0
    predicted_same_edges: list[tuple[str, str]] = []
    for pair in clear:
        predicted = by_id[pair["id"]]
        actual_same = pair["label"] == "SAME_STORY"
        predicted_same = bool(predicted["sameStory"])
        tp += actual_same and predicted_same
        fp += not actual_same and predicted_same
        fn += actual_same and not predicted_same
        tn += not actual_same and not predicted_same
        candidate_tp += actual_same and bool(predicted.get("candidate", predicted_same))
        if predicted_same:
            predicted_same_edges.append((pair["left"], pair["right"]))

    precision = _safe_div(tp, tp + fp)
    recall = _safe_div(tp, tp + fn)
    f1 = _safe_div(2 * precision * recall, precision + recall)
    article_refs = {ref for pair in clear for ref in (pair["left"], pair["right"])}
    parent = {ref: ref for ref in article_refs}

    def find(ref: str) -> str:
        while parent[ref] != ref:
            parent[ref] = parent[parent[ref]]
            ref = parent[ref]
        return ref

    for left, right in predicted_same_edges:
        left_root, right_root = find(left), find(right)
        if left_root != right_root:
            parent[right_root] = left_root
    sizes = Counter(find(ref) for ref in article_refs)
    false_merge_pairs = [pair["id"] for pair in clear
                         if pair["label"] == "DIFFERENT_STORY"
                         and find(pair["left"]) == find(pair["right"])]
    return {
        "split": split,
        "clearPairs": len(clear),
        "uncertainExcluded": len(selected) - len(clear),
        "confusion": {"truePositive": tp, "falsePositive": fp,
                      "falseNegative": fn, "trueNegative": tn},
        "pairwisePrecision": precision,
        "pairwiseRecall": recall,
        "pairwiseF1": f1,
        "candidateRecall": _safe_div(candidate_tp, tp + fn),
        "falseMerges": {"count": len(false_merge_pairs), "pairIds": false_merge_pairs},
        "singletonShare": _safe_div(sum(size == 1 for size in sizes.values()), len(sizes)),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus", type=Path)
    parser.add_argument("--predictions", type=Path)
    parser.add_argument("--split", choices=sorted(SPLITS), default="evaluation")
    parser.add_argument("--allow-incomplete", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    errors = validate_corpus(corpus, enforce_minimum=not args.allow_incomplete)
    if errors:
        raise SystemExit("Invalid corpus:\n- " + "\n- ".join(errors))
    result: dict[str, Any] = {"valid": True}
    if args.predictions:
        predictions = json.loads(args.predictions.read_text(encoding="utf-8"))
        result["evaluation"] = evaluate(corpus, predictions, args.split)
    rendered = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
