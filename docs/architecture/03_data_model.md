# Datenmodell

## Raw Layer

```text
source_batch
staging_row              temporär
raw_gdelt_file           temporär / optional
```

Die Originaldaten von GDELT sind Arbeitsdaten, kein dauerhaftes Produktmodell. Sie werden nur so lange behalten, wie sie für Debugging, Replay oder Fehleranalyse nützlich sind.

Empfohlene MVP-Regel:

```text
Raw ZIPs und Staging-Daten nach erfolgreicher Verarbeitung löschen
oder maximal 7-30 Tage behalten.
```

## Normalized GDELT Layer

```text
gdelt_event              optional / temporär
gdelt_mention            optional / temporär
gdelt_gkg                optional / temporär
```

Dieser Layer ist optional. Wenn normalisierte GDELT-Daten gespeichert werden, dann primär für Debugging und Wiederverarbeitung. Dauerhaft wichtig sind die daraus abgeleiteten Artikel, Story-Zuordnungen und kompakten Story-Signale.

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
- primary_country
- primary_location_name
- primary_latitude
- primary_longitude
- centroid_embedding_id
- confidence
- created_at
- updated_at
```

Werte wie `first_seen_at`, `last_seen_at`, `article_count` und `source_count` werden im MVP nicht im Story-Kernmodell gespeichert. Sie können aus `story_article` und `article` berechnet werden.

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
- source_url
- source_domain
- country
- latitude
- longitude
- actor_1
- actor_2
- event_code
- themes
- persons
- organizations
- tone
- score
- observed_at
```

Story-Signale halten verdichtet fest, warum eine Story existiert und welche GDELT-Daten sie stützen. Sie ersetzen für die Produktlogik die dauerhafte Speicherung großer GDELT-Rohdaten.

## Topic

Topics aggregieren mehrere Stories zu einem mittelfristigen Thema.

```text
topic
- id
- title
- summary
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
staging_row              temporär
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

Optionale oder temporäre Tabellen:

```text
raw_gdelt_file
gdelt_event
gdelt_mention
gdelt_gkg
```
