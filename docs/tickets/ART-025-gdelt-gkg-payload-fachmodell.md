# ART-025: GDELT GKG auf Payload- und Fachmodell umstellen

Status: offen
Bereich: gdelt, architecture

## Kontext

GKG-Daten werden derzeit in `gdelt_raw_gkg`, `gdelt_stage_gkg` und `gdelt_gkg_records`
gespeichert. Identitaet, unveraenderte Quelle, geparste Felder, normalisierte Produktwerte und
Artikelbeziehung derselben GDELT-Zeile sind dadurch ueber drei Tabellen verteilt.

## Ziel

GKG verwendet kuenftig:

```text
gdelt_gkg_payloads  -> temporaere unveraenderte Quellzeilen
gdelt_gkg           -> dauerhafte erfolgreich geparste und normalisierte Fachzeilen
```

Der Import legt die Payload-ID an. Nach erfolgreichem Parsing und Normalisieren wird dieselbe ID
als `gdelt_gkg.id` verwendet. Die Existenz einer Zeile in `gdelt_gkg` belegt die erfolgreiche
Verarbeitung; ein `processing_status` wird nicht gespeichert.

Article-Extraktion und das Anlegen von `article_id` bleiben nachgelagerte Schritte. Eine gueltige
GKG-Zeile darf daher zunaechst ohne Artikelbeziehung existieren.

## Payload-Modell

```text
gdelt_gkg_payloads
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

`gdelt_gkg` enthaelt:

```text
- id aus gdelt_gkg_payloads
- import_file_id
- source_file
- source_timestamp
- row_number
- ingested_at
- parsed_at
- alle heutigen Felder aus gdelt_stage_gkg
- article_id und alle normalisierten Felder aus gdelt_gkg_records
- Themes, Personen, Organisationen und Orte
- alle Tone-Komponenten
- Titel-, Publikationszeit- und Bildmetadaten
```

Die Tabelle enthaelt weder `raw_tsv`, `processing_status` noch eine zusaetzliche `source_id`.

## Umfang

```text
- gdelt_gkg_payloads und gdelt_gkg mit Constraints und Abfrageindizes anlegen
- vorhandene gdelt_raw_gkg mit unveraenderter ID in gdelt_gkg_payloads migrieren
- vorhandene gdelt_stage_gkg mit der jeweiligen Raw-ID in gdelt_gkg migrieren
- normalisierte Werte und Artikelbeziehungen aus gdelt_gkg_records wertgleich integrieren
- GKG-Importer direkt in gdelt_gkg_payloads schreiben lassen
- GKG-Parser und Normalisierer aus Payloads lesen und vollstaendige gdelt_gkg-Zeilen idempotent anlegen lassen
- GKG-Fehler und erfolgreiche Wiederholungen an gdelt_processing_errors anbinden
- GKG-Article-Extraktion direkt auf gdelt_gkg.article_id umstellen
- Article-Projektionen, Suche, Filter, Health-Abfragen und Debug-Views umstellen
- GKG-bezogene Analyseskripte umstellen
- Alt- und Zielbestand vollstaendig vergleichen
- gdelt_raw_gkg, gdelt_stage_gkg und gdelt_gkg_records nach erfolgreicher Validierung entfernen
- Parser-, Normalisierungs-, Migrations-, Extraktions-, Query-, Health-, View- und PostgreSQL-Tests aktualisieren
```

## Migrationsregeln

```text
- jede Raw-Zeile wird genau einmal mit ihrer bisherigen ID als Payload uebernommen
- jede Staging-Zeile erzeugt genau eine GKG-Fachzeile mit der ID ihrer Raw-Zeile
- raw_tsv und Importmetadaten bleiben unveraendert erhalten
- vorhandene geparste und normalisierte Werte werden ohne erneutes Parsen uebernommen
- jede gdelt_gkg_records.source_id wird explizit von Stage-ID auf die zugehoerige Raw-ID abgebildet
- bestehende Artikelbeziehungen bleiben vollstaendig erhalten
- Raw-Zeilen ohne Staging-Ergebnis bleiben ausschliesslich als Payload erhalten
- Neuimport, Retry und Neustart erzeugen keine Duplikate
```

## Akzeptanzkriterien

```text
- gdelt_gkg enthaelt ausschliesslich erfolgreich geparste und normalisierte GKG-Zeilen
- jede GKG-ID entspricht der ID ihrer urspruenglichen Payload
- jede bisherige Raw-Zeile ist genau einmal als Payload vorhanden
- jede bisherige Staging-Zeile ist genau einmal als GKG-Fachzeile vorhanden
- alle bisherigen GKG-Produktwerte und Artikelbeziehungen bleiben wertgleich erhalten
- gdelt_gkg enthaelt weder raw_tsv, processing_status noch source_id
- mehrere GKG-Records pro Artikel bleiben erhalten
- fehlerhafte GKG-Payloads bleiben erneut verarbeitbar
- doppelte Imports und wiederholte Verarbeitung erzeugen keine Duplikate
- Article API, Suche, Filter, Pagination, Sortierung und Statuscodes bleiben unveraendert
- PostgreSQL-Integrationstests decken GKG-Import, Parsing, Normalisierung und Extraktion ab
```

## Abhaengigkeiten

Das Ticket baut auf `gdelt_processing_errors` aus ART-021 und dem normalisierten GKG-Modell aus
ART-018 bis ART-020 sowie den spaeter ergaenzten Publikationszeit- und Bildmetadaten auf.

## Abgrenzung

EVENTS, MENTIONS, Payload-Retention, weitere fachliche GKG-Normalisierung und Aenderungen am
Article-REST-Contract sind nicht Teil dieses Tickets.
