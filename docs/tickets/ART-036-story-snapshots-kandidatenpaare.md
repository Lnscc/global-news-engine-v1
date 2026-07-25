# ART-036: Story-Snapshots und exakte Kandidatenpaare erzeugen

Status: offen
Bereich: stories, operations

## Kontext

ART-033 definiert den deterministischen Story-Verarbeitungsvertrag. ART-034 stellt mit
`story_snapshots`, `story_snapshot_members`, `story_processing_runs` und
`story_pair_decisions` das Persistenzmodell bereit. ART-035 erzeugt die versionierten
`story_article_inputs` und unveraenderlichen READY-Embedding-Artefakte.

Der naechste Verarbeitungsschritt muss diese veraenderliche Eingangsmenge reproduzierbar
einfrieren und daraus fuer jede Shadow-Clustering-Version die exakten zeitlich und semantisch
zulaessigen Artikelpaare bestimmen.

## Ziel

Ein inkrementeller und wiederanlauffaehiger Job erzeugt unveraenderliche Snapshots aus den
aktuellen READY-Artikel-Inputs. Innerhalb jedes in der Clustering-Version gespeicherten
Zeitfensters berechnet er die exakte Cosine Similarity aller zulaessigen Paare und persistiert
die Pair-Entscheidungen deterministisch und idempotent.

Das Ticket erzeugt noch keine Stories, Komponenten, Mitgliedschaften, Merge-, Split- oder
Publish-Entscheidungen.

## Snapshot-Vertrag

Ein Snapshot wird getrennt je Clustering-Version gebildet und enthaelt ausschliesslich aktuelle
Inputs mit:

```text
- current_marker = 1
- title_usability = USABLE
- embedding_status = READY
- vorhandenem Embedding-Artefakt und vectorHash
- effectiveAt <= snapshotWatermark
```

Die kanonische Snapshot-Eingabe ist nach `(effectiveAt, articleRef)` aufsteigend sortiert und
enthaelt mindestens:

```text
- clusteringVersion
- snapshotWatermark
- articleRef
- articleInputFingerprint
- embeddingArtifactId
- vectorHash
```

`snapshotInputHash` ist der kleingeschriebene SHA-256-Hexwert der kanonischen UTF-8-
Serialisierung. Identische Version, Watermark und Eingabemenge verwenden denselben Snapshot.
Nach der ersten Referenz durch einen Run bleiben Snapshot und Mitglieder unveraenderlich.

## Exakte Kandidatensuche

Je Snapshot gelten ausschliesslich die in der Clustering-Version gespeicherten Werte:

```text
candidateWindowHours = 24, 48 oder 72
candidateSimilarityThreshold = 0.700000
candidateSearchMode = exact-cosine-radius-v1
pairDecisionRuleVersion = cosine-070-time{window}-v1
```

Die Verarbeitung:

```text
1. kanonisch gespeicherte Float32-Vektoren aus vector_bytes lesen
2. nur Paare mit abs(left.effectiveAt - right.effectiveAt) <= candidateWindowHours vergleichen
3. Cosine Similarity in ausreichend hoher Praezision berechnen
4. den Score vor Persistenz deterministisch auf sechs Nachkommastellen quantisieren
5. Paare mit Cosine >= 0.700000 als SAME_STORY speichern
6. ein Paar kanonisch genau einmal als left.articleRef < right.articleRef speichern
7. nach Cosine absteigend und danach nach beiden articleRefs aufsteigend ordnen
```

Es gibt keine Top-k-Begrenzung. Fuer jeden Artikel ohne Treffer oberhalb des Schwellwerts wird,
sofern ein Vergleichsartikel im Zeitfenster existiert, der beste exakte Vergleich als
`UNCERTAIN` mit `top_one_below_threshold = true` gespeichert. Gleichstaende werden ueber die
kleinere Vergleichs-`articleRef` aufgeloest.

## Umfang

