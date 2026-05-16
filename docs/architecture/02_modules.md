# Module und Verantwortlichkeiten

Die Modulstruktur sollte nah am bestehenden Spring-Boot-/Spring-Modulith-Code bleiben. GDELT wird intern nach Pipeline-Schritten organisiert, die Produktdomäne nach fachlichen Objekten.

```text
backend/
  gdelt/
    discovery/
    download/
    parser/
    normalization/
    model/
    repository/
  ingestion/
  articles/
  embeddings/
  stories/
  topics/
  themes/
  analytics/
  api/
  common/
```

## Modulverantwortung

```text
gdelt
- GDELT-Manifeste finden
- Dateien herunterladen
- Events, Mentions und GKG parsen
- normalisierte GDELT-Datensätze speichern

ingestion
- Source-Batches
- Raw-Dateien
- Staging-Zeilen
- Job-Status und Wiederholbarkeit

articles
- Artikel-URLs normalisieren
- Artikel aus GKG/Mentions/Events projizieren
- Artikel-Metadaten pflegen
- spätere Volltext-Extraktion anbinden

embeddings
- Embedding-Input aus Artikeldaten bauen
- Embeddings erzeugen und versionieren
- Vector Search kapseln

stories
- ähnliche Artikel zu Stories clustern
- Story-Zuordnung über Zeitfenster steuern
- Story-Scores und Story-Signale berechnen

topics
- mehrere Stories zu Themen aggregieren
- mittelfristige Lagebilder erzeugen

themes
- langfristige Makro-Muster erkennen
- strategische Analyseebene bereitstellen

analytics
- Trends, Zeitreihen, Heatmaps und Kennzahlen

api
- REST- oder GraphQL-Endpunkte fuer Frontend und externe Clients

common
- geteilte Basistypen, Zeitlogik, Fehlerbehandlung, Utilities
```

## Infrastruktur

### Backend

```text
Spring Boot
Spring Modulith
Java
Docker
PostgreSQL
pgvector
```

### Frontend

```text
React
TypeScript
react-globe.gl
```
