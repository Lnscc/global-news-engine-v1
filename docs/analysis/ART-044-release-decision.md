# ART-044 release decision

- Candidate: `story-mvp-title-embedding-24h-v1.0.0` (ID `1`)
- Review date: 2026-09-30
- Reviewer and approver: Linus
- Holdout: `art044-holdout-v1.0.0`
- Gate: `story-release-gates-v1`
- Result: approved for promotion

## Evaluation

The independent holdout contains 20 positive and 20 negative pairs. The candidate produced 19
true positives, no false positives, and one false negative. Precision is `1.00` and recall is
`0.95`; both gates (`>= 0.95` precision and `>= 0.90` recall) pass.

Holdout hash: `873a956cf283a465913688f969b4f3dd75ba31623f160a4515b8ce806d15c82e`

## Story diff

There was no prior active version. On the retained 74-article review data, the candidate contains
53 stories and 74 memberships, with a singleton share of `0.6226415094339622`. Consequently all
74 memberships are new; there are no cross-version merges or splits.

Review basis hash: `62f6fa83f9907740cd126eb24bdf02d9ec6a7bd59d0868d5ec5c2755a48144b0`

## Decision

Linus approved promotion because the confirmed, independent holdout passes the fixed release
gates. The local operational dataset was reduced to the 74 holdout articles before the final
snapshot because the remaining local data was explicitly declared disposable. Raw import payloads
remain available for rebuilding the discarded local data.

Promotion completed with outcome `PROMOTED`. Version 1 is the only `ACTIVE` version; versions 2
and 3 remain `SHADOW`. The active dataset exposes 53 stories. `GET /stories` returned `200`, and
`GET /stories/19e28a85-4bca-3733-97d4-5ed5cb6f53ba` returned `200`.
