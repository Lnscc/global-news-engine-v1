# Operations

## Start

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Der Import startet nach `gdelt.ingestion.initial-delay` und laeuft danach alle
`gdelt.ingestion.poll-interval`.

Fehlgeschlagene Zeitfenster werden bei spaeteren Polls erneut versucht. Die Anzahl wird ueber
`gdelt.ingestion.max-failed-windows-per-poll` begrenzt.

## Payload-Retention

Der Retention-Job loescht erfolgreich verarbeitete Payloads standardmaessig sieben Tage nach
`parsed_at`. Die dauerhafte Fachzeile mit derselben ID und die vollstaendige Fehlerhistorie in
`gdelt_processing_errors` bleiben erhalten. Payloads ohne passende Fachzeile werden unabhaengig
von ihrem Alter nicht geloescht.

```properties
gdelt.retention.enabled=true
gdelt.retention.initial-delay=PT2M
gdelt.retention.poll-interval=PT1H
gdelt.retention.period=PT168H
gdelt.retention.batch-size=1000
gdelt.retention.max-batches-per-run=10
```

Ein Batch verarbeitet je Datensatztyp hoechstens `gdelt.retention.batch-size` Zeilen in der
deterministischen Reihenfolge `parsed_at, id`. Jeder Datensatztyp wird in einer eigenen, begrenzten
Transaktion geloescht. Pro Scheduler-Lauf werden maximal
`gdelt.retention.max-batches-per-run` solcher Batches ausgefuehrt. Das Abschluss-Log weist die
geloeschten EVENTS-, MENTIONS- und GKG-Payloads getrennt aus.

Loeschbare und aufgrund der Frist oder fehlender Fachzeile zurueckgehaltene Payloads fuer die
Standardfrist von sieben Tagen:

```powershell
docker compose exec postgres psql -U gne -d gne -c "with retention as (select current_timestamp - interval '7 days' as cutoff), payloads as (select 'EVENTS' as dataset_type, domain_row.parsed_at from gdelt_event_payloads payload left join gdelt_events domain_row on domain_row.id = payload.id union all select 'MENTIONS', domain_row.parsed_at from gdelt_mention_payloads payload left join gdelt_mentions domain_row on domain_row.id = payload.id union all select 'GKG', domain_row.parsed_at from gdelt_gkg_payloads payload left join gdelt_gkg domain_row on domain_row.id = payload.id) select dataset_type, count(*) filter (where parsed_at <= cutoff) as eligible_payload_rows, count(*) filter (where parsed_at is null or parsed_at > cutoff) as retained_payload_rows from payloads cross join retention group by dataset_type order by dataset_type;"
```

Nach dem Cleanup ist `raw_tsv` nicht mehr in der Datenbank vorhanden. Eine Wiederherstellung ist
nur aus einem externen Datenbank-Backup oder durch erneuten Import der unveraenderten Quelldatei
moeglich; Object-Storage-Archivierung ist nicht Bestandteil des Jobs. Vor einer Verkuerzung der
Frist muessen deshalb Backup- und Reimport-Anforderungen betrieblich geprueft werden.

## Tests

```powershell
docker compose up -d
.\mvnw.cmd verify
```

Die PostgreSQL-Integrationstests nutzen die lokale Compose-Datenbank unter `localhost:5432`, legen
temporaere Schemas an und entfernen sie nach dem Test wieder. Der gemeinsame Pipeline-Test deckt
Download, Payload-Import, Parsing, Normalisierung und Article-Extraktion fuer EVENTS, MENTIONS und
GKG ab.

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

## Pipeline-Health

EVENTS, MENTIONS und GKG verwenden ein gemeinsames Persistenzmodell: Die temporaeren Payloads
liegen in `gdelt_event_payloads`, `gdelt_mention_payloads` und `gdelt_gkg_payloads`; erfolgreich
geparste und normalisierte Fachzeilen liegen mit derselben ID in `gdelt_events`, `gdelt_mentions`
und `gdelt_gkg`. `gdelt_pipeline_health_view` weist Payloads, Payloads ohne Fachzeile, offene
Processing-Fehler und vorhandene Fachzeilen je Datensatztyp getrennt aus:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select dataset_type, payload_rows, pending_payload_rows, open_processing_errors, domain_rows from gdelt_pipeline_health_view order by dataset_type;"
```

## Processing-Fehler

Jeder fehlgeschlagene Parsing-Versuch wird dauerhaft in `gdelt_processing_errors` protokolliert.
Die unveraenderte Quellzeile liegt bis zu einer erfolgreichen Verarbeitung und dem anschliessenden
Ablauf der Retention ausschliesslich in der jeweiligen Payload-Tabelle; sie wird nicht in der
Fehlerhistorie dupliziert. Der Parser versucht Payloads ohne Fachzeile bei spaeteren Laeufen erneut.
Zwischen zwei Versuchen derselben fehlerhaften Quellzeile liegt mindestens
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

Payloads ohne Fachzeile fuer alle Datensatztypen:

```powershell
docker compose exec postgres psql -U gne -d gne -c "select * from gdelt_pipeline_health_view where pending_payload_rows > 0 order by dataset_type;"
```
