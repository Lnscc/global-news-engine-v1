#!/usr/bin/env python3
"""Materialize the reviewed ART-032 corpus from pinned real-data selections.

Story groups and cross-group decisions below are human-reviewed assertions. The
database query only resolves them to stable URL hashes and source metadata; it
does not infer ground-truth labels.
"""

from __future__ import annotations

import argparse
import hashlib
import itertools
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

import psycopg

from art032_evaluation import normalize_title, title_hash
from sample_art032_candidates import stable_ref

WINDOW_START = "2026-07-12T20:15:00+00:00"
WINDOW_END = "2026-07-20T18:45:00+00:00"

STORIES = [
    {"id": "story-cyclospora-national-outbreak", "split": "calibration",
     "title": "cyclosporiasis outbreak thousands fall ill in 31 states",
     "rationale": "Reports on the same national 2026 Cyclospora illness outbreak."},
    {"id": "story-cyclospora-lettuce-recall", "split": "calibration",
     "title": "iceberg lettuce recalled in 27 states due to potential cyclospora contamination",
     "rationale": "Reports on the same later Taylor Farms lettuce recall."},
    {"id": "story-ann-widdecombe-investigation", "split": "calibration",
     "title": "murder of ann widdecombe being investigated as potential act of terrorism",
     "rationale": "Syndicated reports on the same murder investigation."},
    {"id": "story-bangkok-pub-fire", "split": "calibration",
     "title": "twenty seven people killed in fire at bangkok pub say officials",
     "rationale": "Syndicated reports on the same Bangkok pub fire and death toll."},
    {"id": "story-hormuz-toll-announcement", "split": "evaluation",
     "title": "trump says the u s is back to blockading iran and will charge ships a toll in hormuz",
     "rationale": "Reports on the July 13 decision to announce a Hormuz shipping toll."},
    {"id": "story-hormuz-toll-withdrawal", "split": "evaluation",
     "title": "trump backs down over threat to levy tolls on shipping in strait of hormuz",
     "rationale": "Reports on the separate July 14 decision to withdraw the toll plan."},
    {"id": "story-lindsey-graham-death", "split": "evaluation",
     "title": "sen lindsey graham a close trump ally and foreign policy hawk dies after a brief illness",
     "rationale": "Reports announcing Lindsey Graham's death after a brief illness."},
    {"id": "story-darline-graham-swearing-in", "split": "evaluation",
     "title": "darline graham sister of late sen lindsey graham has been sworn in to finish his term",
     "rationale": "Reports on Darline Graham's later swearing-in as a senator."},
    {"id": "story-cairngorms-wildfire-day-three", "split": "evaluation",
     "title": "helicopter deployed as firefighters spend third day tackling cairngorms wildfire",
     "rationale": "Reports on the same third-day response to the Cairngorms wildfire."},
    {"id": "story-yellowstone-bison-attack", "split": "evaluation",
     "title": "tourist hospitalized after bison tossed him into the air at yellowstone",
     "rationale": "Reports on the same Yellowstone bison attack and hospitalization."},
]

HARD_RELATIONS = {
    frozenset(("story-cyclospora-national-outbreak", "story-cyclospora-lettuce-recall")): {
        "category": "follow-up-recall",
        "rationale": "The illness outbreak and the later voluntary product recall are causally linked but distinct concrete events.",
        "evidence": [
            "The outbreak concerns illnesses across states; the FDA records a separate recall initiated on July 17.",
            "https://www.fda.gov/food/outbreaks-foodborne-illness/investigation-5-state-outbreak-cyclospora-illnesses-iceberg-lettuce-july-2026",
            "https://www.fda.gov/safety/recalls-market-withdrawals-safety-alerts/taylor-fresh-foods-recalls-iceberg-lettuce-central-mexico-because-possible-health-risk",
        ]},
    frozenset(("story-hormuz-toll-announcement", "story-hormuz-toll-withdrawal")): {
        "category": "reversed-decision",
        "rationale": "The July 13 toll announcement and July 14 withdrawal are two opposing presidential decisions, not updates to one occurrence.",
        "evidence": [
            "Le Monde reports the July 13 announcement; Axios reports the July 14 backtrack.",
            "https://www.lemonde.fr/en/international/article/2026/07/13/trump-imposes-maritime-toll-in-strait-of-hormuz-as-ship-traffic-collapses_6755446_4.html",
            "https://www.axios.com/2026/07/14/trump-hormuz-toll-demand-trade-fee",
        ]},
    frozenset(("story-lindsey-graham-death", "story-darline-graham-swearing-in")): {
        "category": "same-person-follow-up-appointment",
        "rationale": "Graham's death and his sister's later swearing-in are causally related but distinct events with different dominant actions.",
        "evidence": [
            "The death was reported on July 12; AP records the separate swearing-in three days after the death.",
            "https://www.local10.com/news/politics/2026/07/12/sen-lindsey-graham-a-close-trump-ally-dies-after-a-brief-and-unexpected-illness-his-office-says/",
            "https://apnews.com/article/87bce5649c07e03129cf535feb97873a",
        ]},
}

