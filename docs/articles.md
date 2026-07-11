# Artikel-Schicht

## Ziel

Die Artikel-Schicht ist der erste Schritt vom GDELT-Import zum produktnahen Modell.
Aus `gdelt_stage_events`, `gdelt_stage_mentions` und `gdelt_stage_gkg` werden deduplizierte
Artikel abgeleitet.

Ein Artikel ist in dieser Phase primaer eine kanonische URL mit Metadaten und Signalen aus GDELT.
Fulltext-Crawling, Embeddings, LLM-Zusammenfassungen und Story-Clustering kommen spaeter.

## Rolle im Zielbild

```text
GDELT staging rows
-> Artikel
-> Stories
-> Topics
-> Themes
```

GDELT liefert die Signale. Die Artikel-Schicht bildet daraus stabile, deduplizierte Einheiten,
auf denen spaeter Stories aufgebaut werden.

## Datenmodell

### `articles`

```text
id
canonical_url
url_hash
domain
first_seen_at
created_at
updated_at
```

`canonical_url` ist die normalisierte URL. `url_hash` ist ein SHA-256-Hash der kanonischen URL
und dient als Unique Key fuer idempotente Upserts. `first_seen_at` ist der frueheste
`source_timestamp` aller Signale zu diesem Artikel.

### `article_signals`

```text
id
article_id
signal_type        EVENTS | MENTIONS | GKG
source_id
source_timestamp
global_event_id nullable
event_code nullable
themes nullable
persons nullable
organizations nullable
locations nullable
tone_value nullable
tone_raw nullable
created_at
```

`articles` bleibt dedupliziert und stabil. `article_signals` speichert die GDELT-Hinweise,
aus denen spaeter Stories, Topics und Themes berechnet werden.

`source_id` referenziert die jeweilige Staging-Zeile:

```text
EVENTS   -> gdelt_stage_events.id
MENTIONS -> gdelt_stage_mentions.id
GKG      -> gdelt_stage_gkg.id
```

Die Tabelle bekommt einen fachlichen Unique Key auf `(signal_type, source_id)`. Dadurch kann
derselbe Artikel beliebig viele Mention-Signale haben, aber dieselbe Staging-Zeile wird bei
erneuten Job-Laeufen nicht doppelt eingefuegt.

### `article_extraction_errors`

```text
id
signal_type        EVENTS | MENTIONS | GKG
source_id
source_timestamp
raw_url nullable
error_code
error_message
created_at
```

Fehler in dieser Tabelle bedeuten: Die Staging-Zeile war gueltig, konnte aber nicht in einen
Artikel ueberfuehrt werden, zum Beispiel wegen einer leeren oder ungueltigen URL.
Auch hier verhindert ein Unique Key auf `(signal_type, source_id)` doppelte Fehlerzeilen bei
erneuten Job-Laeufen.

## URL-Normalisierung

Eine Komponente `ArticleUrlNormalizer` normalisiert URLs vor dem Upsert.

Regeln fuer die erste Version:

```text
- trimmen
- Fragment entfernen (#...)
- Tracking-Parameter entfernen (utm_*, fbclid, gclid)
- Host lowercase
- Query-Parameter stabil sortieren
- leere Query entfernen
- Default-Ports entfernen (:80 fuer http, :443 fuer https)
- trailing slash am Pfad konservativ normalisieren
- http und https zunaechst nicht aggressiv mergen
- nur http und https akzeptieren
```

Die letzte Regel verhindert falsche Deduplikate. Eine staerkere Normalisierung kann spaeter
nachgezogen werden, wenn reale Daten zeigen, welche Domains sich sicher zusammenfuehren lassen.

## Article-Extractor-Job

Der Job verarbeitet fertig gestagte GDELT-Zeilen und erzeugt Artikel plus Signale.

```text
GDELT staging rows
-> URL extrahieren
-> canonical URL berechnen
-> article upsert
-> article_signal idempotent einfuegen
```

Quellen:

```text
gdelt_stage_events.source_url
gdelt_stage_mentions.mention_identifier
gdelt_stage_gkg.document_identifier
```

Der Job muss idempotent sein. Ein zweiter Lauf ueber dieselben Staging-Zeilen darf keine
doppelten Artikel oder Signale erzeugen.

## Tests

Die erste Testabdeckung soll diese Faelle pruefen:

```text
- gleiche URL mit utm_* Parametern wird ein Artikel
- Event, Mention und GKG zur gleichen URL erzeugen einen Artikel mit mehreren Signalen
- mehrere Mention-Zeilen zur gleichen URL erzeugen mehrere Signale am selben Artikel
- zweiter Lauf erzeugt keine Duplikate
- leere oder kaputte URLs werden in `article_extraction_errors` protokolliert
```

## Minimaler Nutzen

Nach der Persistenzschicht soll eine einfache Query- oder API-Schicht folgen:

```text
- latest articles by first_seen_at
- article detail with signals
- top domains
- top themes from article signals
```

## Bewusst spaeter

Diese Themen gehoeren nicht in die erste Artikel-Iteration:

