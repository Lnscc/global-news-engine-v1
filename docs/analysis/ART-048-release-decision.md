# ART-048 release decision

- Candidate: `story-mvp-title-embedding-24h-v1.1.0` (ID `4`)
- Review date: 2026-10-15
- Reviewer: Linus
- Holdout: `art048-holdout-v1.0.0`
- Gate: `story-release-gates-v1`
- Result: approved and promoted

## Evaluation

The independent holdout contains 20 positive and 20 negative pairs. The candidate produced 19
true positives, no false positives, and one false negative. Precision is `1.00` and recall is
`0.95`; both fixed gates (`>= 0.95` precision and `>= 0.90` recall) pass.

Holdout hash: `a5e46c9583c7329ff1b44339175586b0a0483bf05c5d0fd4435508ad856ae626`

## Story diff

The active version contains 862 stories and 1,068 memberships. Candidate 1.1 contains 22,561
stories and 41,024 memberships on the shared watermark `2026-09-30T20:20:54.985885Z`. The
singleton share changes from `0.8781902552204176` to `0.8397234165152254`; 39,970 memberships
change identity, with one cross-version merge and one split.

Review basis hash: `cf0cc48696e60bf1d440247590bebd3d500e94798208802624fe499f6653eb55`

## Regression evidence

ART-045 remains separate from the release holdout. Its five known relationship deviations remain
visible; the seven input-quality cases have no deviations. These regression cases are diagnostic
evidence and are not counted toward the release gates.

## Decision

The unchanged candidate passes the fixed gates. Linus approved the promotion on 2026-10-15.
Version 1.1 is now the only `ACTIVE` version and version 1.0 is `RETIRED`. Verification found
22,561 visible stories and successfully loaded story detail
`0144f8ed-8fa8-3bff-a870-a1bf16d37e7c`. Reproducible machine-readable evidence is stored in
`docs/analysis/story-mvp-title-embedding-24h-v1.1.0-release-evidence.json`.
