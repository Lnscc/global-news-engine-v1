# ART-001: Signal-Details typisieren

Status: offen
Bereich: articles

## Kontext

`article_signals` ist in der ersten Version bewusst eine gemeinsame Tabelle fuer Events,
Mentions und GKG. Dadurch entstehen nullable Spalten, weil nicht jeder Signaltyp dieselben
Felder liefert.

Das ist fuer V1 akzeptabel, weil es einen einfachen chronologischen Signal-Stream pro Artikel
ermoeglicht und die Idempotenz ueber `(signal_type, source_id)` einfach bleibt.

## Zielbild

Pruefen, ob das Modell spaeter auf einen gemeinsamen Signal-Kern plus typisierte Detailtabellen
umgestellt werden soll:

```text
article_signals
- id
- article_id
- signal_type
- source_id
- source_timestamp
- created_at

article_event_signal_details
article_mention_signal_details
article_gkg_signal_details
```

## Ausloeser

```text
- article_signals bekommt zu viele typspezifische nullable Spalten
- Queries werden dauerhaft unuebersichtlich
- typabhaengige Constraints werden wichtig
- typspezifische Indexierung oder Performance wird messbar relevant
```

## Akzeptanzkriterien

```text
- Entscheidung dokumentiert: gemeinsame Tabelle behalten oder Detailtabellen einfuehren
- Falls Detailtabellen eingefuehrt werden: Migration und Backfill-Strategie beschrieben
- Query-Pfade fuer Artikel-Detail und Story-Bildung bleiben klar
- Idempotenz ueber signal_type/source_id bleibt erhalten oder wird gleichwertig ersetzt
```
