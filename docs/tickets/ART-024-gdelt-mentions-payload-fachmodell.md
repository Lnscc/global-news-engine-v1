# ART-024: GDELT MENTIONS auf Payload- und Fachmodell umstellen

Status: offen
Bereich: gdelt, architecture

## Kontext

MENTIONS werden derzeit zuerst in `gdelt_raw_mentions` und nach erfolgreichem Parsing in
`gdelt_stage_mentions` gespeichert. Dieselbe Quellzeile besitzt dadurch zwei IDs. Ungeparste
Rohdaten und vollstaendige fachliche Mentions sollen stattdessen klar getrennt werden, ohne die
Identitaet beim Uebergang zu wechseln.

## Ziel

MENTIONS verwenden kuenftig:

```text
gdelt_mention_payloads  -> temporaere unveraenderte Quellzeilen
gdelt_mentions          -> dauerhafte erfolgreich geparste Fachzeilen
```

Der Import legt die Payload-ID an. Nach erfolgreichem Parsing wird dieselbe ID als
`gdelt_mentions.id` verwendet. Die Existenz einer Zeile in `gdelt_mentions` belegt die
erfolgreiche Verarbeitung; ein `processing_status` wird nicht gespeichert.

## Payload-Modell

```text
gdelt_mention_payloads
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

`gdelt_mentions` enthaelt:

```text
- id aus gdelt_mention_payloads
- import_file_id
- source_file
- source_timestamp
- row_number
- ingested_at
- parsed_at
- alle heutigen typisierten Felder aus gdelt_stage_mentions
```

Die Tabelle enthaelt weder `raw_tsv` noch `processing_status`.

## Umfang

```text
- gdelt_mention_payloads und gdelt_mentions mit Constraints und Abfrageindizes anlegen
- vorhandene gdelt_raw_mentions mit unveraenderter ID in gdelt_mention_payloads migrieren
- vorhandene gdelt_stage_mentions mit der jeweiligen Raw-ID in gdelt_mentions migrieren
- MENTIONS-Importer direkt in gdelt_mention_payloads schreiben lassen
- Mention-Parser aus Payloads lesen und vollstaendige gdelt_mentions-Zeilen idempotent anlegen lassen
- Mention-Fehler und erfolgreiche Wiederholungen an gdelt_processing_errors anbinden
- MENTIONS-source_id in article_signals von Stage-ID auf gdelt_mentions.id migrieren
- Mention-Article-Extraktion und Mention-Health-Abfragen umstellen
- Mention-bezogene Debug-Views und Analyseskripte umstellen
- Alt- und Zielbestand vollstaendig vergleichen
- gdelt_raw_mentions und gdelt_stage_mentions nach erfolgreicher Validierung entfernen
- Import-, Parser-, Migrations-, Extraktions-, Health-, View- und PostgreSQL-Tests aktualisieren
```

## Migrationsregeln

```text
- jede Raw-Zeile wird genau einmal mit ihrer bisherigen ID als Payload uebernommen
- jede Staging-Zeile erzeugt genau eine Mention mit der ID ihrer Raw-Zeile
- raw_tsv und Importmetadaten bleiben unveraendert erhalten
- vorhandene geparste Werte werden ohne erneutes Parsen uebernommen
- article_signals.source_id zeigt danach auf gdelt_mentions.id
- Raw-Zeilen ohne Staging-Ergebnis bleiben ausschliesslich als Payload erhalten
- Neuimport, Retry und Neustart erzeugen keine Duplikate
```

## Akzeptanzkriterien

```text
- gdelt_mentions enthaelt ausschliesslich erfolgreich geparste Mentions
- jede Mention-ID entspricht der ID ihrer urspruenglichen Payload
- jede bisherige Raw-Zeile ist genau einmal als Payload vorhanden
- jede bisherige Staging-Zeile ist genau einmal als Mention vorhanden
- bestehende Mention-Artikelzuordnungen bleiben erhalten
- gdelt_mentions enthaelt weder raw_tsv noch processing_status
- fehlerhafte Mention-Payloads bleiben erneut verarbeitbar
- doppelte Imports und wiederholte Verarbeitung erzeugen keine Duplikate
- Article API und bestehende Statuscodes bleiben unveraendert
- PostgreSQL-Integrationstests decken Mention-Import, Parsing und Extraktion ab
```

## Abhaengigkeit

Das Ticket baut auf `gdelt_processing_errors` aus ART-021 auf.

## Abgrenzung

EVENTS, GKG, Payload-Retention, weitere fachliche Mention-Normalisierung und Aenderungen am
Article-REST-Contract sind nicht Teil dieses Tickets.
