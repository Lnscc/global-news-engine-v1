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
title nullable
title_source nullable
first_seen_at
created_at
updated_at
```

`canonical_url` ist die normalisierte URL. `url_hash` ist ein SHA-256-Hash der kanonischen URL
und dient als Unique Key fuer idempotente Upserts. `first_seen_at` ist der frueheste
`source_timestamp` aller Signale zu diesem Artikel.

`title` enthaelt den aus GKG-Feld 27 extrahierten, HTML-dekodierten und getrimmten `PAGE_TITLE`
(maximal 1.000 Zeichen). `title_source` ist dafuer `GKG`. Bei mehreren Titeln fuer dieselbe
kanonische URL gewinnt deterministisch das GKG-Signal mit der kleinsten Staging-ID; ein bereits
gesetzter Titel wird nicht stillschweigend ersetzt.

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

## Quellenmatrix fuer Enrichment

Die strukturierten Staging-Felder liefern URLs, Beobachtungszeitpunkte und Signale. Die vollstaendig
gespeicherten GKG-Rohzeilen enthalten darueber hinaus in Feld 27 (`V2EXTRASXML`) nahezu immer einen
`PAGE_TITLE` und optional weitere Seitenmetadaten. Details und Messwerte stehen in
`docs/analysis/ART-011-gkg-xml-extras.md`. Signalzeiten werden nicht als Publikationszeitpunkt
umgedeutet.

| Zielfeld | GDELT-Quelle | Crawler-Quelle | Prioritaet und Konfliktregel |
|---|---|---|---|
| `canonicalUrl` | `source_url`, `mention_identifier`, `document_identifier` | finale URL nur fuer relative Verweise | bestehende Artikelidentitaet gewinnt |
| `domain` | aus kanonischer URL | keine | bestehender Artikelwert gewinnt |
| `title` | GKG-Feld 27: `PAGE_TITLE` | spaeter optional | GDELT nach HTML-Dekodierung ist primaer |
| `publishedAt` | `PAGE_PRECISEPUBTIMESTAMP` ist zu analysieren; Signalzeiten sind ungeeignet | spaeter optional | noch nicht festgelegt |
| `language` | kein derzeit persistiertes Feld | spaeter optional | noch nicht verfuegbar |
| `mainImageUrl` | nicht nachgewiesen | spaeter optional | noch nicht verfuegbar |
| `extractedText` | keines | spaeter optional | noch nicht verfuegbar |

`themes`, `persons`, `organizations`, `locations` und `tone` bleiben GDELT-Signale. Ein externer
Crawler ist aktuell nicht aktiv und wird erst bei nachgewiesenem Bedarf wieder eingefuehrt.
Leere, fehlende und defekte `PAGE_TITLE`-Tags ergeben keinen Titel und blockieren die anderen
GKG-Felder nicht. Bei Wiederholungen wird der erste nicht leere, korrekt geschlossene Titel
verwendet. `PAGE_PRECISEPUBTIMESTAMP` und `PAGE_AUTHORS` werden bewusst noch nicht persistiert:
Zeitstempelsemantik und ein normalisiertes Autorenmodell muessen separat geklaert werden.

Migration V7 markiert bestehende Staging-Zeilen mit `metadata_extracted = false`. Der Staging-Job
parst diese Zeilen kontrolliert aus dem unveraenderten Raw-TSV nach und setzt die Markierung. Der
Article-Extractor ordnet danach fehlende Titel auch bereits vorhandenen GKG-Signalen zu. Beide
Schritte sind wiederholbar und ueberschreiben kein bereits festgelegtes Ergebnis.

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

## Externes Article Enrichment

Das fruehere Crawler-Zielmodell mit der Tabelle `article_enrichments` wurde nach der Analyse realer
GKG-Rohdaten verworfen. Migration V5 dokumentiert die historische Einfuehrung; Migration V6
entfernt die Tabelle wieder. Es gibt derzeit weder Enrichment-Queue noch Crawl-Statusmodell.

ART-011 erschliesst stattdessen zuerst die bereits importierten GKG-Seitenmetadaten und ordnet sie
direkt dem Artikelmodell zu. Ein externer Crawler wird erst bei einem konkret nachgewiesenen Bedarf
fuer Volltext, Hauptbild, Sprache oder Qualitaetsvalidierung neu entworfen. Dabei wird kein altes
Schema vorsorglich beibehalten.

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
