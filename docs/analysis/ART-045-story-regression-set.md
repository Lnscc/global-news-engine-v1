# ART-045: Story regression set

`ART-045-regression-cases-v1.json` freezes the known ART-038 quality findings as regression
evidence. It contains three confirmed false merges, two confirmed missed merges, one explicitly
excluded uncertain pair, and a separate input-quality stratum with six navigation/service pages
and one normal editorial article.

`ART-045-baseline-v1.json` records the observed behaviour of
`story-mvp-title-embedding-24h-v1.0.0` in ART-038 snapshot 163. All five scored relationship cases
and all six navigation/service cases are known deviations. The editorial FasterSkier article is
correctly included. These are expected baseline failures, not release-gate results.

Run the deterministic, standard-library-only check from the repository root:

```text
python scripts/art045_regression.py docs/analysis/ART-045-regression-cases-v1.json docs/analysis/ART-045-baseline-v1.json
python -m unittest discover -s scripts -p "test_art045_regression.py"
```

The command fails if references, evidence, labels, category coverage, predictions, or the frozen
evaluation change. An intentional improvement therefore requires a reviewed baseline update;
an unreviewed regression cannot silently replace the current result.

This corpus is deliberately contaminated by prior analysis: ART-038 supplied the cases and ART-045
supplied the final labels. It is not independent and must never be passed to the ART-044 promotion
gate or cited as release evidence.
