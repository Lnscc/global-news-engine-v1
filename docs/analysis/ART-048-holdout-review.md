# ART-048: Holdout review

Status: labelled and reviewed on 2026-10-07

## Basis

- Candidate: `story-mvp-title-embedding-24h-v1.1.0`
- Source: locally imported GDELT articles
- Source window: `firstSeenAt` from `2026-09-03T08:15:00Z` through `2026-09-03T14:30:00Z`
- Snapshot watermark: `2026-09-03T14:30:00Z`
- Labels: 20 positive and 20 negative pairs with rationales in
  `docs/analysis/ART-048-holdout-v1.json`
- Label responsibility: Codex on behalf of Linus

## Independence

The candidate and `story-release-gates-v1` were frozen before this holdout was created. Every
article reference was checked against the ART-032 corpus, the ART-044 holdout, and the ART-045
regression cases; there is no overlap. The holdout was not used to change version 1.1, its input
eligibility rule, its similarity threshold, its features, or its release gates.

The ART-045 navigation/service regression set remains separate supporting evidence. It is not
included in the 40 release pairs and is not counted toward precision or recall.

## Review method

Positive labels require the same concrete report or publication event. Negative labels pair
clearly distinct people, decisions, products, places, or events. Ambiguous pairs were omitted.
The machine-readable file is the canonical evaluation input; this review records its provenance,
responsibility, scope, and independence.
