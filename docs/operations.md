# Operations

## Start

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Der Import startet nach `gdelt.ingestion.initial-delay` und laeuft danach alle `gdelt.ingestion.poll-interval`.

Fehlgeschlagene Zeitfenster werden bei spaeteren Polls erneut versucht. Die Anzahl wird ueber `gdelt.ingestion.max-failed-windows-per-poll` begrenzt.

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
