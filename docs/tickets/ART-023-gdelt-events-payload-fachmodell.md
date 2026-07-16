# ART-023: GDELT EVENTS auf Payload- und Fachmodell umstellen

Status: offen
Bereich: gdelt, architecture

## Kontext

EVENTS werden derzeit zuerst in `gdelt_raw_events` und nach erfolgreichem Parsing in
`gdelt_stage_events` gespeichert. Dieselbe Quellzeile besitzt dadurch zwei IDs. Ungeparste
Rohdaten und vollstaendige fachliche Events sollen stattdessen klar getrennt werden, ohne die
Identitaet beim Uebergang zu wechseln.

## Ziel

EVENTS verwenden kuenftig:

```text
gdelt_event_payloads  -> temporaere unveraenderte Quellzeilen
gdelt_events          -> dauerhafte erfolgreich geparste Fachzeilen
```

Der Import legt die Payload-ID an. Nach erfolgreichem Parsing wird dieselbe ID als
`gdelt_events.id` verwendet. Die Existenz einer Zeile in `gdelt_events` belegt die erfolgreiche
Verarbeitung; ein `processing_status` wird nicht gespeichert.

## Payload-Modell

```text
gdelt_event_payloads
- id
- import_file_id
- source_file
- source_timestamp
- row_number
- raw_tsv
- ingested_at
```

Die Tabelle enthaelt keine geparsten Felder oder technischen Verarbeitungsattribute.

## Fachmodell

`gdelt_events` enthaelt:

```text
- id aus gdelt_event_payloads
- import_file_id
- source_file
- source_timestamp
- row_number
- ingested_at
- parsed_at
- alle heutigen typisierten Felder aus gdelt_stage_events
```

Die Tabelle enthaelt weder `raw_tsv` noch `processing_status`.

## Umfang

```text
- gdelt_event_payloads und gdelt_events mit Constraints und Abfrageindizes anlegen
- vorhandene gdelt_raw_events mit unveraenderter ID in gdelt_event_payloads migrieren
- vorhandene gdelt_stage_events mit der jeweiligen Raw-ID in gdelt_events migrieren
- EVENTS-Importer direkt in gdelt_event_payloads schreiben lassen
- Event-Parser aus Payloads lesen und vollstaendige gdelt_events-Zeilen idempotent anlegen lassen
- Event-Fehler und erfolgreiche Wiederholungen an gdelt_processing_errors anbinden
- EVENTS-source_id in article_signals von Stage-ID auf gdelt_events.id migrieren
- Event-Article-Extraktion und Event-Health-Abfragen umstellen
- Event-bezogene Debug-Views und Analyseskripte umstellen
- Alt- und Zielbestand vollstaendig vergleichen
- gdelt_raw_events und gdelt_stage_events nach erfolgreicher Validierung entfernen
- Import-, Parser-, Migrations-, Extraktions-, Health-, View- und PostgreSQL-Tests aktualisieren
```

## Migrationsregeln

```text
- jede Raw-Zeile wird genau einmal mit ihrer bisherigen ID als Payload uebernommen
- jede Staging-Zeile erzeugt genau ein Event mit der ID ihrer Raw-Zeile
- raw_tsv und Importmetadaten bleiben unveraendert erhalten
- vorhandene geparste Werte werden ohne erneutes Parsen uebernommen
- article_signals.source_id zeigt danach auf gdelt_events.id
- Raw-Zeilen ohne Staging-Ergebnis bleiben ausschliesslich als Payload erhalten
- Neuimport, Retry und Neustart erzeugen keine Duplikate
```

## Akzeptanzkriterien

```text
- gdelt_events enthaelt ausschliesslich erfolgreich geparste Events
- jede Event-ID entspricht der ID ihrer urspruenglichen Payload
- jede bisherige Raw-Zeile ist genau einmal als Payload vorhanden
- jede bisherige Staging-Zeile ist genau einmal als Event vorhanden
- bestehende Event-Artikelzuordnungen bleiben erhalten
- gdelt_events enthaelt weder raw_tsv noch processing_status
- fehlerhafte Event-Payloads bleiben erneut verarbeitbar
- doppelte Imports und wiederholte Verarbeitung erzeugen keine Duplikate
- Article API und bestehende Statuscodes bleiben unveraendert
- PostgreSQL-Integrationstests decken Event-Import, Parsing und Extraktion ab
```

## Abhaengigkeit

Das Ticket baut auf `gdelt_processing_errors` aus ART-021 auf.

## Abgrenzung

MENTIONS, GKG, Payload-Retention, weitere fachliche Event-Normalisierung und Aenderungen am
Article-REST-Contract sind nicht Teil dieses Tickets.
