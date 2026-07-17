# ART-021: Dauerhafte GDELT-Processing-Fehlerhistorie

Status: erledigt
Bereich: gdelt, architecture

## Kontext

Fehler beim Parsen von GDELT-Zeilen werden derzeit in `gdelt_stage_errors` gespeichert. Pro
Raw-Zeile kann dort nur ein Fehler existieren. Wiederholte Versuche, spaetere erfolgreiche
Verarbeitung und der jeweils fehlgeschlagene Verarbeitungsschritt sind nicht dauerhaft
nachvollziehbar.

Die geplante Umstellung auf temporaere Payload- und dauerhafte Fachtabellen benoetigt eine
Fehlerhistorie, die unabhaengig von der spaeteren Loeschung einer Payload erhalten bleibt.

## Ziel

`gdelt_stage_errors` wird durch `gdelt_processing_errors` ersetzt. Jeder fehlgeschlagene
Verarbeitungsversuch wird als eigene dauerhafte Zeile gespeichert.

Die Fehlerhistorie ist kein Verarbeitungsstatus der fachlichen GDELT-Zeile. Sie dokumentiert
ausschliesslich konkrete fehlgeschlagene Versuche.

## Zielmodell

```text
gdelt_processing_errors
- id
- dataset_type
- source_row_id
- import_file_id
- source_file
- source_timestamp
- row_number
- failed_step
- error_code
- error_message
- occurred_at
- resolved_at
```

`source_row_id` bezeichnet die stabile ID der GDELT-Quellzeile. Bis zur Umstellung der einzelnen
Datensatztypen entspricht sie der jeweiligen Raw-ID. Nach der Umstellung entspricht sie der
gemeinsamen ID in Payload- und Fachtabelle.

`resolved_at` zeigt an, dass die Quellzeile spaeter erfolgreich verarbeitet wurde. Der historische
Fehler bleibt dabei unveraendert erhalten.

## Fachliche Regeln

```text
- jeder fehlgeschlagene Versuch erzeugt eine eigene Fehlerzeile
- mehrere Fehler fuer dieselbe source_row_id sind erlaubt
- dataset_type und source_row_id identifizieren gemeinsam die betroffene Quellzeile
- failed_step benennt den fehlgeschlagenen Schritt, beispielsweise PARSING oder NORMALIZATION
- ein erfolgreicher Wiederholungsversuch setzt resolved_at fuer alle offenen Fehler der Quellzeile
- historische Fehler werden weder bei Erfolg noch durch spaetere Payload-Retention geloescht
- raw_tsv wird nicht in der Fehlerhistorie dupliziert
```

## Umfang

```text
- gdelt_processing_errors mit Constraints und Abfrageindizes anlegen
- vorhandene gdelt_stage_errors verlustfrei migrieren
- Parserfehler als neue Versuche statt als eindeutigen Fehler pro Raw-Zeile speichern
- erfolgreiche Wiederholungen als aufgeloest markieren
- Retry-Auswahl so anpassen, dass fehlerhafte Payloads erneut verarbeitet werden koennen
- Health-Abfragen auf offene und historische Fehler umstellen
- Debug-Abfragen und Dokumentation anpassen
- Migrations-, Retry-, Health- und PostgreSQL-Integrationstests ergaenzen
- gdelt_stage_errors nach erfolgreicher Validierung entfernen
```

## Migrationsregeln

```text
- jede vorhandene gdelt_stage_errors-Zeile wird genau einmal uebernommen
- source_row_id entspricht fuer migrierte Fehler der bisherigen raw_id
- vorhandene Fehlercodes, Meldungen und Zeitstempel bleiben wertgleich erhalten
- migrierte Fehler sind zunaechst offen und besitzen resolved_at = null
- der Rueckbau von gdelt_stage_errors erfolgt erst nach Anzahl- und Pflichtfeldpruefungen
```

## Akzeptanzkriterien

```text
- mehrere fehlgeschlagene Versuche derselben Quellzeile bleiben einzeln nachvollziehbar
- ein spaeter erfolgreicher Versuch entfernt keine historischen Fehler
- aufgeloeste und offene Fehler sind eindeutig unterscheidbar
- die Fehlerhistorie enthaelt keine Kopie von raw_tsv
- bestehende Fehler werden vollstaendig und wertgleich migriert
- fehlerhafte Quellzeilen koennen erneut verarbeitet werden
- Health-Metriken weisen offene Fehler fachlich korrekt aus
- PostgreSQL-Integrationstests decken Fehler, Retry und spaeteren Erfolg ab
```

## Folgearbeiten

- ART-023 stellt EVENTS auf Payload- und Fachmodell um.
- ART-024 stellt MENTIONS auf Payload- und Fachmodell um.
- ART-025 stellt GKG auf Payload- und Fachmodell um.
- ART-026 konsolidiert gemeinsame Abfragen, Tests und Dokumentation.
- ART-022 fuehrt anschliessend die Payload-Retention ein.

## Abgrenzung

Payload- und Fachtabellen, automatische Payload-Loeschung, eine Retention fuer
`gdelt_processing_errors` und Aenderungen am Article-REST-Contract sind nicht Teil dieses Tickets.

## Implementierungskommentar

Migration V15 ersetzt `gdelt_stage_errors` durch die dauerhafte, append-only Tabelle
`gdelt_processing_errors`. Bestehende Fehler werden vor dem Entfernen der Alttabelle vollständig
übernommen und anhand von Anzahl und Pflichtfeldern validiert; `raw_tsv` wird nicht dupliziert.
V14 bleibt als arbeitslose Kompatibilitätsmigration unter ihrer historisch bereits angewendeten
Identität `add gkg article language` reserviert. Dadurch validieren bestehende Datenbanken korrekt,
während neue Installationen direkt mit V15 in das aktuelle Schema migrieren.

Der Staging-Job schließt fehlgeschlagene Quellzeilen nicht mehr dauerhaft aus. Jeder erneute
fehlgeschlagene Parsing-Versuch erzeugt eine eigene Fehlerzeile mit `failed_step = PARSING`.
`gdelt.staging.retry-delay` verhindert unmittelbare Wiederholungen innerhalb desselben Job-Laufs.
Ein später erfolgreicher Versuch setzt `resolved_at` für alle offenen Fehler derselben
`dataset_type`/`source_row_id`-Kombination. Die bestehende Extraction-Health-Antwort berücksichtigt
offene Processing-Fehler und historische Versuche beim letzten verarbeiteten Quellzeitpunkt, ohne
ihren REST-Contract zu verändern. Migrations-, Retry-, Health- und PostgreSQL-Integrationstests
decken Migration, mehrere Versuche und späteren Erfolg ab.
