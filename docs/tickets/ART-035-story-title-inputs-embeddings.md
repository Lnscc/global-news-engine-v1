# ART-035: Story-Titel-Inputs und Embeddings erzeugen

Status: offen
Bereich: stories, articles, operations

## Kontext

ART-033 definiert den Verarbeitungsvertrag fuer Story-Clustering. ART-034 stellt mit
`story_clustering_versions`, `story_article_inputs`, `story_embedding_artifacts` und
`story_embedding_attempts` das produktive Persistenzmodell bereit.

Der Anwendung fehlt noch der erste schreibende Verarbeitungsschritt: Aus den vorhandenen
Artikelmetadaten muessen deterministische, versionierte Titel-Inputs entstehen. Fuer verwendbare
Titel werden idempotent Embeddings erzeugt und als unveraenderliche Artefakte gespeichert.

## Ziel

Ein inkrementeller und reparierbarer Job erzeugt fuer jede konfigurierte Shadow-Version den
aktuellen Story-Artikel-Input. Er normalisiert Titel und Zeitwerte exakt nach ART-033, verwendet
vorhandene Embedding-Artefakte wieder und ruft das konfigurierte Embedding-Modell nur fuer neue
fachliche Artefaktschluessel auf.

Das Ticket erzeugt noch keine Snapshots, Kandidaten, Stories oder Mitgliedschaften.

## Titel- und Zeitregeln

```text
articleRef = articles.url_hash
effectiveAt = publishedAt, falls vorhanden, sonst firstSeenAt
effectiveAtSource = PUBLISHED_AT oder FIRST_SEEN_AT
```

Die Normalisierung `art031-title-nfkc-ws-v1`:

```text
1. HTML5-Entities dekodieren
2. Unicode NFKC anwenden
3. jede Folge von Unicode-Whitespace durch ein Leerzeichen ersetzen
4. trimmen
5. Gross-/Kleinschreibung und Interpunktion erhalten
```

`titleInputHash` ist der kleingeschriebene SHA-256-Hexwert der UTF-8-Bytes des normalisierten
Titels. Ein Titel ist nicht verwendbar, wenn er leer ist oder sein Unicode-Casefold-Wert in
`art031-generic-title-v1` liegt:

```text
Deadline
Health
CKIA
NPR News
Targeted News Service
```

Nicht verwendbare Titel werden als `NOT_REQUIRED` mit `TITLE_MISSING` beziehungsweise
`TITLE_GENERIC` gespeichert und loesen keinen Modellaufruf aus. Kurze Titel werden nicht allein
anhand einer Wortzahl abgelehnt.

## Embedding-Vertrag

Die Startversion verwendet den in ART-033 eingefrorenen Vertrag:

```text
embeddingModelId = text-embedding-3-small
embeddingModelVersion = openai:text-embedding-3-small@2026-07-20
embeddingDimension = 1536
```

Der fachliche Artefaktschluessel lautet:

```text
(embeddingModelId,
 embeddingModelVersion,
 embeddingDimension,
 titleNormalizationVersion,
 titleInputHash)
```

Erfolgreiche Vektoren werden defensiv L2-normalisiert und als 1.536 Float32-Werte in
Dimensionsreihenfolge kanonisch serialisiert. `vectorHash` ist der SHA-256-Hash dieser Bytefolge.
Die Vorher-Norm, gemessene Dimension, Anbieter-Request-ID und Versuchsdaten bleiben
nachvollziehbar.

## Umfang

```text
- Story-Modul fuer Titel-Normalisierung, Input-Fingerprints und Embedding-Erzeugung anlegen
- deterministische Auswahl des aktuell fuer ArticleSummary/ArticleDetail projizierten Titels und
  der Publikationszeit wiederverwenden; keine zweite Metadaten-Wahrheit einfuehren
- Titel-Normalisierung, HTML-Entity-Decoding, Unicode NFKC, Whitespace und Hashing implementieren
- versionierte Generiktitelliste und Unicode-Casefold-Pruefung implementieren
- effectiveAt und Zeitquelle deterministisch ableiten
- aktuelle story_article_inputs je Artikel und Clustering-Version ermitteln
- unveraenderten fachlichen Input als No-op behandeln
- bei Titel-, Zeit-, Normalisierungs- oder Modellwechsel bisherigen Input historisieren und einen
  neuen aktuellen Input erzeugen
- NOT_REQUIRED-Inputs ohne externen Aufruf speichern
- story_embedding_artifacts ueber den fachlichen Schluessel atomar anlegen oder wiederverwenden
- konfigurierbaren Embedding-Client fuer das festgelegte Modell implementieren
- API-Schluessel ausschliesslich ueber Konfiguration/Umgebung beziehen und niemals protokollieren
- Batch-Aufrufe eindeutig auf einzelne Titel-Inputs zurueckfuehren
- Dimension, endliche Werte und Nullnorm vor READY pruefen
- Vektor defensiv L2-normalisieren, als Float32-BYTEA serialisieren und SHA-256 hashen
- PENDING, READY, RETRYABLE_FAILURE und TERMINAL_FAILURE konsistent persistieren
- jeden Versuch in story_embedding_attempts nachvollziehbar speichern
- Timeout und temporaere Anbieterfehler mit Backoff 1 min, 5 min, 15 min, 1 h und 6 h behandeln
- Retry-After bei Rate Limits bevorzugen
- nach fuenf retryfaehigen Fehlern den Fall fuer Reparatur und Alarm sichtbar lassen
- Dimensionsfehler, nicht endliche Werte und Nullnorm als TERMINAL_FAILURE behandeln
- abweichenden Vektor-Hash fuer einen vorhandenen Artefaktschluessel als MODEL_DRIFT protokollieren
  und das vorhandene READY-Artefakt nicht ueberschreiben
- inkrementellen Lauf sowie taeglichen Reparaturlauf konfigurierbar und abschaltbar machen
- konkurrierende Worker durch Unique Constraints und transaktionales Claiming idempotent halten
- Metriken fuer Zustaende, Versuche, Latenz, Fehler, Rueckstand und Modellaufrufe bereitstellen
- Unit-, Service-, Client-, Concurrency- und PostgreSQL-Integrationstests ergaenzen
- Betriebs- und Konfigurationsdokumentation aktualisieren
```

