# Pipeline und Story Clustering

## Grundidee der Datenpipeline

```text
GDELT (Events / Mentions / GKG)
-> Raw Storage
-> Normalisierung
-> Artikel-Projektion
-> Article-Signale
-> Embedding-Erstellung
-> Vector Search
-> Story Clustering
-> Topic Aggregation
-> Theme Detection
-> API
-> Frontend / Globe / Dashboard
```

## Verwendung von GDELT

GDELT-Datensätze sind Rohsignale. Sie müssen nicht dauerhaft gespeichert werden, wenn die relevanten Informationen in `article` und `article_signal` verdichtet wurden.

```text
GDELT Raw/Normalized
-> Article Projection
-> Article Signals
-> Article Embeddings
-> Story Assignment
-> Aggregates
-> Raw-Daten nach Retention löschen
```

GKG, Event und Mention werden best-effort zusammengeführt. Es ist keine harte Annahme, dass zu jedem Artikel alle drei Datenarten vollständig passen.

### Events

- Akteure
- Orte
- Event-Typ
- Zeit
- Intensität
- CAMEO-Klassifikation

### Mentions

- Relevanz
- Intensität
- Reichweite
- Medienverbreitung
- Wiederholung eines Ereignisses über Quellen hinweg

### GKG

- Personen
- Organisationen
- Themen
- Orte
- Emotionen
- Kategorien
- Bilder und Zusatzmetadaten

## Vector Search

MVP:

```text
PostgreSQL + pgvector
```

Spätere Optionen:

- Qdrant
- Weaviate
- Milvus

Die Anwendung sollte Vector Search über ein eigenes Modul kapseln, damit ein späterer Wechsel nicht die Story-Logik berührt.

## Story Clustering

```text
Artikel
-> Article Signal
-> Embedding Input
-> Embedding
-> Similarity Search
-> Score mit Zusatzsignalen
-> Story Assignment oder Story Creation
```

Zusätzliche Kriterien:

- Zeitnähe
- geografische Nähe
- gleiche Akteure
- gleiche Keywords oder GKG-Themes
- gleiche oder verwandte Quellencluster
- Mention-Volumen
- Event- und GKG-Übereinstimmung

Diese Kriterien kommen im MVP überwiegend aus `article_signal`. Die Story selbst speichert diese Rohhinweise nicht nochmal.

## Zeitfenster statt Story-Status

Für den MVP braucht die Story keinen gespeicherten Status wie `active`, `cooling` oder `archived`.

Stattdessen wird beim Clustering mit einem einfachen Zeitfenster gearbeitet:

```text
Neue Artikel nur gegen Stories matchen,
deren neuester Artikel im relevanten Zeitfenster liegt.
```

Beispiel:

```text
candidate stories = stories with latest article published_at >= now - 72h
```

`latest article published_at` wird aus `story_article` und `article` berechnet. Ein gespeichertes Statusmodell kann später ergänzt werden, wenn Performance oder Produktlogik es wirklich brauchen.
