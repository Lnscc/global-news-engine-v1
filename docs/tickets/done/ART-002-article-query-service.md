# ART-002: Article Query Service

Status: erledigt
Bereich: articles

## Kontext

Die Artikel-Persistenz und Extraktion existieren. Aktuell muessen Artikel und Signale aber direkt
per SQL abgefragt werden. Fuer API, UI und spaetere Story-Bildung braucht es eine klare
Query-Schicht.

## Ziel

Einen `ArticleQueryService` mit DTOs fuer die wichtigsten Lesezugriffe bauen.

## Umfang

```text
- latest articles by first_seen_at
- article detail with signals
- top domains
- top themes from article_signals
```

## Akzeptanzkriterien

```text
- Service liefert paginierbare latest articles
- Service liefert Article-Detail inklusive Signalen
- Service liefert top domains mit Counts
- Service liefert top themes mit Counts
- Tests decken leere DB, mehrere Signaltypen und Sortierung ab
```
