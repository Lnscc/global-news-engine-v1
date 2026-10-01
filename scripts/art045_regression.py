#!/usr/bin/env python3
"""Validate and evaluate the frozen ART-045 story regression cases."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

SHA256 = re.compile(r"^[0-9a-f]{64}$")
RELATIONSHIPS = {"SAME_STORY", "DIFFERENT_STORY", "UNCERTAIN"}
DISPOSITIONS = {"INCLUDE", "EXCLUDE"}
CATEGORIES = {"NAVIGATION_SERVICE", "NORMAL_EVENT_ARTICLE"}


def validate(corpus: dict[str, Any], baseline: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    for field in ("schemaVersion", "corpusVersion", "provenance", "articles",
                  "relationshipCases", "inputQualityCases"):
        if not corpus.get(field):
            errors.append(f"corpus: missing {field}")
    provenance = corpus.get("provenance", {})
    if provenance.get("usage") != "REGRESSION_ONLY" or provenance.get("releaseHoldout") is not False:
        errors.append("corpus: must be marked as regression-only and not a release holdout")

    refs: set[str] = set()
    for index, article in enumerate(corpus.get("articles", [])):
        ref, url = article.get("ref", ""), article.get("canonicalUrl", "")
        if not SHA256.fullmatch(ref):
            errors.append(f"articles[{index}]: invalid ref")
        if ref in refs:
            errors.append(f"articles[{index}]: duplicate ref {ref}")
        refs.add(ref)
        if hashlib.sha256(url.encode()).hexdigest() != ref:
            errors.append(f"articles[{index}]: ref does not hash canonicalUrl")
        if not article.get("title"):
            errors.append(f"articles[{index}]: missing title")

    case_ids: set[str] = set()
    scored_relationships: set[str] = set()
    for index, case in enumerate(corpus.get("relationshipCases", [])):
        case_id = case.get("id", "")
        if not case_id or case_id in case_ids:
            errors.append(f"relationshipCases[{index}]: missing or duplicate id")
        case_ids.add(case_id)
        if case.get("left") not in refs or case.get("right") not in refs:
            errors.append(f"relationshipCases[{index}]: unknown article ref")
        if case.get("targetLabel") not in RELATIONSHIPS:
            errors.append(f"relationshipCases[{index}]: invalid targetLabel")
        if not case.get("sourceFinding") or not case.get("rationale"):
            errors.append(f"relationshipCases[{index}]: missing evidence")
        if case.get("targetLabel") == "UNCERTAIN":
            if not case.get("exclusionReason"):
                errors.append(f"relationshipCases[{index}]: uncertain case needs exclusionReason")
        else:
            scored_relationships.add(case_id)

    quality_ids: set[str] = set()
    for index, case in enumerate(corpus.get("inputQualityCases", [])):
        case_id = case.get("id", "")
        if not case_id or case_id in quality_ids:
            errors.append(f"inputQualityCases[{index}]: missing or duplicate id")
        quality_ids.add(case_id)
        if case.get("articleRef") not in refs:
            errors.append(f"inputQualityCases[{index}]: unknown article ref")
        if case.get("targetCategory") not in CATEGORIES:
            errors.append(f"inputQualityCases[{index}]: invalid targetCategory")
        if case.get("expectedDisposition") not in DISPOSITIONS or not case.get("rationale"):
            errors.append(f"inputQualityCases[{index}]: invalid expectation")

    if baseline.get("corpusVersion") != corpus.get("corpusVersion"):
        errors.append("baseline: corpusVersion mismatch")
    if not baseline.get("clusteringVersion") or not baseline.get("observationSource"):
        errors.append("baseline: missing clustering provenance")
    relationship_predictions = baseline.get("relationshipPredictions", {})
    if set(relationship_predictions) != scored_relationships:
        errors.append("baseline: relationship predictions do not cover scored cases exactly")
    if any(value not in RELATIONSHIPS - {"UNCERTAIN"}
           for value in relationship_predictions.values()):
        errors.append("baseline: invalid relationship prediction")
    disposition_predictions = baseline.get("inputDispositionPredictions", {})
    if set(disposition_predictions) != quality_ids:
        errors.append("baseline: input predictions do not cover quality cases exactly")
    if any(value not in DISPOSITIONS for value in disposition_predictions.values()):
        errors.append("baseline: invalid input disposition prediction")
    return errors


def evaluate(corpus: dict[str, Any], baseline: dict[str, Any]) -> dict[str, Any]:
    relationships = [case for case in corpus["relationshipCases"]
                     if case["targetLabel"] != "UNCERTAIN"]
    relationship_deviations = sorted(
        case["id"] for case in relationships
        if baseline["relationshipPredictions"][case["id"]] != case["targetLabel"]
    )
    quality = corpus["inputQualityCases"]
    quality_deviations = sorted(
        case["id"] for case in quality
        if baseline["inputDispositionPredictions"][case["id"]] != case["expectedDisposition"]
    )
    return {
        "corpusVersion": corpus["corpusVersion"],
        "relationshipCases": len(relationships),
        "uncertainExcluded": len(corpus["relationshipCases"]) - len(relationships),
        "relationshipDeviations": relationship_deviations,
        "inputQualityCases": len(quality),
        "navigationServiceCases": sum(
            case["targetCategory"] == "NAVIGATION_SERVICE" for case in quality),
        "inputQualityDeviations": quality_deviations,
        "totalDeviations": len(relationship_deviations) + len(quality_deviations),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("corpus", type=Path)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
    errors = validate(corpus, baseline)
    if errors:
        raise SystemExit("Invalid regression data:\n- " + "\n- ".join(errors))
    result = evaluate(corpus, baseline)
    if result != baseline.get("expectedEvaluation"):
        raise SystemExit("Regression baseline changed:\n" + json.dumps(result, indent=2))
    rendered = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
