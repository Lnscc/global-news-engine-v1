# ART-021: GDELT-Tabellen konsolidieren

Status: offen
Bereich: gdelt, architecture

## Kontext

Jeder GDELT-Datensatz durchlaeuft derzeit mehrere persistierte Tabellen. Die unveraenderte
Quellzeile liegt in einer `gdelt_raw_*`-Tabelle, die geparsten Felder liegen anschliessend in einer
`gdelt_stage_*`-Tabelle. Fuer GKG existiert mit `gdelt_gkg_records` zusaetzlich ein dauerhaftes
Produktmodell. Dadurch werden Identitaet, Importmetadaten und Verarbeitungszustand derselben
Quellzeile ueber mehrere IDs und Joins verteilt.

## Ziel

Pro GDELT-Datensatztyp gibt es genau eine Tabelle, die die Quellzeile, die geparsten Felder und den
aktuellen Verarbeitungszustand enthaelt. Die bisherigen Tabellen werden wie folgt
zusammengefuehrt:

| Aktuell | Ziel |
|---|---|
| `gdelt_raw_events` + `gdelt_stage_events` | `gdelt_events` |
| `gdelt_raw_mentions` + `gdelt_stage_mentions` | `gdelt_mentions` |
| `gdelt_raw_gkg` + `gdelt_stage_gkg` + `gdelt_gkg_records` | `gdelt_gkg` |

Eine heruntergeladene GDELT-Zeile besitzt damit vom Import bis zur fachlichen Nutzung eine stabile
Identitaet. Parsing und nachgelagerte Verarbeitung aktualisieren dieselbe Zeile, statt weitere
Darstellungen mit eigenen IDs anzulegen.

## Zielmodell

Alle drei Zieltabellen enthalten mindestens die gemeinsamen Import- und Lebenszyklusfelder:

```text
- id
- import_file_id
- source_file
- source_timestamp
- row_number
- raw_tsv
- ingested_at
- parsed_at
- processing_status
```

Daneben behaelt jede Tabelle ihre heute in `gdelt_stage_*` vorhandenen, typisierten Fachfelder.
`gdelt_gkg` enthaelt zusaetzlich alle normalisierten Spalten und Artikelbeziehungen aus
`gdelt_gkg_records`, beispielsweise `article_id`, `themes`, `persons`, `organizations`,
`locations`, die Tone-Komponenten sowie Titel-, Publikationszeit- und Bildmetadaten.

Die konkreten erlaubten Werte und Uebergaenge von `processing_status` werden in Migration und Code
zentral festgelegt. Mindestens muessen noch nicht geparste, erfolgreich geparste, fehlerhafte und
fachlich verarbeitete Zeilen eindeutig unterscheidbar sein.

## Umfang

```text
- drei neue Zieltabellen mit Constraints, Fremdschluesseln und Abfrageindizes anlegen
- vorhandene Raw-, Staging- und GKG-Produktdaten verlustfrei in die Zieltabellen migrieren
- Raw-Zeilen ohne Staging-Ergebnis sowie Parsingfehler als nachvollziehbaren Zustand erhalten
- source_id-Verweise aus article_signals fuer EVENTS und MENTIONS auf die neuen stabilen IDs umstellen
- GKG-Verweise und Article-Projektionen direkt auf gdelt_gkg umstellen
- Importer so umstellen, dass neue Quellzeilen direkt in die jeweilige Zieltabelle geschrieben werden
- Parser/Transformer so umstellen, dass Fachfelder und Verarbeitungsstatus derselben Zeile aktualisiert werden
- Article-Extraktion, Health-Abfragen, Debug-Views und Analyseskripte auf die Zieltabellen umstellen
- Bedeutung von gdelt_stage_errors.raw_id an die neue Identitaet anpassen oder Fehlerdaten kontrolliert integrieren
- Alt- und Zielbestand vor dem Entfernen der sieben ersetzten Tabellen vollstaendig vergleichen
- ersetzte Raw-, Staging- und GKG-Record-Tabellen erst nach erfolgreicher Validierung entfernen
- Import-, Parser-, Migrations-, Extraktions-, Health-, View- und PostgreSQL-Integrationstests aktualisieren
- Architektur- und Artikeldokumentation auf das konsolidierte Modell umstellen
```

## Migrationsregeln

```text
- pro (source_file, row_number) entsteht genau eine Zeile in der passenden Zieltabelle
- raw_tsv und Importmetadaten bleiben fuer jede vorhandene Quellzeile unveraendert erhalten
- vorhandene geparste Werte werden ohne erneutes Parsen uebernommen
- vorhandene IDs duerfen nur mit einer expliziten und getesteten Zuordnung ersetzt werden
- article_signals.source_id zeigt nach der Migration auf die passende Zeile in gdelt_events oder gdelt_mentions
- vorhandene GKG-Artikelzuordnungen und normalisierte Metadaten bleiben vollstaendig erhalten
- Neuimport, Wiederholung und Neustart bleiben idempotent
- fehlerhafte Zeilen werden nicht stillschweigend geloescht oder als erfolgreich markiert
- der Rueckbau der Alttabellen erfolgt erst nach Anzahl-, Zuordnungs- und Pflichtfeldpruefungen
```

## Akzeptanzkriterien

```text
- gdelt_events, gdelt_mentions und gdelt_gkg sind die einzigen fachlichen Zeilentabellen fuer die drei Datensaetze
- jede bisherige Raw-Zeile ist genau einmal in der zugehoerigen Zieltabelle vorhanden
- alle bisherigen Staging-Felder und GKG-Produktfelder stimmen nach der Migration wertgleich ueberein
- bestehende EVENTS-, MENTIONS- und GKG-Zuordnungen zu Artikeln bleiben erhalten
- Import und Parsing erzeugen keine zweite persistierte Darstellung derselben Quellzeile mehr
- doppelte Imports und wiederholte Verarbeitung erzeugen weder Duplikate noch inkonsistente Statuswerte
- Parsingfehler und noch nicht verarbeitete Zeilen bleiben unterscheidbar und erneut verarbeitbar
- Health-Metriken weisen pending, processed und failed weiterhin fachlich korrekt aus
- Article API, Suche, Filter, Pagination, Sortierung und bestehende Statuscodes bleiben unveraendert
- Migrationstests pruefen vollstaendige Datenuebernahme, Referenzintegritaet und das Entfernen der Alttabellen
- PostgreSQL-Integrationstests decken den gesamten Pfad Download, Import, Parsing und Extraktion ab
- Dokumentation nennt nur noch die konsolidierten Tabellen als aktuelles Modell
```

## Abhaengigkeiten

Das Ticket baut auf dem normalisierten GKG-Modell aus ART-018 bis ART-020 sowie den spaeter
ergänzten Publikationszeit- und Bildmetadaten auf. Deren bestehende Spalten und fachliche Regeln
werden in `gdelt_gkg` uebernommen.

## Abgrenzung

Die fachliche Normalisierung weiterer Event- oder Mention-Felder, Aenderungen am Article-REST-
Contract, neue Suchfunktionen, neue Retention-Regeln fuer `raw_tsv` und eine Zusammenlegung von
`gdelt_import_files` sind nicht Teil dieses Tickets. `article_signals` wird nur soweit angepasst,
wie es fuer gueltige EVENTS- und MENTIONS-Referenzen erforderlich ist.
