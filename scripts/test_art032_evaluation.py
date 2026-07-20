import hashlib
import unittest

from art032_evaluation import evaluate, title_hash, validate_corpus


def article(ref: str) -> dict:
    title = f"Title {ref}"
    digest = hashlib.sha256(ref.encode()).hexdigest()
    return {"ref": ref, "urlHash": digest, "canonicalUrl": f"https://example.com/{ref}",
            "domain": "example.com", "title": title, "titleInputHash": title_hash(title),
            "firstSeenAt": "2026-07-01T00:00:00Z"}


def pair(pair_id: str, left: str, right: str, label: str, split="evaluation") -> dict:
    return {"id": pair_id, "left": left, "right": right, "label": label,
            "split": split, "category": "test", "rationale": "Test rationale.",
            "hardNegative": label == "DIFFERENT_STORY",
            "verification": {"method": "source-review", "checkedAt": "2026-07-20",
                             "evidence": ["Independent test evidence."]}}


def corpus() -> dict:
    return {"schemaVersion": "1.0", "corpusVersion": "test", "provenance": {"source": "test"},
            "articles": [article(ref) for ref in "abcd"],
            "pairs": [pair("p1", "a", "b", "SAME_STORY"),
                      pair("p2", "b", "c", "DIFFERENT_STORY"),
                      pair("p3", "c", "d", "UNCERTAIN")],
            "referenceStories": [{"id": "s1", "split": "evaluation",
                                  "articleRefs": ["a", "b", "d"],
                                  "rationale": "Test story."}],
            "baseline": {"titleInputVersion": "v1", "embeddingModel": "model",
                         "embeddingModelVersion": "version", "candidateWindowHours": 24,
                         "topK": 50, "frozenDecisionRule": "test"}}


class ValidationTests(unittest.TestCase):
    def test_incomplete_fixture_is_structurally_valid(self):
        self.assertEqual([], validate_corpus(corpus(), enforce_minimum=False))

    def test_detects_split_leakage(self):
        value = corpus()
        value["pairs"].append(pair("p4", "a", "d", "DIFFERENT_STORY", "calibration"))
        self.assertTrue(any("leaks" in error for error in validate_corpus(value, False)))

    def test_detects_title_hash_change(self):
        value = corpus()
        value["articles"][0]["title"] = "Changed"
        self.assertTrue(any("titleInputHash" in error for error in validate_corpus(value, False)))


class EvaluationTests(unittest.TestCase):
    def test_metrics_exclude_uncertain_and_report_false_merge(self):
        predictions = {"pairs": [
            {"pairId": "p1", "sameStory": True, "candidate": True},
            {"pairId": "p2", "sameStory": True, "candidate": True},
        ]}
        result = evaluate(corpus(), predictions, "evaluation")
        self.assertEqual(1, result["uncertainExcluded"])
        self.assertEqual(0.5, result["pairwisePrecision"])
        self.assertEqual(1.0, result["pairwiseRecall"])
        self.assertEqual(["p2"], result["falseMerges"]["pairIds"])


if __name__ == "__main__":
    unittest.main()
