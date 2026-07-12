# ART-009: Article API um GKG-Metadaten erweitern

Status: offen
Bereich: articles

## Kontext

ART-011 erschliesst den nahezu flaechendeckenden `PAGE_TITLE` aus GKG-Feld 27. Das fruehere
Crawler-Enrichment-Modell wurde entfernt. Die bestehenden Endpoints liefern bislang nur die
technische Artikelidentitaet und Signale.

## Ziel

Artikelabfragen liefern die im Artikelmodell vorhandenen GKG-Metadaten, zunaechst insbesondere
Titel und transparente Quellenangabe. Crawler-Status- und Fehlerfelder sind nicht Teil des Vertrags.

## Umfang

```text
- ArticleSummary und ArticleDetail um title und titleSource erweitern
- GET /articles und GET /articles/{id} liefern die Felder nullable aus
- Controller-, Query-Service- und Contract-Tests
- Postman-Collection und Postman-Tests aktualisieren
```

## Akzeptanzkriterien

```text
- Titel und Quelle werden fuer Artikel mit GKG-Metadaten ausgegeben
- Artikel ohne Titel bleiben abrufbar und liefern null
- Pagination, Sortierung, Signale und Statuscodes bleiben unveraendert
- Postman-Collection ist aktualisiert und valides JSON
```

## Abhaengigkeit

Dieses Ticket wird nach ART-011 umgesetzt. Ein spaeterer Crawler aus ART-010 erweitert den Vertrag
nur bei einem neu nachgewiesenen Bedarf.
