# ART-005: Article Extraction Health

Status: offen
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
