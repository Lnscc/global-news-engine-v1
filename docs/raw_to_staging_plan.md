# Raw to Staging Plan

Stand: 2026-06-05

## Ziel

Raw-GDELT-Zeilen aus Events, Mentions und GKG sollen in eine erste typisierte Staging-Schicht ueberfuehrt werden.

Raw bleibt unveraendert und dient als Wiederholungs- und Audit-Basis. Staging ist die erste Schicht, die wir sinnvoll abfragen, pruefen und spaeter in Artikel-, Event- und Story-Modelle ueberfuehren koennen.

## Prinzipien

- Erst Kernfelder, nicht das komplette GDELT-Schema.
- Jede Staging-Zeile referenziert genau eine Raw-Zeile.
- Transformationen sind idempotent.
- Fehlerhafte Raw-Zeilen stoppen den Job nicht.
- Parse-Fehler werden separat gespeichert.
- Staging darf technisch sein; das Produktmodell entsteht spaeter.

## Phase 1: Tabellen

Flyway-Migration `V2__create_gdelt_staging_tables.sql` anlegen.

Tabellen:

- `gdelt_stage_events`
- `gdelt_stage_mentions`
- `gdelt_stage_gkg`
- `gdelt_stage_errors`

Gemeinsame technische Felder:

- `id`
- `raw_id`
- `import_file_id`
- `source_file`
- `source_timestamp`
- `row_number`
- `staged_at`

Constraints:

- Unique pro Dataset auf `raw_id`
- Foreign Key auf die passende Raw-Tabelle
- Foreign Key auf `gdelt_import_files`

## Phase 2: Erste Kernfelder

Events:

- `global_event_id`
- `event_date`
- `actor1_code`
- `actor1_name`
- `actor1_country_code`
- `actor2_code`
- `actor2_name`
- `actor2_country_code`
- `event_code`
- `quad_class`
- `goldstein_scale`
- `avg_tone`
- `source_url`

Mentions:

- `global_event_id`
- `event_time_date`
- `mention_time_date`
- `mention_type`
- `mention_source_name`
- `mention_identifier`
- `confidence`
- `mention_doc_tone`

GKG:

- `gkg_record_id`
- `document_date`
- `source_collection_identifier`
- `source_common_name`
- `document_identifier`
- `themes`
- `persons`
- `organizations`
- `locations`
- `tone`

## Phase 3: Parser

Parser getrennt nach Dataset bauen:

- `GdeltEventParser`
- `GdeltMentionParser`
- `GdeltGkgParser`

Regeln:

- TSV per `split("\t", -1)` parsen, damit leere Felder erhalten bleiben.
- Spaltenanzahl pruefen.
- Zahlen und Datumstypen strikt parsen.
- Leere Strings als `null` behandeln, wenn das Feld optional ist.
- Bei Fehlern eine strukturierte Fehlermeldung erzeugen.

## Phase 4: Transformationsjob

Service `GdeltRawToStagingTransformer` bauen.

Der Job:

- liest nur Raw-Zeilen aus `COMPLETED` Import-Dateien.
- verarbeitet pro Dataset batchweise.
- ignoriert bereits gestagte Raw-Zeilen.
- schreibt parsebare Zeilen in Staging.
- schreibt nicht parsebare Zeilen in `gdelt_stage_errors`.

Start erstmal manuell oder testgetrieben, nicht automatisch nach jedem Import.

## Phase 5: Tests

Unit-Tests:

- Parser akzeptieren gueltige Beispielzeilen.
- Parser behalten leere TSV-Felder korrekt bei.
- Parser melden fehlende oder ungueltige Pflichtfelder.

Integrationstest:

- Raw-Beispielzeilen importieren.
- Transformation ausfuehren.
- Staging-Tabellen enthalten erwartete Zeilen.
- Zweiter Lauf erzeugt keine Duplikate.
- Fehlerhafte Raw-Zeile landet in `gdelt_stage_errors`.

## Nicht Jetzt

- Vollstaendige Normalisierung aller GDELT-Felder
- Artikel- und Story-Modell
- Entity-Deduplizierung
- Performance-Optimierung mit PostgreSQL `COPY`
- Automatische Staging-Transformation nach jedem Poll

## Offene Entscheidungen

- Welche Event-Felder sind fuer den ersten Story-Prototyp wirklich Pflicht?
- Speichern wir GKG-Listenfelder zunaechst als Text oder direkt als JSONB?
- Soll `gdelt_stage_errors` pro Dataset eigene Foreign Keys haben oder nur `dataset_type` plus `raw_id`?
- Wird die Transformation spaeter zeitfensterweise oder dateiweise getriggert?
