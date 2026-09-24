# ART-040: Story REST API bereitstellen

Status: offen
Bereich: stories

## Kontext

Nach der Promotion einer Clustering-Version sind Stories produktsichtbar, koennen aber noch nicht
ueber HTTP abgefragt werden. Nach ART-041 ist nur der aktuelle Stand erforderlich;
abgeloeste Story-IDs benoetigen keine Nachfolgeraufloesung.

## Ziel

Eine lesende REST API stellt Stories der aktuellen `ACTIVE`-Clustering-Version mit stabilen DTOs,
Mitgliedern bereit.

## Umfang

```text
GET /stories
GET /stories/{id}
```

- Story-Liste begrenzt und validiert paginieren
- Story-Detail mit Zustand, Zeitraum, repraesentativem Artikel und aktuellen Mitgliedern liefern
- abgeloeste IDs mit `404` beantworten, ohne Nachfolgerverweise
- ausschliesslich die aktuelle `ACTIVE`-Version als produktsichtbar behandeln
- API-Vertrag und Betriebsdokumentation ergaenzen
- Postman-Collection und Postman-Tests aktualisieren

## Akzeptanzkriterien

- `GET /stories` liefert eine stabil sortierte, paginierte Liste der aktuellen Stories
- `GET /stories/{id}` liefert Story-Metadaten und aktuelle Mitgliedschaften
- eine unbekannte ID liefert `404`
- ungueltige Pagination liefert `400`
- eine abgeloeste ID liefert `404`; weiterverwendete IDs liefern den aktualisierten Stand
- Shadow- und ausgemusterte Versionen erscheinen nicht als aktuelle Stories
- Responses verwenden stabile DTOs und keine DB-internen Row-Maps
- Controller- und PostgreSQL-Integrationstests pruefen Statuscodes, leere Ergebnisse,
  aktuelle Mitgliedschaften, aktualisierte Stories und abgeloeste IDs
- die Postman-Collection prueft die betroffenen Statuscodes und Response-Vertraege
- die aktualisierte Postman-Collection ist valides JSON

## Abgrenzung

Schreibende Story-Endpunkte, Clustering-Steuerung, manuelle Freigabe-Endpunkte,
Story-Zusammenfassungen und UI sind nicht enthalten.

## Offene Fragen

Keine.
