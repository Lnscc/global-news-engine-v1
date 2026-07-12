# ART-009: Article API um Enrichment erweitern

Status: verworfen
Bereich: articles

## Kontext

ART-008 fuehrte persistierte Crawler-Daten in `article_enrichments` ein. Die bestehenden Endpoints
`GET /articles` und `GET /articles/{id}` sollten diese Daten fuer API-Clients sichtbar machen.

## Urspruengliches Ziel

Artikelabfragen sollten Titel, Publikationszeitpunkt, Sprache, Hauptbild, Volltext sowie den
Crawler-Status und Retry-/Fehlerdaten aus `article_enrichments` ausgeben.

## Urspruenglicher Umfang

```text
- ArticleQueryService liest article_enrichments per LEFT JOIN
- ArticleSummary und ArticleDetail werden um Enrichment-Daten erweitert
- API-Vertrag umfasst Crawler-Inhalte, Status, Attempts und Fehler
- Controller-, Query-Service-, Postman- und Contract-Tests
```

## Verwerfungsgrund

Das zugrunde liegende Crawler-Modell aus ART-007 und ART-008 wurde verworfen und durch Migration V6
entfernt. Reale GKG-Daten liefern `PAGE_TITLE` bereits nahezu flaechendeckend. Eine API-Erweiterung
gegen die nicht mehr existierende Tabelle ist deshalb fachlich und technisch gegenstandslos.

ART-012 uebernimmt die neue API-Aufgabe fuer direkt am Artikel persistierte GKG-Metadaten.