## Artikel-Input-Fingerprint

Der kanonisch serialisierte Fingerprint enthaelt mindestens:

```text
- articleRef
- effectiveAt und effectiveAtSource
- titleInputHash oder expliziten TITLE_MISSING-/TITLE_GENERIC-Grund
- Embedding-Artefaktschluessel und vectorHash bei READY
- Titel-Normalisierungs-, Generiktitel- und Modellversion
```

Personen, Organisationen, Orte, Themes, Event-IDs, Event-Codes und Domain sind nicht Teil des
entscheidungsrelevanten Fingerprints.

## Fehler- und Wiederanlaufregeln

```text
- ein Retry erzeugt weder einen zweiten aktuellen Input noch ein zweites Artefakt
- ein bereits vorhandenes READY-Artefakt verhindert einen erneuten Modellaufruf
- mehrere Artikel mit demselben normalisierten Titel duerfen dasselbe Artefakt referenzieren
- ein fehlender API-Schluessel deaktiviert Modellaufrufe mit klarer Health-Meldung
- ein Fehler fuer einen Titel blockiert nicht die Verarbeitung anderer Titel
- bereits erfolgreiche Batch-Eintraege werden bei Teilfehlern nicht erneut angefordert
- ein spaeter hinzukommender oder korrigierter Titel erzeugt eine explizite Neubewertung
- alte READY-Artefakte und historische Inputs werden nicht veraendert oder geloescht
```

## Akzeptanzkriterien

```text
- Normalisierung und Hash sind fuer HTML-Entities, Unicode, Whitespace und Interpunktion
  deterministisch getestet
- die fuenf belegten Generiktitel erzeugen NOT_REQUIRED/TITLE_GENERIC und keinen Modellaufruf
- fehlende oder leere Titel erzeugen NOT_REQUIRED/TITLE_MISSING
- publishedAt wird bevorzugt, firstSeenAt bleibt der eindeutige Fallback
- zwei Artikel mit demselben normalisierten Titel verwenden genau ein Embedding-Artefakt
- wiederholte Verarbeitung unveraenderter Daten ist vollstaendig idempotent
- Titel- oder Publikationszeitaenderung beendet den bisherigen aktuellen Input und erzeugt einen
  neuen Fingerprint
- READY enthaelt exakt 1.536 kanonische Float32-Werte, Hash, positive Norm und Versuchsdaten
- falsche Dimension, NaN, Infinity und Nullnorm werden nie READY
- Timeout, Rate Limit, Retry-After und Backoff sind mit kontrollierter Zeit getestet
- MODEL_DRIFT ueberschreibt kein vorhandenes READY-Artefakt
- konkurrierende Verarbeitung erzeugt keine doppelten Artefakte oder aktuellen Inputs
- PostgreSQL-Integrationstests pruefen Constraints, Historisierung und Wiederanlauf
- vorhandene Artikel-, GDELT- und Story-Daten bleiben unveraendert
- es werden keine Snapshots, Kandidaten, Stories oder Mitgliedschaften erzeugt
- es gibt keine REST-API-Aenderung und keine Postman-Anpassung
```

## Abhaengigkeiten und Reihenfolge

Das Ticket baut auf ART-031, ART-033 und ART-034 auf. Es muss vor dem Snapshot- und
Kandidatenlauf umgesetzt werden, weil dieser ausschliesslich eingefrorene READY-Inputs und
gespeicherte Embeddings verwenden darf.

## Abgrenzung

Snapshot-Erzeugung, exakte Cosine-Kandidatensuche, Pair-Entscheidung, Medoid-Clustering,
Story-Publishing, Merge, Split, Backfill des Cluster-Ergebnisses und Story REST API sind nicht
Teil dieses Tickets. Das Ticket aktiviert keine Clustering-Version und macht keine Shadow-Daten
produktsichtbar.
