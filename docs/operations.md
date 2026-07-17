# Operations

## Start

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Der Import startet nach `gdelt.ingestion.initial-delay` und laeuft danach alle `gdelt.ingestion.poll-interval`.

Fehlgeschlagene Zeitfenster werden bei spaeteren Polls erneut versucht. Die Anzahl wird ueber `gdelt.ingestion.max-failed-windows-per-poll` begrenzt.

## Tests

```powershell
docker compose up -d
.\mvnw.cmd verify
```

Der PostgreSQL-Integrationstest nutzt die lokale Compose-Datenbank unter `localhost:5432`, legt ein temporaeres Schema an und entfernt es nach dem Test wieder.

## Importstatus

Letzte erfolgreich importierte Dateien:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select dataset_type, source_timestamp, row_count, completed_at, source_file from gdelt_import_files where status = 'COMPLETED' order by completed_at desc limit 10;"
```

Letzte vollstaendig importierte Zeitfenster:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select source_timestamp, count(*) as completed_files, sum(row_count) as rows from gdelt_import_files where status = 'COMPLETED' group by source_timestamp having count(*) = 3 order by source_timestamp desc limit 10;"
```

Fehlgeschlagene Dateien:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select dataset_type, source_timestamp, status, error_message, completed_at, source_file from gdelt_import_files where status = 'FAILED' order by completed_at desc limit 20;"
```

Import-Zusammenfassung:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select status, dataset_type, count(*) as files, sum(row_count) as rows from gdelt_import_files group by status, dataset_type order by status, dataset_type;"
```

## Processing-Fehler

EVENTS verwenden `gdelt_event_payloads` als Rohdatenspeicher und `gdelt_events` als dauerhafte
Fachtabelle. Eine erfolgreich geparste EVENTS-Zeile besitzt in beiden Tabellen dieselbe ID;
`gdelt_events` enthaelt kein `raw_tsv` und keinen technischen Verarbeitungsstatus. MENTIONS und
GKG verwenden bis zu ihren jeweiligen Modellmigrationen weiterhin die Raw-/Staging-Tabellen.

Jeder fehlgeschlagene Parsing-Versuch wird dauerhaft in `gdelt_processing_errors` protokolliert.
Die Rohzeile selbst bleibt ausschließlich in der jeweiligen Raw-Tabelle und wird nicht in der
Fehlerhistorie dupliziert. Der Staging-Job versucht noch nicht erfolgreich gestagte Zeilen bei
späteren Läufen erneut. Zwischen zwei Versuchen derselben fehlerhaften Quellzeile liegt mindestens
`gdelt.staging.retry-delay` (Standard: `PT1M`), damit ein Scheduler-Lauf keine unmittelbare
Retry-Schleife erzeugt. Bei Erfolg erhalten alle offenen Fehler derselben Kombination aus
`dataset_type` und `source_row_id` einen Wert in `resolved_at`.

Offene Fehler:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select dataset_type, failed_step, error_code, count(*) as attempts from gdelt_processing_errors where resolved_at is null group by dataset_type, failed_step, error_code order by dataset_type, failed_step, error_code;"
```

Fehlerhistorie einer Quellzeile:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select id, dataset_type, source_row_id, failed_step, error_code, occurred_at, resolved_at from gdelt_processing_errors where dataset_type = 'EVENTS' and source_row_id = 4711 order by occurred_at, id;"
```

Historische und weiterhin offene Versuche:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select dataset_type, resolved_at is null as open, count(*) as attempts from gdelt_processing_errors group by dataset_type, resolved_at is null order by dataset_type, open desc;"
```

EVENTS-Payloads ohne Fachzeile:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select payload.id, payload.source_file, payload.row_number, payload.ingested_at from gdelt_event_payloads payload left join gdelt_events event on event.id = payload.id where event.id is null order by payload.id limit 100;"
```