AMBIGUOUS = [
    ("uncertain-iran-island-explosions", 3914, 34550, "calibration", "sparse-breaking",
     "Both reports mention Qeshm explosions, but different second locations and a two-day gap do not establish one incident."),
    ("uncertain-cyclospora-scope", 51369, 53621, "calibration", "scope",
     "The titles do not establish whether the national/Michigan report and New Mexico cases belong to one outbreak event."),
    ("uncertain-widdecombe-retrospective", 24646, 26242, "evaluation", "retrospective",
     "The interview immediately before the killing may be context or the dominant subject; source framing is insufficiently stable."),
    ("uncertain-hormuz-explosions", 1154, 30351, "evaluation", "same-region",
     "Regional conflict context does not prove that unspecified Iranian explosions and the named tanker explosion are one event."),
    ("uncertain-bison-update", 858, 17980, "evaluation", "update",
     "The recovery report strongly suggests the same attack, but the first headline omits the victim identity needed for certainty."),
]

LOW_SIMILARITY_POSITIVES = [
    ("low-positive-carroll-cross-language", 46180, 50054, "calibration", "cross-language",
     "English and French reports describe the same Trump payment to E. Jean Carroll.", 0.593483),
    ("low-positive-balogun-cross-language", 31639, 48659, "calibration", "cross-language",
     "English and Italian reports describe the same Balogun/Trump-FIFA dressing-room controversy.", 0.540481),
    ("low-positive-ice-policy-paraphrase", 46198, 50378, "evaluation", "paraphrase",
     "Both reports describe the same ICE order suspending most vehicle stops.", 0.637285),
]

ARTICLE_SQL = """
SELECT a.id,a.url_hash,a.canonical_url,a.domain,a.first_seen_at,
       t.page_title,t.page_precise_pub_timestamp
FROM articles a
JOIN LATERAL (
  SELECT g.page_title,g.page_precise_pub_timestamp FROM gdelt_gkg g
  WHERE g.article_id=a.id AND nullif(btrim(g.page_title),'') IS NOT NULL
  ORDER BY g.source_timestamp,g.id LIMIT 1
) t ON true
WHERE a.first_seen_at >= %s AND a.first_seen_at < %s
ORDER BY a.url_hash,a.id
"""


def normalized_key(value: str) -> str:
    return re.sub(r"[^\w]+", " ", normalize_title(value).casefold()).strip()


def article_export(row: dict) -> dict:
    def iso(value):
        return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z") if value else None
    result = {"ref": stable_ref(row["url_hash"]), "urlHash": row["url_hash"],
              "canonicalUrl": row["canonical_url"], "domain": row["domain"],
              "title": row["page_title"], "titleInputHash": title_hash(row["page_title"]),
              "firstSeenAt": iso(row["first_seen_at"]), "publishedAt": iso(row["page_precise_pub_timestamp"])}
    return {key: value for key, value in result.items() if value is not None}


def pair_id(left: str, right: str) -> str:
    return "pair-" + hashlib.sha256("|".join(sorted((left, right))).encode()).hexdigest()[:16]


def make_pair(left: str, right: str, label: str, split: str, category: str,
              rationale: str, evidence: list[str], hard: bool = False) -> dict:
    return {"id": pair_id(left, right), "left": left, "right": right, "label": label,
            "split": split, "category": category, "hardNegative": hard,
            "rationale": rationale,
            "verification": {"method": "manual-source-and-metadata-review",
                             "checkedAt": "2026-07-20", "evidence": evidence}}


