# ART-018: GKG-Records vom Artikel trennen

Status: offen
Bereich: gdelt, articles

## Kontext

GKG-Daten werden derzeit uneinheitlich gespeichert: Themes, Personen, Organisationen, Orte und
Tone landen im generischen `article_signals`, waehrend `PAGE_TITLE` aus dem GKG-Staging nach
`articles.title` kopiert wird. Damit liegt der Titel nicht mehr am verursachenden GKG-Record und
existiert als zweite Wahrheit am Artikel.

## Festgehaltene Modellentscheidung

```text
- ein Article ist die normalisierte Identitaet einer Artikel-URL
- ein GKG-Record ist eine konkrete GDELT-Analyse dieses Dokuments
- GKG-Metadaten gehoeren zum GKG-Record und nicht als Kopie zum Article
- die Article API darf GKG-Werte weiterhin als abgeleitete Projektion ausgeben
- eigene Tabellen fuer GKG-Mehrfachwerte werden nur bei konkretem Abfragebedarf eingefuehrt
```

## Ziel

GKG bekommt ein eigenes persistiertes Record-Modell. Der GKG-Titel wird dort gespeichert und fuer
die Article API deterministisch abgeleitet. `articles.title` und `articles.title_source` werden
anschliessend entfernt.

## Zielmodell

```text
gdelt_gkg_records
- id
- source_id
- article_id
- source_timestamp
- document_identifier
- page_title
- themes_raw
- persons_raw
- organizations_raw
- locations_raw
- tone_raw
- tone_value
- created_at
```

Die Raw-Felder bleiben in diesem Ticket bewusst erhalten. Ihre gezielte Normalisierung erfolgt in
kleineren Folgetickets.

## Umfang

```text
- gdelt_gkg_records mit Constraints und Indizes anlegen
- neue GKG-Extraktion direkt in dieses Modell schreiben lassen
- vorhandene GKG-Signale idempotent backfillen
- mehrere GKG-Records pro Artikel erhalten
- Titelprojektion fuer ArticleSummary und ArticleDetail auf GKG umstellen
- q-Titelsuche aus ART-013 auf GKG-Records umstellen
- articles.title und articles.title_source nach erfolgreicher Umstellung entfernen
- betroffene Health- und Debug-Abfragen anpassen
- Migrations-, Backfill-, Extractor-, Query-, Controller- und Contract-Tests
- Postman-Collection und Postman-Tests bei Vertragsaenderungen aktualisieren
```

## Fachliche Regeln

```text
- source_id identifiziert den GKG-Quelldatensatz idempotent
- ein Artikel kann mehrere GKG-Records besitzen
- fuer den API-Titel gewinnt der frueheste nicht-leere GKG-Titel nach source_timestamp und id
- Artikel ohne GKG-Titel bleiben abrufbar und liefern title = null
- titleSource bleibt in der API vorerst GKG, wird aber nicht am Artikel persistiert
```

## Akzeptanzkriterien

```text
- jeder persistierte GKG-Wert ist seinem GKG-Record zugeordnet
- Backfill und Neuimport erzeugen dasselbe Ergebnis
- mehrere GKG-Records werden nicht zu einem Record zusammengefuehrt
- API und Titelsuche liefern ihre Titel aus dem GKG-Modell
- articles enthaelt keine persistierte GKG-Titelkopie mehr
- Pagination, Sortierung, total und bestehende Statuscodes bleiben unveraendert
- Tests decken fehlende und konkurrierende GKG-Titel ab
- Postman-Collection bleibt valides JSON
```

## Folgearbeiten

- ART-019 normalisiert Themes fuer Suche und Aggregation.
- ART-020 entscheidet feldweise ueber Personen, Organisationen und Orte.
- ART-001 behandelt die getrennte Typisierung von EVENTS und MENTIONS.

## Abgrenzung

Theme-Normalisierung, Personen-/Organisationssuche, strukturierte Orte, Event-/Mention-Tabellen,
Story-Clustering und externe Webseitenabrufe sind nicht Teil dieses Tickets.
