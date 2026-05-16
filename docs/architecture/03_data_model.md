# Datenmodell

## Raw Layer

```text
source_batch
staging_row
gdelt_event_raw
gdelt_mention_raw
gdelt_gkg_raw
```

Die Originaldaten von GDELT werden unverändert oder möglichst verlustfrei gespeichert. Dieser Layer dient als Audit- und Replay-Basis.

## Normalized GDELT Layer

```text
gdelt_event
gdelt_mention
gdelt_gkg
```

Dieser Layer enthält normalisierte, typisierte und abfragbare GDELT-Datensätze.

## Article

Artikel sind die atomare Informationseinheit der Plattform.

```text
article
- id
- canonical_url
- original_url
- source_name
- source_domain
- title
- text
- language
- published_at
- first_seen_at
- last_seen_at
- country
- themes
- persons
- organizations
- tone
- word_count
- sharing_image
- created_at
- updated_at
```

Für den MVP muss `text` nicht zwingend Volltext sein. Der erste robuste Schritt ist ein Embedding-Input aus vorhandenen GDELT-/GKG-Signalen:

```text
title
themes
persons
organizations
source_domain
locations
tone
published_at
```

Volltext-Extraktion kann später ergänzt werden, sollte aber nicht die Story-Erkennung blockieren.

## Article Embedding

```text
article_embedding
- id
- article_id
- model
- input_hash
- input_text
- vector
- created_at
```

Für den MVP ist `pgvector` die naheliegende Wahl, weil PostgreSQL ohnehin Teil der Architektur ist. Qdrant, Weaviate oder Milvus bleiben spätere Optionen, falls Volumen oder Suchanforderungen wachsen.

## Story

Eine Story gruppiert ähnliche Artikel über dasselbe konkrete Ereignis oder denselben eng zusammenhängenden Nachrichtenverlauf.

```text
story
- id
- title
- summary
- status                active / cooling / archived
- first_seen_at
- last_seen_at
- primary_country
- primary_location_name
- primary_latitude
- primary_longitude
- centroid_embedding_id
- confidence
- article_count
- source_count
- created_at
- updated_at
```

## Story Article

```text
story_article
- story_id
- article_id
- similarity_score
- signal_score
- assigned_at
```

## Story Signal

```text
story_signal
- id
- story_id
- signal_type           event / mention / gkg / article
- source_record_id
- score
- observed_at
```

Story-Signale halten nachvollziehbar fest, warum eine Story existiert und welche GDELT-Daten sie stützen.

## Topic

Topics aggregieren mehrere Stories zu einem mittelfristigen Thema.

```text
topic
- id
- title
- summary
- first_seen_at
- last_seen_at
- story_count
- confidence
- created_at
- updated_at
```

```text
topic_story
- topic_id
- story_id
- relevance_score
- assigned_at
```

## Theme

Themes sind langfristige Makro-Muster.

```text
theme
- id
- title
- summary
- horizon              weekly / monthly / quarterly
- first_seen_at
- last_seen_at
- confidence
- created_at
- updated_at
```

```text
theme_topic
- theme_id
- topic_id
- relevance_score
- assigned_at
```

## PostgreSQL Tabellen

```text
source_batch
staging_row
gdelt_event
gdelt_mention
gdelt_gkg
article
article_embedding
story
story_article
story_signal
topic
topic_story
theme
theme_topic
```
