import copy
import json
import unittest
from pathlib import Path

from art045_regression import evaluate, validate

ROOT = Path(__file__).resolve().parents[1]


class RegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.corpus = json.loads((ROOT / "docs/analysis/ART-045-regression-cases-v1.json").read_text(encoding="utf-8"))
        cls.baseline = json.loads((ROOT / "docs/analysis/ART-045-baseline-v1.json").read_text(encoding="utf-8"))

    def test_frozen_baseline_is_valid_and_reproducible(self):
        self.assertEqual([], validate(self.corpus, self.baseline))
        self.assertEqual(self.baseline["expectedEvaluation"], evaluate(self.corpus, self.baseline))

    def test_regression_is_visible(self):
        changed = copy.deepcopy(self.baseline)
        changed["relationshipPredictions"]["false-merge-football"] = "DIFFERENT_STORY"
        self.assertNotEqual(changed["expectedEvaluation"], evaluate(self.corpus, changed))

    def test_articles_are_disjoint_from_release_holdout(self):
        holdout = json.loads((ROOT / "docs/analysis/ART-044-holdout-v1.json").read_text(encoding="utf-8"))
        holdout_refs = {ref for pair in holdout["pairs"] for ref in (pair["left"], pair["right"])}
        regression_refs = {article["ref"] for article in self.corpus["articles"]}
        self.assertTrue(regression_refs.isdisjoint(holdout_refs))


if __name__ == "__main__":
    unittest.main()
