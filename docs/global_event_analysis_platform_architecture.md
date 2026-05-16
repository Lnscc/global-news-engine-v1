# Globale Ereignis- und Analyseplattform – Backend Architektur

## Projektvision

Die Plattform soll globale Ereignisse analysieren, clustern und visuell darstellen.  
Im Mittelpunkt stehen nicht einzelne GDELT-Events, sondern die automatische Bildung von:

```text
Artikel → Stories → Topics → Themes
```

Das Ziel ist eine skalierbare Intelligence-/Analyseplattform mit:

- Echtzeit-Ereignissen
- Story-Erkennung
- thematischen Zusammenfassungen
- geopolitischer Analyse
- Globe-Visualisierung
- langfristiger Trendanalyse

---

# Grundidee der Datenpipeline

```text
GDELT (Events / Mentions / GKG)
↓
Raw Storage
↓
Artikel-Extraktion
↓
Embedding-Erstellung
↓
Vector DB
↓
Story Clustering
↓
Topic Aggregation
↓
Theme Detection
↓
API
↓
Frontend / Globe / Dashboard
```

---

# Kernarchitektur

```text
backend/
├── gdelt
│   ├── events
│   ├── mentions
│   └── gkg
├── ingestion
├── rawdata
├── article
├── vector
├── story
├── topic
├── theme
├── analytics
├── api
├── auth
└── common
```

---

# Datenmodell

## Raw Layer

```text
gdelt_event_raw
gdelt_mention_raw
gdelt_gkg_raw
ingestion_runs
```

Die Originaldaten von GDELT werden unverändert gespeichert.

---

# Kernobjekte

## Artikel

```text
article
- id
- url
- source
- title
- text
- language
- published_at
- country
- embedding_id
- created_at
```

Artikel sind die atomare Informationseinheit.

---

## Story

Eine Story gruppiert ähnliche Artikel über dasselbe Ereignis.

Beispiel:

```text
Story:
"Russian missile strike hits Kyiv"
```

---

## Topic

Topics aggregieren mehrere Stories.

Beispiel:

```text
Topic:
"Escalation in Ukraine war"
```

---

## Theme

Themes sind langfristige Makro-Muster.

Beispiel:

```text
Theme:
"European security instability"
```

---

# Hierarchie

```text
Article
→ gehört zu Stories

Story
→ besteht aus Artikeln

Topic
→ besteht aus Stories

Theme
→ besteht aus Topics
```

---

# Wichtige Architekturentscheidung

Nicht Event-first bauen.

Richtig:

```text
GDELT liefert Signale
↓
Artikel liefern Inhalt
↓
Stories liefern Bedeutung
↓
Topics liefern Kontext
↓
Themes liefern Langzeitanalyse
```

---

# Verwendung von GDELT

## Events

- Akteure
- Orte
- Event-Typ
- Zeit

## Mentions

- Relevanz
- Intensität
- Reichweite
- Story-Erkennung

## GKG

- Personen
- Organisationen
- Themen
- Emotionen
- Kategorien

---

# Speicherung

## PostgreSQL

```text
articles
stories
topics
themes
story_article
topic_story
theme_topic
event_scores
```

## Vector DB

Mögliche Optionen:

- Qdrant
- Weaviate
- pgvector
- Milvus

---

# Story Clustering

```text
Artikel
↓
Embedding
↓
Similarity Search
↓
Clustering
↓
Story Creation
```

Zusätzliche Kriterien:

- Zeitnähe
- geografische Nähe
- gleiche Akteure
- gleiche Keywords
- gleiche Quellencluster

---

# API-Struktur

```text
GET /api/stories
GET /api/stories/{id}

GET /api/topics
GET /api/themes

GET /api/globe/events
GET /api/globe/clusters

GET /api/analytics/trends
```

---

# MVP-Phasen

## Phase 1

```text
GDELT herunterladen
Events / Mentions / GKG speichern
```

## Phase 2

```text
Artikel extrahieren
Artikel speichern
```

## Phase 3

```text
Embeddings erzeugen
Vector DB integrieren
```

## Phase 4

```text
Story Clustering
Story APIs
Story Globe
```

## Phase 5

```text
Topic Aggregation
```

## Phase 6

```text
Theme Detection
Langzeitanalyse
```

---

# Frontend-Idee

## Globe

Technologie:

```text
react-globe.gl
```

---

# Infrastruktur

## Backend

```text
Spring Boot
Java
Docker
PostgreSQL
```

## Frontend

```text
React
TypeScript
```

---

# Grundprinzip

```text
GDELT ist Input.
Artikel sind Wissen.
Stories sind Bedeutung.
Topics sind Kontext.
Themes sind strategische Analyse.
```
