# API, Frontend und MVP-Roadmap

## API-Struktur

```text
GET /api/stories
GET /api/stories/{id}
GET /api/stories/{id}/articles

GET /api/topics
GET /api/topics/{id}

GET /api/themes
GET /api/themes/{id}

GET /api/globe/stories
GET /api/globe/clusters

GET /api/analytics/trends
GET /api/analytics/heatmap
```

Für den Globe sollte die API story-zentriert sein. Einzelne GDELT-Events können als Debug- oder Detaildaten ergänzt werden, sollten aber nicht die primäre Kartenebene sein.

## Frontend-Idee

### Globe

Technologie:

```text
react-globe.gl
```

Primäre Visualisierung:

```text
Story-Punkte
Story-Cluster
Intensität
Zeitverlauf
Story-Detailpanel
```

## MVP-Phasen

### Phase 1: GDELT Ingestion

```text
GDELT-Manifeste lesen
Dateien herunterladen
Events / Mentions / GKG parsen
Raw- und Normalized-Layer speichern
Jobs wiederholbar machen
```

### Phase 2: Article Projection

```text
Artikel aus GKG/Mentions/Events ableiten
URLs kanonisieren
Artikel deduplizieren
Artikel-Metadaten speichern
GDELT-Signale mit Artikeln verknüpfen
```

### Phase 3: Embeddings

```text
Embedding-Input aus Artikel-Metadaten bauen
Embeddings erzeugen
pgvector integrieren
Similarity Search testen
```

### Phase 4: Story Clustering

```text
Story-Datenmodell einführen
Artikel zu Stories zuordnen
Story-Lifecycle implementieren
Story-Scores berechnen
Story APIs bereitstellen
```

### Phase 5: Globe MVP

```text
Story-zentrierte Globe API
Koordinaten aus Story-Signalen ableiten
aktive Stories auf dem Globe anzeigen
Cluster und Detailansicht bereitstellen
```

### Phase 6: Topic Aggregation

```text
Stories zu Topics gruppieren
Topic-Zusammenfassungen erzeugen
Topic-Trends sichtbar machen
```

### Phase 7: Theme Detection

```text
Topics langfristig analysieren
Makro-Muster erkennen
Langzeitanalyse und strategische Lagebilder erzeugen
```

## Umsetzungsregel

```text
Erst Stories stabil bekommen.
Dann Topics bauen.
Themes zuletzt.
```
