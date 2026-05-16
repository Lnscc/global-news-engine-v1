# Pipeline und Story Clustering

## Grundidee der Datenpipeline

```text
GDELT (Events / Mentions / GKG)
-> Raw Storage
-> Normalisierung
-> Artikel-Projektion
-> Embedding-Erstellung
-> Vector Search
-> Story Clustering
-> Topic Aggregation
-> Theme Detection
-> API
-> Frontend / Globe / Dashboard
```

## Verwendung von GDELT

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

## Story-Lifecycle

```text
active
-> neue passende Artikel werden regelmäßig zugeordnet

cooling
-> Story bekommt kaum neue Artikel, bleibt aber noch Kandidat für Zuordnung

archived
-> Story wird nicht mehr automatisch erweitert, bleibt aber abfragbar
```

Ohne Lifecycle wird es schwer zu entscheiden, ob ein neuer Artikel zu einer alten Story gehört oder eine neue Story starten soll.