```text
- Story-Modul fuer Snapshot-Erzeugung und exakte Pair-Berechnung anlegen
- Snapshot-Watermark und kanonische Snapshot-Serialisierung eindeutig implementieren
- snapshotKey und snapshotInputHash deterministisch erzeugen
- aktuelle READY-Inputs atomar auswaehlen und als story_snapshot_members materialisieren
- identische Snapshots und Mitglieder vollstaendig wiederverwenden
- fuer jeden Lauf einen idempotenten story_processing_runs-Datensatz erzeugen
- INCREMENTAL-, BACKFILL- und REPROCESSING-Modus technisch unterscheidbar halten
- Float32-BYTEA strikt nach dem ART-035-Vertrag dekodieren
- Dimension, Byte-Laenge, endliche Werte, Norm und vectorHash vor der Suche pruefen
- exakte zeitfensterbegrenzte Cosine-Suche ohne approximativen Index implementieren
- Similarity deterministisch auf NUMERIC(7,6) quantisieren
- kanonische Paarreihenfolge und stabile Tie-Break-Regeln implementieren
- SAME_STORY-Entscheidungen ab dem versionierten Schwellwert persistieren
- beste Top-1-Diagnose unterhalb des Schwellwerts fuer Artikel ohne Treffer persistieren
- decisionHash aus Snapshot, Pair-Inputs, Regelversion und Ergebnis bilden
- Pair-Entscheidungen ueber Unique Constraints atomar anlegen oder wiederverwenden
- ein Retry verwendet denselben Snapshot und erzeugt keine doppelten Runs oder Entscheidungen
- konkurrierende Worker durch transaktionales Claiming und Unique Constraints idempotent halten
- ein Fehler fuer einen Artikel oder Vektor blockiert nicht unkontrolliert andere Versionen
- Metriken fuer Snapshots, Mitglieder, Kandidaten, Pair-Ergebnisse, Latenz und Fehler bereitstellen
- inkrementellen Lauf und begrenzten Backfill konfigurierbar und abschaltbar machen
- Unit-, Service-, Concurrency- und PostgreSQL-Integrationstests ergaenzen
- Betriebs- und Konfigurationsdokumentation aktualisieren
```

## Idempotenz und Wiederanlauf

```text
- identische Snapshot-Eingaben erzeugen genau einen Snapshot
- ein Snapshot-Mitglied wird je Snapshot und Artikel hoechstens einmal gespeichert
- ein Paar wird je Snapshot und Pair-Rule hoechstens einmal entschieden
- ein abgebrochener Lauf darf mit demselben Snapshot wiederaufgenommen werden
- ein erfolgreicher Retry erzeugt keine zweite fachliche Pair-Entscheidung
- neue oder historisierte Artikel-Inputs wirken erst in einem neuen Snapshot
- READY-Embeddings und alte Snapshots werden nicht geaendert oder geloescht
- Worker-Reihenfolge beeinflusst weder Paarmenge, Score noch Rangfolge
```

## Akzeptanzkriterien

```text
- nur aktuelle READY-Inputs werden in Snapshots aufgenommen
- Snapshot-Watermark, snapshotKey und snapshotInputHash sind deterministisch getestet
- identische Eingaben verwenden denselben unveraenderten Snapshot
- neue oder geaenderte Inputs erzeugen einen neuen Snapshot, ohne den alten zu veraendern
- jedes Snapshot-Mitglied referenziert exakt Fingerprint, Artefakt und vectorHash des Inputs
- 1.536 Float32-Werte werden in kanonischer Dimensionsreihenfolge dekodiert
- falsche Byte-Laenge, Dimension, Hash, NaN, Infinity und Nullnorm gelangen nicht in die Suche
- die Cosine-Berechnung ist gegen bekannte Vektorpaare und Grenzwerte getestet
- Paare ausserhalb des Zeitfensters werden nicht verglichen oder gespeichert
- alle Paare mit quantisierter Cosine >= 0.700000 werden ohne Top-k-Limit gespeichert
- leftArticleRef ist immer kleiner als rightArticleRef
- Artikel ohne positiven Treffer erhalten hoechstens eine deterministische Top-1-Diagnose
- wiederholte und konkurrierende Verarbeitung erzeugt keine doppelten Snapshots oder Paare
- PostgreSQL-Integrationstests pruefen Snapshot-Freeze, Constraints, Retry und Historisierung
- vorhandene Artikel-, GDELT-, Embedding- und Story-Daten bleiben unveraendert
- es werden keine Stories, Komponenten, Mitgliedschaften, Merge- oder Split-Ergebnisse erzeugt
- es gibt keine REST-API-Aenderung und keine Postman-Anpassung
```

## Abhaengigkeiten und Reihenfolge

Das Ticket baut auf ART-033, ART-034 und ART-035 auf. Es ist die Voraussetzung fuer den
anschliessenden deterministischen Medoid-Clusterlauf, weil dieser ausschliesslich eingefrorene
Snapshot-Mitglieder und gespeicherte Pair-Entscheidungen verwenden darf.

Der laufende ART-035-Backfill muss fuer einen gewaehlten Snapshot-Watermark nicht global beendet
sein. Der Snapshot darf aber nur den zu diesem Zeitpunkt vollstaendigen, aktuellen READY-Bestand
verwenden und muss seinen konkreten Umfang nachvollziehbar festhalten.

## Abgrenzung

Medoid-Berechnung, Komponentenbildung, Story-Identitaeten, Mitgliedschaften, Story-Zustaende,
Merge, Split, Lineage, Publishing, Promotion einer Clustering-Version, Story REST API,
approximative Vektorsuche und `pgvector`-Indizes sind nicht Teil dieses Tickets.
