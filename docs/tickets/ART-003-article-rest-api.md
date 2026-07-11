# ART-003: Article REST API

Status: offen
Bereich: articles

## Kontext

Nach dem Query-Service soll die Artikel-Schicht ueber HTTP abfragbar werden, damit sie von UI,
Tools oder externen Clients genutzt werden kann.

## Ziel

REST-Endpunkte fuer die wichtigsten Artikel-Abfragen bereitstellen.

## Umfang

```text
GET /articles
GET /articles/{id}
GET /articles/domains/top
GET /articles/themes/top
```

## Abhaengigkeiten

```text
- ART-002: Article Query Service
```

## Akzeptanzkriterien

```text
- Endpunkte verwenden den ArticleQueryService
- Responses enthalten stabile DTOs, keine DB-internen Row-Maps
- Pagination/Limit ist begrenzt und validiert
- Tests pruefen Statuscodes, leere Ergebnisse und Detailabruf
```
