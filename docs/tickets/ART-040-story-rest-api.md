# ART-040: Story REST API bereitstellen

Status: offen
Bereich: stories

## Kontext

Nach der Promotion einer Clustering-Version sind Stories produktsichtbar, koennen aber noch nicht
ueber HTTP abgefragt werden. Alte, durch Merge oder Split abgeloeste Story-IDs muessen weiterhin
nachvollziehbar bleiben.

## Ziel

Eine lesende REST API stellt Stories der aktuellen `ACTIVE`-Clustering-Version mit stabilen DTOs,
Mitgliedern und nachvollziehbarer Lineage bereit.

## Umfang

```text
GET /stories
GET /stories/{id}
```

- Story-Liste begrenzt und validiert paginieren
- Story-Detail mit Zustand, Zeitraum, repraesentativem Artikel und aktuellen Mitgliedern liefern
- `SUPERSEDED`-IDs mit ihren direkten Nachfolgern aufloesbar halten
- ausschliesslich die aktuelle `ACTIVE`-Version als produktsichtbar behandeln
- API-Vertrag und Betriebsdokumentation ergaenzen
- Postman-Collection und Postman-Tests aktualisieren

## Akzeptanzkriterien

- `GET /stories` liefert eine stabil sortierte, paginierte Liste der aktuellen Stories
- `GET /stories/{id}` liefert Story-Metadaten und aktuelle Mitgliedschaften
- eine unbekannte ID liefert `404`
- ungueltige Pagination liefert `400`
- eine bekannte `SUPERSEDED`-ID bleibt abrufbar und nennt ihre Nachfolger
- Shadow- und ausgemusterte Versionen erscheinen nicht als aktuelle Stories
- Responses verwenden stabile DTOs und keine DB-internen Row-Maps
- Controller- und PostgreSQL-Integrationstests pruefen Statuscodes, leere Ergebnisse,
  Mitgliedschaften und Lineage
- die Postman-Collection prueft die betroffenen Statuscodes und Response-Vertraege
- die aktualisierte Postman-Collection ist valides JSON

## Abgrenzung

Schreibende Story-Endpunkte, Clustering-Steuerung, manuelle Freigabe-Endpunkte,
Story-Zusammenfassungen und UI sind nicht enthalten.

## Offene Fragen

Keine.
