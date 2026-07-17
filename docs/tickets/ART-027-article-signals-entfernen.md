# ART-027: Article-Zuordnungen in Fachmodelle verschieben

Status: offen
Bereich: articles, gdelt, architecture

## Kontext

`article_signals` wurde eingefuehrt, als EVENTS, MENTIONS und GKG aus getrennten Raw- und
Staging-Modellen in eine gemeinsame Artikelprojektion ueberfuehrt wurden. Seit ART-023 bis ART-025
besitzen alle drei GDELT-Datensaetze stabile, dauerhafte Fachzeilen. GKG speichert seine
Artikelzuordnung bereits direkt als `gdelt_gkg.article_id`; EVENTS und MENTIONS verwenden dagegen
weiterhin die separate Tabelle `article_signals`.

Dadurch ist das Modell asymmetrisch. `article_signals` dupliziert ausser der Artikelzuordnung auch
Werte, die bereits in `gdelt_events` beziehungsweise `gdelt_mentions` vorliegen, darunter
`source_timestamp`, `global_event_id`, `event_code` und Tone. Die Tabelle enthaelt trotz ihres
Namens keine GKG-Signale.

## Ziel

EVENTS, MENTIONS und GKG speichern ihre nullable Artikelzuordnung einheitlich in der jeweiligen
Fachzeile. Article-Queries, Health-Abfragen und Debug-Views projizieren den gemeinsamen
Signalstrom direkt aus diesen drei Fachmodellen. Nach einem validierten Backfill wird
`article_signals` entfernt.

```text
gdelt_events.article_id   -> articles.id
gdelt_mentions.article_id -> articles.id
gdelt_gkg.article_id      -> articles.id
```

## Modellregeln

```text
- article_id ist nullable, solange eine Fachzeile noch nicht extrahiert wurde
- eine erfolgreiche Extraktion setzt genau eine Artikelzuordnung an der Fachzeile
- ungueltige oder leere URLs bleiben in article_extraction_errors nachvollziehbar
- eine Fachzeile gilt als bearbeitet, wenn article_id gesetzt ist oder ein Extraction-Error existiert
- die Signalidentitaet ist das Paar (signalType, sourceId)
- sourceId entspricht der stabilen ID aus gdelt_events, gdelt_mentions oder gdelt_gkg
- das bestehende API-Feld id bleibt erhalten und wird fuer alle Signaltypen aus sourceId projiziert
- mehrere Fachzeilen duerfen auf denselben deduplizierten Artikel zeigen
```

`id` ist damit nicht global ueber verschiedene Signaltypen eindeutig. Clients muessen
`signalType` und `sourceId` gemeinsam als Identitaet verwenden. Diese bereits fuer die
Quellidentitaet geltende Regel wird im REST-Vertrag explizit dokumentiert und getestet.

## Umfang

```text
- nullable article_id-FKs in gdelt_events und gdelt_mentions mit geeigneten Indizes anlegen
- bestehende EVENTS- und MENTIONS-Zuordnungen aus article_signals vollstaendig backfillen
- vor dem Entfernen Mengen, Quellreferenzen und Artikelzuordnungen validieren
- Article-Extraktion direkt auf gdelt_events.article_id und gdelt_mentions.article_id umstellen
- idempotente Auswahl unbearbeiteter Fachzeilen ohne article_signals erhalten
- ArticleDetail-Signale direkt aus gdelt_events, gdelt_mentions und gdelt_gkg projizieren
- Signaltyp-Filter und alle Article-Aggregationen auf die drei Fachmodelle umstellen
- article_signal_summary_view und article_detail_view neu aufbauen
- Article-Extraction-Health ohne article_signals berechnen
- article_signals nach erfolgreicher Migration entfernen
- veraltete Dokumentation und Analyseskripte aktualisieren
- Migrations-, Extractor-, Query-, Controller-, Health-, View- und PostgreSQL-Integrationstests anpassen
- Postman-Collection und Postman-Tests fuer die Signalidentitaet aktualisieren
- Postman-Collection als valides JSON pruefen
```

## Migrationsregeln

```text
- jede bestehende EVENTS-Zeile in article_signals uebertraegt article_id auf gdelt_events
- jede bestehende MENTIONS-Zeile in article_signals uebertraegt article_id auf gdelt_mentions
- fehlende oder mehrdeutige Quellreferenzen brechen die Migration vor dem DROP ab
- bereits gesetzte, abweichende article_id-Werte brechen die Migration ab
- die Anzahl zugeordneter EVENTS und MENTIONS bleibt unveraendert
- article_extraction_errors bleibt erhalten und referenziert weiterhin die stabile Fachzeilen-ID
- articles und gdelt_gkg werden inhaltlich nicht veraendert
- der Tabellen-DROP erfolgt erst nach erfolgreicher Validierung und View-Umstellung
```

## API-Projektion

Der bestehende Response-Aufbau von `GET /articles/{id}` bleibt erhalten. Die Werte werden jedoch
nicht mehr aus einer duplizierten Signalzeile, sondern direkt aus dem jeweiligen Fachmodell
gelesen:

```text
EVENTS:
  sourceTimestamp <- gdelt_events.source_timestamp
  globalEventId   <- gdelt_events.global_event_id
  eventCode       <- gdelt_events.event_code
  toneValue       <- gdelt_events.avg_tone

MENTIONS:
  sourceTimestamp <- gdelt_mentions.source_timestamp
  globalEventId   <- gdelt_mentions.global_event_id
  toneValue       <- gdelt_mentions.mention_doc_tone

GKG:
  unveraendert aus gdelt_gkg
```

## Akzeptanzkriterien

```text
- jede bisherige EVENTS- und MENTIONS-Artikelzuordnung ist vollstaendig erhalten
- neue EVENTS-, MENTIONS- und GKG-Zuordnungen folgen demselben article_id-Modell
- wiederholte Extraktion erzeugt weder neue Zuordnungen noch doppelte API-Signale
- article_signals existiert nach der Migration nicht mehr
- ArticleDetail liefert fuer alle drei Signaltypen dieselben fachlichen Werte wie zuvor
- id und sourceId entsprechen in der Signalprojektion der stabilen Fachzeilen-ID
- Signaltyp-Filter, Summary-View, Detail-View und Extraction-Health funktionieren ohne article_signals
- Artikel ohne Signale und Fachzeilen mit Extraction-Errors werden korrekt behandelt
- Fremdschluessel und Indizes unterstuetzen Article-Detail- und Extraktionsabfragen
- PostgreSQL-Migrationstests pruefen Backfill, Konfliktabbruch und verlustfreien Tabellen-DROP
- bestehende Statuscodes und die Struktur des Article-REST-Vertrags bleiben erhalten
- Postman-Collection und Postman-Tests sind aktualisiert und die Collection ist valides JSON
```

## Abhaengigkeiten und Reihenfolge

Das Ticket baut auf den abgeschlossenen Tickets ART-023, ART-024 und ART-025 auf. Es sollte vor
ART-016 umgesetzt werden, damit die geplanten Signalzusammenfassungen nicht mehr auf dem
asymmetrischen Zwischenmodell aufgebaut werden.

## Abgrenzung

Neue Signalmetriken, Relevanzbewertung, Story-Clustering, externe Webseitenabrufe und eine
fachliche Erweiterung der GDELT-Daten sind nicht Teil dieses Tickets. ART-016 implementiert die
Listenaggregate anschliessend auf dem vereinheitlichten Modell.