```text
- Fulltext-Crawling
- Embeddings
- LLM-Summaries
- Story-Clustering
- Topic- und Theme-Aggregation
```

## Zielmodell fuer Article Enrichment

### Entscheidung

Angereicherte Inhalte werden spaeter in einer separaten 1:1-Tabelle `article_enrichments`
gespeichert. `articles` bleibt damit die stabile, ausschliesslich aus GDELT-Signalen abgeleitete
Identitaet eines Artikels. Die Trennung verhindert, dass Crawl-Zustaende, grosse Texte und
wiederholbare Fehlerbehandlung die erste Extraktionsschicht belasten.

Die erste Enrichment-Version verwendet eine aktuelle Zeile je Artikel. Versionierung von
Crawler-Ergebnissen ist nicht Teil dieses Zielmodells; falls sie spaeter benoetigt wird, kann eine
Historientabelle ergaenzt werden, ohne `articles` zu veraendern.

### `article_enrichments`

```text
article_id              PK und FK -> articles.id
title                   nullable
published_at            nullable
language                nullable, BCP-47-Sprachcode soweit ermittelbar
main_image_url           nullable
extracted_text           nullable
status                   PENDING | PROCESSING | SUCCEEDED | FAILED
attempt_count            Anzahl gestarteter Versuche
last_attempt_at          nullable
next_attempt_at          nullable, fuer Retry-Planung
error_code               nullable
error_message            nullable
enriched_at              nullable, Zeitpunkt des letzten Erfolgs
created_at
updated_at
```

`extracted_text` liegt zunaechst direkt in `article_enrichments`: Es gehoert zum selben
Crawler-Ergebnis und benoetigt in der geplanten Version weder eigene Kardinalitaet noch eigenen
Lebenszyklus. Eine separate Content- oder Object-Storage-Loesung wird erst eingefuehrt, wenn
reale Textgroessen, Versionierung oder unabhaengige Verarbeitung dies rechtfertigen.

Bei `SUCCEEDED` werden Fehlerfelder und `next_attempt_at` geleert. Ein fehlender einzelner
Metadatenwert ist erlaubt, solange der Crawl insgesamt verwertbar ist. Bei `FAILED` werden ein
stabiler `error_code`, eine begrenzte technische Fehlermeldung und gegebenenfalls
`next_attempt_at` gespeichert. Permanente Fehler haben keinen naechsten Versuch. Ein Worker muss
eine Zeile vor dem Abruf atomar von `PENDING` oder retry-faelligem `FAILED` auf `PROCESSING`
setzen, damit parallele Worker denselben Artikel nicht gleichzeitig verarbeiten.

### Getrennte Verarbeitung

```text
GDELT staging -> ArticleExtractorService -> articles + article_signals
                                             |
                                             v
                                  Enrichment-Queue/Worker
                                             |
                                             v
                                    article_enrichments
```

Der bestehende `ArticleExtractorService` schreibt weiterhin nur `articles`, `article_signals`
und `article_extraction_errors`. Er ruft keine Webseiten ab und kennt keine Enrichment-Felder.
Ein separater, unabhaengig planbarer Worker waehlt Artikel ohne Enrichment-Zeile beziehungsweise
retry-faellige Enrichments aus, crawlt die `canonical_url` und persistiert das Ergebnis. Dadurch
bleiben GDELT-Import und -Extraktion auch dann funktionsfaehig, wenn Zielseiten langsam, gesperrt
oder fehlerhaft sind.

### Migrationspfad

```text
1. Additive Flyway-Migration fuer `article_enrichments`, Status-Constraint und Index auf
   `(status, next_attempt_at)` anlegen; bestehende Artikel und APIs bleiben unveraendert.
2. Enrichment-Repository und Worker separat vom GDELT-Extractor implementieren. Der Worker darf
   fehlende Zeilen bei Bedarf als `PENDING` anlegen; ein verpflichtender Volltabellen-Backfill ist
   fuer das Deployment nicht erforderlich.
3. Crawl-/Parser-Adapter mit Zeitlimits, Groessenlimit, Retry-Klassifizierung und Tests ergaenzen.
4. Query-Service und REST-Vertrag erst danach additiv um nullable Enrichment-Daten erweitern und
   dabei die Postman-Collection samt Contract-Tests aktualisieren.
5. Bestehende Artikel kontrolliert in Batches enqueuen. Metriken fuer Pending, Processing,
   Succeeded, Failed, Retry-Alter und Fehlertypen beobachten.
6. Falls Textvolumen oder Versionierung es erfordern, `extracted_text` spaeter additiv in eine
   Content-Tabelle oder Object Storage auslagern; die Metadatenzeile behaelt dabei ihre 1:1-ID.
```

Weitere geplante Arbeiten liegen unter `docs/tickets`, zum Beispiel
`ART-001-signal-details-typisieren.md`.

## Naechster Implementierungsschritt

```text
1. Migration V3__create_articles.sql anlegen
2. ArticleUrlNormalizer implementieren
3. Tests fuer URL-Normalisierung schreiben
4. Article-Extractor-Service mit idempotenten Upserts bauen
5. Service-Tests fuer Artikel, Signale und Extraction-Errors ergaenzen
```
