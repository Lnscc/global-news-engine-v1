# ART-005: Article Extraction Health

Status: erledigt
Bereich: articles

## Kontext

Nach einem Lauf muss schnell sichtbar sein, ob alle Staging-Zeilen verarbeitet wurden und ob
Extraction-Errors entstanden sind.

## Ziel

Health-/Status-Abfragen fuer die Artikel-Extraktion bereitstellen.

## Umfang

```text
- pending stage rows je Signaltyp
- article_extraction_errors nach Signaltyp und Fehlercode
- article_signals je Signaltyp
- latest processed source_timestamp
- articles created total
```

## Akzeptanzkriterien

```text
- Status kann ohne manuelle SQL-Snippets abgefragt werden
- Pending-Berechnung beruecksichtigt Signale und Extraction-Errors
- Ergebnis ist geeignet fuer Logs, API oder spaeteres Monitoring
- Tests decken pending, processed und error Faelle ab
```

## Umsetzungskommentar

Umgesetzt am 2026-07-11:

- `GET /articles/extraction/health` stellt den Extraction-Status ohne manuelle SQL-Abfragen bereit.
- Die Antwort enthaelt je Signaltyp Pending-Staging-Zeilen, erfolgreiche Article-Signale,
  Extraction-Errors gruppiert nach Fehlercode und den neuesten verarbeiteten `source_timestamp`.
- Pending-Zeilen schliessen sowohl bereits erfolgreich verarbeitete Signale als auch dokumentierte
  Extraction-Errors aus.
- Die Gesamtzahl erstellter Artikel wird als `articlesCreatedTotal` ausgegeben.
- Die Postman-Collection `docs/postman/Article-API.postman_collection.json` enthaelt den Endpoint
  einschliesslich Contract-Tests.
- Service- und Controller-Tests wurden ergaenzt; der vollstaendige Maven-Testlauf war erfolgreich
  (32 Tests, 0 Fehler).
