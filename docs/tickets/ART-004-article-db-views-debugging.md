# ART-004: Article DB Views fuer Debugging

Status: offen
Bereich: articles

## Kontext

Artikel koennen in DBeaver aktuell nur ueber laengere Joins mit `article_signals` und den
GDELT-Staging-Tabellen inspiziert werden. Views wuerden Debugging und Datenvalidierung
vereinfachen.

## Ziel

Lesbare Datenbank-Views fuer haeufige Artikel-Abfragen anlegen.

## Umfang

```text
- article_signal_summary_view
- article_detail_view
- optional: article_event_signals_view
- optional: article_mention_signals_view
- optional: article_gkg_signals_view
```

## Akzeptanzkriterien

```text
- Views werden per Flyway-Migration angelegt
- Views sind read-only und veraendern kein Persistenzmodell
- DBeaver-Abfragen fuer Artikel-Detail werden deutlich kuerzer
- Tests oder Migrationsvalidierung stellen sicher, dass Views auf H2/Postgres funktionieren
```