def build(connection) -> dict:
    with connection.cursor() as cursor:
        cursor.execute(ARTICLE_SQL, (WINDOW_START, WINDOW_END))
        names = [column.name for column in cursor.description]
        rows = [dict(zip(names, row)) for row in cursor.fetchall()]
    by_id = {row["id"]: row for row in rows}
    by_title: dict[str, list[dict]] = {}
    for row in rows:
        by_title.setdefault(normalized_key(row["page_title"]), []).append(row)

    selected: dict[str, dict] = {}
    story_rows: dict[str, list[dict]] = {}
    reference_stories = []
    pairs = []
    for story in STORIES:
        matches = by_title.get(story["title"], [])
        domains: set[str] = set()
        chosen = []
        for row in matches:
            if row["domain"] not in domains:
                chosen.append(row)
                domains.add(row["domain"])
            if len(chosen) == 3:
                break
        if len(chosen) != 3:
            raise RuntimeError(f"{story['id']}: expected three distinct-domain articles, got {len(chosen)}")
        story_rows[story["id"]] = chosen
        refs = [stable_ref(row["url_hash"]) for row in chosen]
        for row in chosen:
            selected[row["url_hash"]] = row
        reference_stories.append({"id": story["id"], "split": story["split"],
                                  "articleRefs": refs, "rationale": story["rationale"]})
        for left, right in itertools.combinations(refs, 2):
            pairs.append(make_pair(left, right, "SAME_STORY", story["split"], "syndicated",
                                   story["rationale"],
                                   ["Three distinct domains carry the same normalized headline and concrete event."]))

    # Every cross product in a reviewed hard relation is intentionally retained (3 x 3).
    for relation, review in HARD_RELATIONS.items():
        left_story, right_story = sorted(relation)
        split = next(story["split"] for story in STORIES if story["id"] == left_story)
        for left, right in itertools.product(story_rows[left_story], story_rows[right_story]):
            pairs.append(make_pair(stable_ref(left["url_hash"]), stable_ref(right["url_hash"]),
                                   "DIFFERENT_STORY", split, review["category"], review["rationale"],
                                   review["evidence"], hard=True))

    # Add 43 transparent negative controls within splits, avoiding the three hard relations.
    controls_needed = {"calibration": 20, "evaluation": 23}
    for split, target in controls_needed.items():
        split_stories = [story["id"] for story in STORIES if story["split"] == split]
        controls = []
        for left_story, right_story in itertools.combinations(split_stories, 2):
            if frozenset((left_story, right_story)) in HARD_RELATIONS:
                continue
            for left, right in itertools.product(story_rows[left_story], story_rows[right_story]):
                controls.append((left_story, right_story, left, right))
        for left_story, right_story, left, right in controls[:target]:
            pairs.append(make_pair(
                stable_ref(left["url_hash"]), stable_ref(right["url_hash"]), "DIFFERENT_STORY", split,
                "clear-negative-control",
                f"Distinct dominant events: {left_story.removeprefix('story-')} versus {right_story.removeprefix('story-')}.",
                ["Different named actions, subjects, and/or locations in the two pinned source headlines."],
                hard=False))

    for identifier, left_id, right_id, split, category, rationale, measured_cosine in LOW_SIMILARITY_POSITIVES:
        left, right = by_id[left_id], by_id[right_id]
        selected[left["url_hash"]] = left
        selected[right["url_hash"]] = right
        pairs.append(make_pair(
            stable_ref(left["url_hash"]), stable_ref(right["url_hash"]), "SAME_STORY", split,
            category, rationale,
            [f"ART-031 measured text-embedding-3-small cosine {measured_cosine:.6f}; source titles, actors, and concrete action were manually compared.",
             left["canonical_url"], right["canonical_url"]]))

    for identifier, left_id, right_id, split, category, rationale in AMBIGUOUS:
        left, right = by_id[left_id], by_id[right_id]
        selected[left["url_hash"]] = left
        selected[right["url_hash"]] = right
        pairs.append(make_pair(stable_ref(left["url_hash"]), stable_ref(right["url_hash"]),
                               "UNCERTAIN", split, category, rationale,
                               ["Both pinned URLs and available structured metadata were reviewed; decisive event identity is absent."]))

    return {
        "schemaVersion": "1.0", "corpusVersion": "1.0.0",
        "provenance": {"source": "local PostgreSQL populated by the GDELT pipeline",
                       "selectionStart": WINDOW_START, "selectionEndExclusive": WINDOW_END,
                       "generatedAt": datetime.now(timezone.utc).isoformat(),
                       "fullTextStored": False},
        "articles": [article_export(row) for row in sorted(selected.values(), key=lambda row: row["url_hash"])],
        "pairs": sorted(pairs, key=lambda pair: pair["id"]),
        "referenceStories": reference_stories,
        "baseline": {"titleInputVersion": "art031-title-nfkc-ws-v1",
                     "embeddingModel": "text-embedding-3-small",
                     "embeddingModelVersion": "openai:text-embedding-3-small@2026-07-20",
                     "candidateWindowHours": 24, "topK": 50,
                     "calibrationSplit": "calibration",
                     "frozenAt": "2026-07-20",
                     "lexicalThreshold": 0.090909,
                     "embeddingCosineThreshold": 0.224867,
                     "frozenDecisionRule": "candidate iff within 24h and top-50; SAME_STORY iff cosine >= 0.224867"},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dsn", default=os.environ.get("ART032_DSN", "postgresql://gne:gne@localhost:5432/gne"))
    parser.add_argument("--output", type=Path, default=Path("docs/analysis/ART-032-corpus.json"))
    args = parser.parse_args()
    with psycopg.connect(args.dsn) as connection:
        corpus = build(connection)
    args.output.write_text(json.dumps(corpus, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}: {len(corpus['articles'])} articles, {len(corpus['pairs'])} pairs")


if __name__ == "__main__":
    main()
