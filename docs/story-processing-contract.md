# Story-Lebenszyklus und Verarbeitungsvertrag

## Zweck und Geltungsbereich

Dieses Dokument konkretisiert die fachliche Story-Definition aus `stories.md` zu einem
technologieunabhaengigen Verarbeitungsvertrag. Es legt fest, welche Eingaben eine
Clustering-Version sieht, wie daraus reproduzierbare Kandidaten und Mitgliedschaften entstehen
und wie inkrementelle Laeufe, Fehler, Backfills und Regelwechsel behandelt werden.

Der Vertrag beschreibt das erste produktionsfaehige MVP. Er schreibt keine konkrete Datenbank,
Queue, Scheduler- oder Vektorindeximplementierung vor. Ein spaeteres Persistenzschema und ein
Cluster-Job muessen die hier beschriebenen Invarianten nachweisbar umsetzen.

## Verbindliche Entscheidungen

| Thema | Entscheidung | Begruendung |
|---|---|---|
| Artikelzeit | `publishedAt`, falls vorhanden, sonst `firstSeenAt` | Archivartikel werden nicht anhand ihrer spaeten Ingestion in aktuelle Stories gemischt; der Fallback bleibt vollstaendig |
| Kandidatenraum | alle Embeddings im symmetrischen `+/- 24 h`-Fenster; kein Top-k | kein fachlich moeglicher Treffer wird durch eine feste Ranggrenze abgeschnitten |
| Automatische Startregel | Cosine Similarity `>= 0,70` innerhalb des Kandidatenfensters | verwendet wie gewuenscht ausschliesslich Titel-Embedding und Zeit und bleibt auf dem vorhandenen Korpus konservativ |
| Clusterbildung | deterministisches, radiusbeschraenktes Medoid-Clustering | verhindert Single-Linkage-Ketten und verwendet einen realen Artikel als erklaerbaren Story-Mittelpunkt |
| Unsicherheit | keine erzwungene Verbindung; Singleton oder `UNASSIGNED` | falsche Zusammenfuehrungen sind schwerer reversibel und erklaerbar als zusaetzliche Singletons |
| Mitgliedschaft | hoechstens eine aktuelle Story je Artikel und Clustering-Version | entspricht der fachlichen Definition und macht Partition und Historie eindeutig |
| Suche | exakte Cosine-Suche fuer veroeffentlichende Laeufe | identischer Embedding-Bestand liefert identische Kandidaten; approximative Suche bleibt zunaechst Shadow-Modus |
| Generische Titel | versionierter Eingangsfilter, keine Story-Zuordnung | ein inhaltsarmer Titel mit hoher Similarity ist kein belastbarer Ereignisanker |
| Parallelitaet | Embeddings und reine Berechnung duerfen parallel laufen; genau ein Publisher je Clustering-Version | verhindert konkurrierende, reihenfolgeabhaengige Mitgliedschaften |
| Historie | append-only Entscheidungen und Gueltigkeitsintervalle; keine stille Ueberschreibung | Retry, Merge, Split und Neubewertung bleiben erklaerbar |
| Regel- oder Modellwechsel | neue Clustering-Version und vollstaendiges Shadow-Reprocessing | ein Modellwechsel ist keine Wiederholung desselben Laufs |
| Erste Betriebsphase | ausschliesslich Shadow-Modus bis zur Freigabe auf einem neuen Holdout | verhindert, dass ein bereits zur Schwellenwahl verwendeter Datensatz als scheinbar unabhaengiger Nachweis dient |

Die Startregel ersetzt die in ART-032 eingefrorene Top-50-Baseline. Der dortige Schwellwert
`0,224867` war auf F1 und Recall optimiert und erzeugte Fehl-Merges. Fuer den reinen Embedding-Zeit-
Vertrag wird deshalb konservativ `0,70` festgelegt. Innerhalb 24 Stunden liefert diese Regel auf
dem bisherigen Kalibrierungssplit `12 TP`, `0 FP`, `2 FN`, `29 TN` und auf dem bisherigen
Evaluationssplit `18 TP`, `0 FP`, `1 FN`, `41 TN`. Das entspricht auf dem Evaluationssplit
Precision `1,0000`, Recall `0,9474` und F1 `0,9730`.

Da `0,70` unter Kenntnis des bisherigen Evaluationssplits festgelegt wurde, ist dieser Split fuer
eine kuenftige Produktionsfreigabe verbraucht und darf nicht erneut als unabhaengiger
Qualitaetsnachweis gelten. Vor der technischen Freigabe wird ein neuer, leakagefreier Holdout
benoetigt. Ohne Handlungs-, Entitaets- oder andere Trennsignale bleibt ausserdem eine bewusste
Grenze: Zwei verschiedene Geschehen oberhalb `0,70` werden zusammengefuehrt.

## Invarianten

Jede konforme Implementierung muss folgende Eigenschaften einhalten:

1. Eine eingefrorene Artikelmenge, dieselben gespeicherten Embeddings und dieselbe
   Clustering-Version ergeben unabhaengig von Workerzahl und Verarbeitungsreihenfolge dieselbe
   Partition.
2. Ein Artikel besitzt je Clustering-Version hoechstens eine aktuelle Mitgliedschaft. Historische
   Mitgliedschaften duerfen erhalten bleiben, sind aber nicht gleichzeitig aktuell.
3. Derselbe Artikel-Input-Fingerprint wird in derselben Version hoechstens einmal wirksam
   entschieden. Retries duerfen keine zweite Story, Mitgliedschaft oder Embedding-Zuordnung
   erzeugen.
4. Embedding-Artefakte sind unveraenderlich. Ein anderer Titel, eine andere Normalisierung, ein
   anderes Modell oder eine andere Vektordimension erzeugt einen neuen Schluessel.
5. Eine sichtbare Story-ID wird nie geloescht oder neu vergeben. Merge und Split erzeugen
   nachvollziehbare Nachfolgerbeziehungen.
6. Eine automatische Mitgliedschaft ist ohne erneuten Embedding-Aufruf aus den gespeicherten
   Eingaben, Kandidaten, Scores, Regeln und Entscheidungen erklaerbar.
7. Inkrementelle Verarbeitung ist eine Optimierung. Ihr veroeffentlichtes Ergebnis muss dem
   kanonischen Ergebnis eines vollstaendigen Laufs auf demselben Snapshot entsprechen.
8. Ein Regel-, Modell- oder Inputversionswechsel wird nicht als normaler Retry behandelt und
   veraendert keine Ergebnisse der alten Clustering-Version.

## Versionen und stabile Referenzen

### Clustering-Version

Eine Clustering-Version ist ein unveraenderlicher Vertrag mit mindestens folgenden Bestandteilen:

```text
clusteringVersion
titleNormalizationVersion
genericTitleRuleVersion
embeddingModelId
embeddingModelVersion
embeddingDimension
candidateTimeRuleVersion
candidateWindowHours
candidateSimilarityThreshold
candidateSearchMode
pairDecisionRuleVersion
componentRuleVersion
featureNormalizationVersions
```

Fuer die erste Shadow-Version gelten:

```text
titleNormalizationVersion = art031-title-nfkc-ws-v1
genericTitleRuleVersion = art031-generic-title-v1
embeddingModelId = text-embedding-3-small
embeddingModelVersion = openai:text-embedding-3-small@2026-07-20
embeddingDimension = 1536
candidateTimeRuleVersion = published-at-else-first-seen-v1
candidateWindowHours = 24
candidateSimilarityThreshold = 0.70
candidateSearchMode = exact-cosine-radius-v1
pairDecisionRuleVersion = cosine-070-time24-v1
componentRuleVersion = medoid-radius-agglomerative-v1
versionStatus = SHADOW
```

Parallel werden zwei Challenger-Versionen mit identischem Modell, Schwellwert und
Komponentenverfahren berechnet:

```text
Challenger 1: candidateWindowHours = 48
Challenger 2: candidateWindowHours = 72
```

Die 24-h-Version bleibt die Freigabeempfehlung, solange keine Challenger-Version auf einem neuen
Holdout zusaetzlichen Recall zeigt, ohne Precision `0,98` zu unterschreiten. Das Zeitfenster wird
nicht innerhalb derselben Clustering-Version dynamisch gewechselt.

Eine Clustering-Version besitzt unabhaengig vom Zustand einzelner Stories einen Betriebsstatus:

```text
SHADOW -> ACTIVE
ACTIVE -> RETIRED
RETIRED -> ACTIVE nur durch expliziten Rollback
```

`SHADOW` berechnet den vollstaendigen Lebenszyklus und alle Metriken in einem getrennten Namespace,
ist aber fuer Produktfunktionen und externe IDs nicht verbindlich. Die erste Version startet
ausschliesslich in diesem Zustand. `ACTIVE` erfordert einen neuen unabhaengigen Holdout, die
definierten Qualitaets-Gates, einen dokumentierten Diff zu den Challenger-Versionen und eine
bewusste Freigabe. `RETIRED` bleibt lesbar und auditierbar, erhaelt aber keine normalen
inkrementellen Eingaben mehr. Ein Rollback darf den unveraenderten vorherigen Versionsdatensatz
explizit erneut aktivieren und wird als eigener Aktivierungsvorgang historisiert.

Die konkrete `clusteringVersion` wird bei der Implementierung als unveraenderliche, semantisch
versionierte Kennung angelegt. Eine Aenderung, die Kandidaten, Paarentscheidungen oder Partitionen
beeinflussen kann, erfordert mindestens eine neue Minor-Version und ein vollstaendiges
Reprocessing. Patch-Versionen duerfen nur Erklaerung, Metriken oder nicht entscheidungsrelevante
Metadaten korrigieren.

Die Modellkennung `openai:text-embedding-3-small@2026-07-20` ist eine lokal eingefrorene
Vertragskennung und kein vom Anbieter garantierter, unveraenderlicher Snapshot. Reproduzierbare
Laeufe verwenden deshalb die einmal erfolgreich gespeicherten Vektoren. Eine spaetere Erzeugung,
die fuer denselben Schluessel einen anderen Vektor-Hash liefert, wird als Modelldrift gemeldet und
nicht still ueberschrieben.

### Artikelreferenz und Story-ID

Die stabile Artikelreferenz basiert auf dem kanonischen URL-Hash und nicht allein auf einer
lokalen Datenbank-ID. Eine Story erhaelt bei ihrer ersten Veroeffentlichung eine unveraenderliche
oeffentliche ID und einen Identitaetsanker. Der Identitaetsanker ist der nach
`(effectiveAt, articleRef)` kleinste damalige Mitgliedsartikel.

Die Story-ID wird durch spaetere Erweiterung oder Schliessung nicht veraendert. Der Anker darf als
historische Referenz erhalten bleiben, auch wenn der Artikel nach einer Neubewertung nicht mehr
aktuell zur Story gehoert. Eine frische Umgebung darf intern deterministische IDs aus
Clustering-Version und Anker ableiten; nach einer bereits veroeffentlichten Version hat die
persistierte Identitaets- und Nachfolgerhistorie Vorrang.

## Artikel-Input

### Effektiver Artikelzeitpunkt

```text
effectiveAt = publishedAt, falls publishedAt vorhanden ist
              sonst firstSeenAt
```

`publishedAt` wird nicht allein wegen eines grossen Abstands zu `firstSeenAt` verworfen. ART-031
hat echte Archivzeitpunkte nachgewiesen. Dadurch bleibt ein 1977 publizierter Archivartikel im
historischen Kandidatenraum, auch wenn er 2026 erstmals importiert wird. `firstSeenAt` bleibt
zusaetzlich als Betriebszeit und fuer Revisit, Schliessung und Audit erhalten.

Kommt `publishedAt` spaeter hinzu oder aendert sich der persistierte Wert, entsteht ein neuer
Artikel-Input-Fingerprint und eine explizite Neubewertung. Eine vorherige Mitgliedschaft darf sich
dabei aendern; die alte Entscheidung wird beendet, nicht ueberschrieben.

### Verwendbarer Titel

Ein Titel ist in der Startversion verwendbar, wenn er nach `art031-title-nfkc-ws-v1` nicht leer ist
und sein daraus abgeleiteter Unicode-Casefold-Wert nicht auf der versionierten Generikliste steht.
Generik ist eine Eingangsqualitaetsregel; zwischen zugelassenen Artikeln entscheiden weiterhin nur
Embedding und Zeit. Wortzahl, Sprache, Domain oder Titelgleichheit sind keine Vergleichsregeln.
Die Embedding-Eingabe bleibt case-sensitiv. Die Normalisierung:

1. dekodiert HTML5-Entities,
2. wendet Unicode NFKC an,
3. ersetzt jede Folge von Unicode-Whitespace durch genau ein Leerzeichen,
4. trimmt den Wert und
5. behaelt Gross-/Kleinschreibung und Interpunktion bei.

Der `titleInputHash` ist der kleingeschriebene SHA-256-Hexwert der UTF-8-Bytes dieses Inputs.
Kurze Titel werden nicht allein anhand einer Wortzahl verworfen, weil dies insbesondere fuer
CJK-Titel fachlich falsch waere. `art031-generic-title-v1` enthaelt die durch ART-031 belegten
inhaltsarmen Werte `Deadline`, `Health`, `CKIA`, `NPR News` und `Targeted News Service` in ihrer
casefold-normalisierten Form. Eine Listenanderung ist entscheidungsrelevant und erzeugt eine neue
Clustering-Version.

Artikel ohne verwendbaren Titel sind `UNASSIGNED` mit Grund `TITLE_MISSING` oder
`TITLE_GENERIC`. Sie werden bei neuen Titeldaten sowie nach 24 und 72 Stunden erneut betrachtet;
sie erhalten kein erfundenes Ersatzmerkmal und kein Embedding fuer diese Clustering-Version.

### Artikel-Input-Fingerprint

Der Fingerprint wird deterministisch ueber kanonisch serialisierte Werte gebildet:

```text
articleRef
effectiveAt und verwendete Zeitquelle
titleInputHash oder expliziter Missing-/Generic-Grund
Embedding-Artefaktschluessel und Vektor-Hash
zugehoerige Titel- und Modellversionen
```

Personen, Organisationen, Orte, Themes, Event-IDs, Event-Codes, Domain und lexikalische
Titelmerkmale sind nicht Teil des Entscheidungs-Fingerprints. Sie duerfen fuer Diagnose und Audit
als getrennte Evidenz-Snapshots gespeichert werden, loesen in dieser Version aber keine
Neuzuordnung aus und beeinflussen weder Kandidaten noch Pair-Entscheidung.

## Eintritt und Neubewertung

Ein Artikel wird fuer eine Clustering-Version vorgemerkt bei:

- erstmaliger Anlage,
- erstmalig verwendbarem oder korrigiertem Titel,
- hinzugekommenem oder geaendertem `publishedAt`,
- einem Signal, das Titel oder `publishedAt` des Artikels tatsaechlich aendert,
- faelligem Revisit nach 24 oder 72 Stunden,
- erfolgreicher Behebung eines Embedding-Fehlers,
- einem expliziten Backfill oder Reprocessing.

Ein Event startet keine zweite Verarbeitung, wenn der resultierende Artikel-Input-Fingerprint
bereits erfolgreich entschieden wurde. Spaetere Revisit-Zeitpunkte orientieren sich an
`firstSeenAt`, nicht am gegebenenfalls historischen `effectiveAt`. Der 24-Stunden-Revisit deckt
den von ART-031 gemessenen normalen Nachlauf ab; der 72-Stunden-Revisit liegt oberhalb des dort
gemessenen Maximums von rund 60,9 Stunden. Ein taeglicher Reparaturlauf sucht zusaetzlich gezielt
nach ausstehenden, retryfaehigen oder vom aktuellen Fingerprint abweichenden Artikeln. Er clustert
nicht blind den gesamten Bestand neu.

## Embedding-Vertrag

### Idempotenzschluessel und Zustaende

Der fachliche Schluessel eines Embedding-Artefakts lautet:

```text
(embeddingModelId,
 embeddingModelVersion,
 embeddingDimension,
 titleNormalizationVersion,
 titleInputHash)
```

Mehrere Artikel mit demselben normalisierten Titel duerfen dasselbe unveraenderliche Artefakt
referenzieren. Pro Schluessel existiert hoechstens ein erfolgreich veroeffentlichtes Artefakt.
Neben dem Vektor werden Erzeugungszeit, Anbieter-Request-ID soweit vorhanden, Versuch, Norm,
Vektor-Hash und gemessene Dimension gespeichert.

Der kanonische Vektor besteht aus 1.536 IEEE-754-Float32-Werten in Dimensionsreihenfolge nach der
defensiven L2-Normalisierung. Sein Hash wird ueber diese kanonische Bytefolge gebildet. Cosine
Similarity wird mit Float64-Akkumulation in Dimensionsreihenfolge berechnet und anschliessend mit
Round-half-even auf sechs Dezimalstellen quantisiert. Kandidaten- und Pair-Regeln vergleichen den
quantisierten Wert; die Startgrenze lautet exakt `0,700000`. Dadurch haengt ein Grenzfall nicht von
SQL-, JVM- oder CPU-spezifischer Rundung ab.

Ein Artikel beziehungsweise Artefakt durchlaeuft konzeptionell folgende Zustaende:

```text
NOT_REQUIRED -> PENDING -> READY
                    |-> RETRYABLE_FAILURE -> PENDING
                    |-> TERMINAL_FAILURE
READY -- Titel-/Modell-/Inputwechsel --> neuer Schluessel in PENDING
```

Das alte `READY`-Artefakt wird bei einem Wechsel nicht veraendert oder geloescht. Nur die
Zuordnung des aktuellen Artikel-Inputs zeigt auf den neuen Schluessel.

### Fehlerregeln

- Timeout und temporaerer Anbieterfehler: `RETRYABLE_FAILURE`; maximal fuenf Versuche mit
  exponentiellem Backoff `1 min, 5 min, 15 min, 1 h, 6 h`. Zufalls-Jitter beeinflusst nur die
  Ausfuehrungszeit, nie das Ergebnis. Danach bleibt der Fall offen fuer den Reparaturlauf und
  wird alarmiert.
- Rate Limit: `Retry-After` hat Vorrang, sonst dieselbe Backoff-Folge. Der Batch wird in
  identifizierbare Einzelinputs zerlegt; bereits erfolgreiche Artefakte werden nicht erneut
  angefordert.
- Ungueltige Dimension, nicht endliche Werte oder Nullnorm: `TERMINAL_FAILURE` fuer die betroffene
  Modellversion, Circuit Breaker fuer weitere Veroeffentlichungen und Alarm. Der Vektor darf nicht
  abgeschnitten, aufgefuellt oder still normalisiert werden.
- Nicht verwendbarer Titel: `NOT_REQUIRED`; kein API-Aufruf und `UNASSIGNED` bis zu einer
  relevanten Eingabeaenderung.
- Abweichender Vektor-Hash bei demselben Artefaktschluessel: vorhandenes Artefakt bleibt
  kanonisch, Abweichung wird als Modelldrift quarantiniert. Eine bewusste Uebernahme benoetigt
  eine neue Modell- und Clustering-Version.

Erfolgreiche Vektoren werden defensiv L2-normalisiert, sofern die gespeicherte Norm innerhalb der
versionierten Toleranz liegt. Die Vorher-Norm bleibt im Audit erhalten. Batch-Groesse ist eine
operative Einstellung und kein fachlicher Versionsbestandteil; verschiedene Batch-Groessen
muessen dieselben Artefaktschluessel und gespeicherten Vektoren verwenden.

## Snapshot und Kandidatensuche

### Eingefrorener Snapshot

Jeder veroeffentlichende Lauf liest einen benannten, unveraenderlichen Snapshot aus:

```text
snapshotId
snapshotWatermark
clusteringVersion
sortierte Menge aus articleRef und articleInputFingerprint
sortierte Menge der referenzierten Embedding-Artefaktschluessel und Vektor-Hashes
```

Der `snapshotInputHash` ist der Hash der kanonischen Serialisierung dieser Mengen. Neue Signale,
die nach dem Watermark eintreffen, gehoeren in einen neuen Lauf. Dadurch sieht ein Lauf keine
halb aktualisierten Artikel.

### Kandidatenmenge

Fuer jeden Artikel mit `READY`-Embedding:

1. beruecksichtige nur Artikel derselben Clustering-Version mit
   `abs(left.effectiveAt - right.effectiveAt) <= 24 h`,
2. schliesse den Artikel selbst sowie Artikel ohne `READY`-Embedding aus,
3. berechne exakte Cosine Similarity auf den gespeicherten, normalisierten Vektoren,
4. behalte alle Paare mit Cosine `>= 0,70`, ohne Top-k-Begrenzung, und
5. sortiere nach Cosine absteigend, danach nach kleinerer und groesserer `articleRef` aufsteigend.

Die Similarity-Range ist die Kandidaten- und zugleich die positive Pair-Grenze. Eine konkrete
Implementierung darf die Range als zeitlich partitionierten Scan, Matrixmultiplikation oder
Indexabfrage ausfuehren, muss aber logisch alle Paare oberhalb der Grenze liefern. Ein Paar wird
kanonisch nur einmal als `(kleinere articleRef, groessere articleRef)` gespeichert.

Fuer Artikel ohne Treffer wird fuer die Erklaerbarkeit zusaetzlich der beste exakte Score im
Zeitfenster gespeichert, sofern ein Vergleichsartikel existiert. Dieser Top-1-Diagnosewert
begrenzt die Kandidatenmenge nicht.

NaN, fehlende Dimensionen oder fehlende Artefakte duerfen nicht als schlechtester Score
einsortiert werden; sie sind Embedding-Fehler und werden vor der Suche ausgesondert.

### Pair-Rule der Startversion

`cosine-070-time24-v1` liefert:

```text
SAME_STORY  wenn beide Embeddings READY sind,
            abs(left.effectiveAt - right.effectiveAt) <= 24 h gilt und
            die exakte Cosine Similarity >= 0,70 ist

UNCERTAIN   in allen anderen Faellen
```

`UNCERTAIN` bedeutet produktiv: keine Kante und damit keine Verbindung. Titelgleichheit,
Personen, Organisationen, Orte, Themes, Event-IDs, Event-Codes und Domain veraendern dieses
Ergebnis nicht. Sie duerfen nur als nicht entscheidungsrelevante Diagnoseevidenz protokolliert
werden. Die Startversion erzeugt kein automatisches `DIFFERENT_STORY`.

Ein anderer Schwellwert oder ein anderes Zeitfenster darf erst in einer neuen Clustering-Version
veroeffentlichen, wenn es auf einem neuen leakagefreien Evaluationssplit mindestens folgende Gates
erfuellt:

```text
pairwise precision >= 0.98
pairwise recall >= 0.90
24-h-Zeitfenster-Recall separat ausgewiesen
Fehl-Merges und False-Negatives nach Grenzfallkategorie separat ausgewiesen
Metriken fuer sprachuebergreifende, generische, kurze und kodierungsauffaellige Titel separat
vollstaendige Modell-, Normalisierungs-, Zeit- und Schwellwertversionierung
```

Bis dahin duerfen andere Schwellwerte oder Zeitfenster nur im Shadow-Modus Ergebnisse erzeugen.
Der bisherige ART-032-Evaluationssplit ist wegen der nachtraeglichen Wahl von `0,70` kein
unabhaengiger Freigabenachweis mehr.

Eine lokale Simulation des vollstaendigen Medoid-Verfahrens auf dem vorhandenen Korpus liefert
dieselben Paarmetriken wie die reine Schwellenmessung: Kalibrierung `12 TP`, `0 FP`, `2 FN`,
`29 TN`; Evaluation `18 TP`, `0 FP`, `1 FN`, `41 TN`. Das bestaetigt die interne Konsistenz des
Vertrags, ersetzt aber aus demselben Leakage-Grund keinen neuen Holdout.

## Deterministische Partition

Der kanonische Lauf startet mit einem Singleton-Cluster je verwendbarem Artikel. Der Medoid eines
Clusters ist immer ein tatsaechlicher Mitgliedsartikel. Er ist das Mitglied mit der hoechsten
mittleren quantisierten Cosine Similarity zu allen anderen Mitgliedern. Bei Gleichstand gewinnt
das kleinste Tupel `(effectiveAt, articleRef)`.

Danach wird deterministisch agglomeriert:

1. Berechne fuer jedes Clusterpaar Zeitabstand und Cosine Similarity seiner Medoide.
2. Beruecksichtige nur Paare mit Medoid-Zeitabstand `<= 24 h` und Medoid-Similarity `>= 0,70`.
3. Waehle das Paar mit der hoechsten Similarity; bei Gleichstand entscheiden die beiden
   kanonisch sortierten Medoid-`articleRef`-Werte.
4. Berechne den Medoid der vorgeschlagenen Vereinigungsmenge.
5. Akzeptiere den Merge nur, wenn jedes Mitglied zum neuen Medoid Cosine `>= 0,70` und einen
   effektiven Zeitabstand `<= 24 h` besitzt.
6. Nach einem akzeptierten Merge werden Medoide und alle betroffenen Clusterpaare neu berechnet.
   Ein fuer bestimmte Komponenten abgelehnter Merge darf nach einer Komponentenaenderung erneut
   geprueft werden.
7. Beende den Lauf, wenn kein Clusterpaar mehr akzeptiert werden kann.

Damit kann ein Artikel keine beliebig lange Similarity-Kette verbinden. Wenn `A-B >= 0,70` und
`B-C >= 0,70`, der Medoid der Vereinigungsmenge aber nicht zu jedem Mitglied mindestens `0,70`
erreicht, bleiben mindestens zwei Stories bestehen. Alle Berechnungen verwenden den eingefrorenen
Snapshot, die kanonische Score-Quantisierung und stabile Tie-Breaks; Worker-Reihenfolge spielt
keine Rolle.

Jede Komponente mit mindestens einem Artikel ist eine Story, also auch ein Singleton. Ein Artikel
ohne verwendbaren Titel oder ohne `READY`-Embedding bleibt dagegen `UNASSIGNED`; er wird nicht
vorzeitig als dauerhafter Singleton veroeffentlicht.

Der Medoid ist zugleich der repraesentative Artikel der aktuellen Story. Aendert er sich, bleibt
die Story-ID stabil und der Vertreterwechsel wird mit altem und neuem Medoid auditiert. Der
Identitaetsanker bleibt davon getrennt und unveraendert.

Ein inkrementeller Lauf darf nur die Aenderungsmenge und ihre transitive Kandidaten- und
Story-Nachbarschaft berechnen. Vor Veroeffentlichung muss er zeigen, dass an der Grenze dieser
Impact-Closure keine offene Kandidatenkante liegt; andernfalls wird die Closure erweitert. Ein
vollstaendiges Reprocessing auf demselben Snapshot ist die Referenz fuer Tests und Reparatur.

## Story-Zustaende und Uebergaenge

Eine veroeffentlichte Story besitzt genau einen Zustand:

```text
ACTIVE
CLOSED
SUPERSEDED
```

Erlaubte Uebergaenge:

```text
neu -> ACTIVE
ACTIVE -> CLOSED
CLOSED -> ACTIVE
ACTIVE -> SUPERSEDED
CLOSED -> SUPERSEDED
SUPERSEDED -> kein weiterer fachlicher Zustand
```

- `ACTIVE`: Die Story kann regulaer erweitert oder neu bewertet werden.
- `CLOSED`: Der Snapshot-Watermark liegt mindestens 72 Stunden hinter dem letzten relevanten
  Ingest- oder Bewertungsereignis, seitdem gab es keine Aenderung, und es existiert kein
  ausstehender retryfaehiger Input eines aktuellen Mitglieds. Die Frist verwendet Betriebszeit,
  nicht den eventuell historischen `effectiveAt`.
- `SUPERSEDED`: Die Story wurde durch Merge oder Split abgeloest. Sie bleibt aufloesbar und
  verweist auf mindestens einen Nachfolger.

Schliessen ist eine Sichtbarkeits- und Betriebsentscheidung, kein Schreibschutz. Ein neuer
akzeptierter Artikel, eine Titelkorrektur, ein spaetes Signal oder ein Backfill darf eine
`CLOSED`-Story deterministisch wieder `ACTIVE` setzen. Bleibt die Neubewertung ohne Auswirkung,
bleibt sie `CLOSED` und erhaelt nur einen Audit-Eintrag.

## Mitgliedschaft, Merge und Split

### Historisierte Mitgliedschaft

Eine Mitgliedschaft enthaelt mindestens:

```text
storyId
articleRef
clusteringVersion
validFromRunId
validToRunId oder null
articleInputFingerprint
decisionId
assignmentReason
```

Eine neue Entscheidung mit identischem fachlichem Inhalt ist ein No-op. Bei einer Neuzuordnung
wird die bisherige aktuelle Mitgliedschaft und die neue Mitgliedschaft atomar im selben
Veroeffentlichungsschritt historisiert.

### Erweitern

Eine Story wird erweitert, wenn die neue kanonische Komponente ihre bisherigen Mitglieder plus
weitere Artikel enthaelt. Story-ID und Identitaetsanker bleiben erhalten. Zeitspanne,
Repraesentant, Zustand und Erklaerungsdaten werden aus der aktuellen Mitgliedschaft neu abgeleitet.

### Merge

Wenn eine kanonische Komponente aktuelle Mitglieder mehrerer Story-IDs enthaelt, ist dies ein
Merge. Es ueberlebt deterministisch die Story mit dem kleinsten Tupel
`(createdAt, storyId)`. Alle anderen IDs wechseln zu `SUPERSEDED` und verweisen mit Grund,
Run-ID und Clustering-Version auf die ueberlebende ID. Ein Abruf einer alten ID kann dadurch
eindeutig zum Nachfolger aufgeloest werden.

Die Wahl ueber `createdAt` bezieht sich auf persistierte, bereits veroeffentlichte Identitaeten
und nicht auf die aktuelle Worker-Reihenfolge. Bei einer erstmaligen Gesamtverarbeitung wird die
Erzeugungsreihenfolge durch `(kleinste effectiveAt, kleinste articleRef)` der Komponenten
festgelegt.

### Split

Wenn die Mitglieder einer Story in mehrere kanonische Komponenten zerfallen, behaelt die
Komponente mit dem historischen Identitaetsanker die alte Story-ID. Die uebrigen Komponenten
erhalten neue IDs in der stabilen Reihenfolge ihres jeweiligen
`(kleinsten effectiveAt, kleinsten articleRef)`. Die alte Story dokumentiert alle Split-Nachfolger.

Ist der Identitaetsanker nicht mehr zuordenbar, etwa weil sein korrigierter Titel unbrauchbar ist,
wird die alte Story `SUPERSEDED`; alle verbleibenden Komponenten erhalten neue IDs. Auch dann
bleibt die alte ID mit ihren Nachfolgern und dem Grund aufloesbar. Ein Artikel, der aus einer Story
herausfaellt und keine neue Komponente erhaelt, wird mit historisierter Entscheidung
`UNASSIGNED`.

## Idempotenz, Retry und konkurrierende Laeufe

Fachliche Idempotenzschluessel sind:

```text
Embedding:  Modell-/Dimensions-/Normalisierungsvertrag + titleInputHash
Bewertung:  clusteringVersion + articleRef + articleInputFingerprint
Snapshot:   clusteringVersion + snapshotInputHash + runMode
Entscheid:  Hash aus Snapshot, Pair-Input, Regelversion und Ergebnis
Publish:    clusteringVersion + snapshotInputHash
```

Ein Retry verwendet denselben Snapshot und dieselben gespeicherten Embeddings. Ist der
Publish-Schluessel bereits erfolgreich, liefert er das vorhandene Ergebnis. Ein Retry darf keine
neuen IDs erzeugen.

Embedding-Erzeugung und reine Pair-Berechnung duerfen parallelisiert werden. Fuer das MVP besitzt
jedoch jede Clustering-Version genau einen aktiven Publisher mit Lease und monotonem Fencing-
Token. Jeder Publish prueft zusaetzlich die gelesenen Story-Versionen optimistisch. Ein abgelaufener
Publisher oder eine geaenderte Story-Version darf nicht teilweise schreiben; sein Plan wird
verworfen und auf einem neuen Snapshot berechnet.

Mitgliedschaften, Story-Ableitungen, Zustandswechsel und Lineage eines Laufs werden atomar
veroeffentlicht. Eine konkrete Implementierung darf diese Atomizitaet technisch aufteilen, muss
dann aber ein gleichwertiges, fuer Leser unsichtbares Staging-/Commit-Protokoll besitzen.

## Inkrementell, Backfill und Reprocessing

### Normaler inkrementeller Lauf

Ein inkrementeller Lauf verarbeitet neue Artikel-Input-Fingerprints und deren Impact-Closure in
der aktuell veroeffentlichten Clustering-Version. Er darf `ACTIVE`- und `CLOSED`-Stories erweitern,
wiedereroeffnen, mergen oder splitten. Er aendert nie die Definition seiner Version.

### Backfill

Ein Backfill fuellt fehlende Artikel oder Embeddings mit derselben Clustering-Version und
denselben Regeln nach. Er ist durch ein halboffenes Zeit- oder Referenzintervall, einen benannten
Snapshot und begrenzte Batches definiert. Nach jedem Batch wird ueber denselben Publisher- und
Lineage-Vertrag reconciled. Wiederholung desselben Backfills ist idempotent.

Ein Backfill darf historische `CLOSED`-Stories wiedereroeffnen und kontrolliert umbauen, aber nur
innerhalb seiner nachweislich geschlossenen Impact-Closure. Anzahl geaenderter Mitgliedschaften,
Merge, Split und wiedereroeffnete Stories werden vor und nach Veroeffentlichung ausgewiesen.

### Vollstaendiges Reprocessing

Eine Aenderung von Modell, Dimension, Normalisierung, Zeitregel, Similarity-Schwellwert,
Pair-Rule oder Komponentenregel erzeugt eine neue Clustering-Version. Diese wird in einem
getrennten Shadow-Namespace auf einem eingefrorenen Gesamtsnapshot berechnet. Die alte Version
bleibt lesbar und unveraendert.

Veroeffentlichung der neuen Version erfordert:

1. erfolgreiche technische Vollstaendigkeits- und Dimensionspruefung,
2. die fuer die Regel geltenden Evaluations-Gates,
3. einen Diff zu Storyanzahl, Singleton-Anteil, Mitgliedschaft, Merge und Split,
4. dokumentierte Freigabe und
5. atomaren Wechsel der als aktuell sichtbaren Clustering-Version.

Public IDs werden bei der Promotion durch deterministisches Membership-Overlap zugeordnet:
groesste gemeinsame Artikelanzahl, danach groesster Anteil an der alten Story, danach alte
`storyId` aufsteigend. Bei Merge und Split gelten anschliessend dieselben Gewinner- und
Ankerregeln wie oben. Nicht mehr aktuelle Versionen und ihre Auditdaten werden nicht geloescht.

## Health, Audit und Erklaerbarkeit

### Pro Lauf

Zu speichern beziehungsweise zu melden sind mindestens:

- Run-ID, Modus, Clustering-Version, Snapshot-ID, Watermark und `snapshotInputHash`,
- Start, Ende, Status, Fencing-Token und Retry-Bezug,
- gelesene, geaenderte, uebersprungene und fehlerhafte Artikel,
- Kandidatenanzahl, Pair-Entscheidungen je Ergebnis und Verteilung der Similarity-Werte,
- neue, erweiterte, geschlossene, wiedereroeffnete, gemergte, gesplittete und supersedierte Stories,
- aktuelle Mitgliedschaften, Singletons und `UNASSIGNED` nach Grund,
- Konflikte beim optimistischen Publish und Groesse erweiterter Impact-Closures.

### Embedding-Health

Mindestens folgende Zaehler und Verteilungen sind erforderlich:

```text
PENDING, READY, RETRYABLE_FAILURE, TERMINAL_FAILURE, NOT_REQUIRED
Requests, Inputs, Batches, Latenz und Versuche
Timeouts und Rate Limits
Dimensions-, Nullnorm- und Non-Finite-Fehler
Modelldrift-Faelle
Alter des aeltesten ausstehenden Inputs
Kosten beziehungsweise Tokens nach Modellversion
```

Ein Alarm ist erforderlich bei Dimensionsfehler oder Modelldrift sofort, bei wachsendem
Rueckstand, nach dem fuenften retryfaehigen Fehler sowie bei einem ungewoehnlichen Anstieg von
`UNASSIGNED`, Merge oder Split.

### Pro Entscheidung

Jede Paar- und Mitgliedschaftsentscheidung enthaelt mindestens:

```text
clusteringVersion, runId und snapshotId
beide articleRefs und articleInputFingerprints
effectiveAt-Werte und Zeitquellen
titleInputHashes und Verwendbarkeitsgruende
Embedding-Artefaktschluessel, Vektor-Hashes und Cosine Similarity
Kandidatenrang, Zeitfenster und Similarity-Schwellwert
optionale strukturierte Diagnoseevidenz mit Herkunft und Kennzeichnung `non_decisive`
Pair-Rule-Version, Ergebnis und ausgeloste Regel
Komponentenentscheidung sowie vorherige und neue storyId
```

Alle Kandidaten oberhalb der Similarity-Grenze werden je Bewertung gespeichert oder ueber einen
unveraenderlichen Snapshot rekonstruierbar referenziert. Fuer Artikel ohne Kandidaten wird der
beste exakte Score unterhalb der Grenze als Diagnose gespeichert. Eine Erklaerung nennt damit die
ausgeloeste Schwellenregel und den knappsten abgelehnten Vergleich.

## Ende-zu-Ende-Szenarien

| Nr. | Eingang | Erwarteter Ausgang | Geschuetzte Invariante |
|---:|---|---|---|
| 1 | neuer Artikel mit verwendbarem, bisher unbekanntem Titel und erfolgreichem Embedding | neue `ACTIVE`-Singleton-Story im Shadow-Namespace; noch nicht produktsichtbar | kein erzwungener unsicherer Merge und keine Veroeffentlichung ohne Freigabe |
| 2 | drei Agenturartikel mit paarweiser beziehungsweise Medoid-Similarity mindestens 0,70 innerhalb 24 h | eine Story mit drei Mitgliedern und Medoid als Vertreter | deterministische Kandidaten und hoechstens eine Mitgliedschaft |
| 3 | neuer Artikel ohne Titel, GKG-Titel kommt 20 h spaeter | zuerst `UNASSIGNED/TITLE_MISSING`, danach Embedding und normale Bewertung | fehlender Titel ist nicht dauerhaft, Historie bleibt erhalten |
| 4 | `publishedAt` kommt spaeter und verschiebt den Artikel aus dem bisherigen Kandidatenfenster | neuer Fingerprint, alte Mitgliedschaft beendet, deterministische Repartition | Zeitwechsel ist Neubewertung, kein Retry |
| 5 | Archivartikel von 1977 wird 2026 erstmals importiert | `effectiveAt` liegt 1977; keine Mischung mit aktuellen Artikeln nur wegen Ingestion | eindeutige Zeit-Fallback-Regel |
| 6 | derselbe Import und Job werden mehrfach zugestellt | vorhandenes Embedding und Publish-Ergebnis werden wiederverwendet | keine doppelten Artefakte, Stories oder Mitgliedschaften |
| 7 | Embedding-Aufruf laeuft in Timeout oder Rate Limit | retryfaehiger Zustand und Backoff; bis `READY` keine Story-Zuordnung | technische Fehler veraendern keine Fachentscheidung |
| 8 | Anbieter liefert 1.535 statt 1.536 Dimensionen | terminaler Fehler, Quarantaene und Alarm; kein Kandidatenscore | fehlerhafte Vektoren werden nicht still angepasst |
| 9 | Titel wird nach erfolgreichem Embedding korrigiert | neuer Input-Hash und neues Artefakt; altes Artefakt bleibt; Mitgliedschaft wird historisiert | Titelwechsel ist explizite Neubewertung |
| 10 | Ankuendigung und Ruecknahme derselben Hormus-Gebuehr liegen im bisherigen Korpus unter Cosine 0,70 | zwei Story-Komponenten | reine Embedding-Zeit-Regel wird exakt angewendet; oberhalb 0,70 waere ein Fehl-Merge bewusst moeglich |
| 11 | spaetes Signal trifft auf eine seit mehr als 72 h `CLOSED`-Story | neue Bewertung; bei Mitgliedschaftsaenderung `CLOSED -> ACTIVE`, sonst nur Audit | kontrollierte Wiedereroeffnung |
| 12 | zwei Publisher berechnen denselben Snapshot parallel | nur gueltiger Fencing-Token publiziert; der andere Lauf ist No-op oder rechnet neu | Parallelitaet erzeugt keine konkurrierenden Ergebnisse |
| 13 | eine Titelkorrektur verbindet zwei bisherige Stories | deterministischer Merge; aeltere Story-ID bleibt, andere wird `SUPERSEDED` | sichtbare IDs besitzen Lineage |
| 14 | eine Korrektur trennt eine Story in zwei Komponenten | Ankerkomponente behaelt ID, zweite erhaelt neue ID, Mitgliedschaften werden historisiert | Split ist deterministisch und erklaerbar |
| 15 | Backfill wird nach einem Abbruch erneut gestartet | bereits publizierte Batches sind No-op; offene Batches laufen weiter | Backfill ist begrenzt und idempotent |
| 16 | Embedding-Modell oder Pair-Rule wird geaendert | neue Shadow-Clustering-Version und vollstaendiges Reprocessing; Promotion erst nach bewusster Freigabe | Regelwechsel ist vom normalen Lauf getrennt |
| 17 | `Deadline` oder ein anderer versioniert generischer Titel trifft ein | `UNASSIGNED/TITLE_GENERIC`, kein Embedding und keine Story | inhaltsarme Eingaben erzeugen keine falschen Cluster |
| 18 | `A-B` und `B-C` liegen ueber 0,70, aber kein Medoid der Vereinigungsmenge erreicht jedes Mitglied mit 0,70 | Merge der drei Artikel wird abgelehnt; mindestens zwei Stories bleiben | keine Single-Linkage-Kette |

## Bewusst vertagte Alternativen und Entscheidungskriterien

### Approximative Vektorsuche

Sie ist fuer veroeffentlichende MVP-Laeufe abgelehnt. Sie darf erst erwogen werden, wenn eine
Kapazitaetsmessung auf mindestens sieben lueckenlosen Importtagen zeigt, dass die exakte
Similarity-Range das vereinbarte Laufzeit- oder Kostenbudget nicht einhaelt. Eine Alternative muss
gegen alle exakten Treffer der 24-h-Range mindestens `0,99` Recall erreichen, zur finalen
Entscheidung Scores exakt nachrechnen, ihre Index-/Library-Version festhalten und die beobachtete
Abweichung bei wiederholten identischen Abfragen dokumentieren. Liefert sie nicht reproduzierbar
dieselbe Kandidatenmenge, darf sie nur Shadow-Ergebnisse erzeugen.

### Anderer Similarity-Schwellwert oder anderes Zeitfenster

Die Startversion bleibt bewusst bei genau zwei Entscheidungswerten: Zeitabstand und Cosine
Similarity. Eine Senkung von `0,70` kann Recall erhoehen, hat im vorhandenen Korpus aber
Fehl-Merges erzeugt. Eine Aenderung wird deshalb als neue Clustering-Version im Shadow-Modus gegen
einen neuen Holdout bewertet. Zusaetzliche lexikalische oder strukturierte Signale werden nicht
stillschweigend eingefuehrt; dies waere ein neuer fachlicher Vertrag.

### Mehrere Publisher und Mehrfachmitgliedschaft

Beides ist fuer das MVP abgelehnt. Mehrere Publisher werden erst noetig, wenn der einzelne
Publisher trotz paralleler Vorbereitung sein Publish-SLA verfehlt; eine Partitionierung muss dann
grenzueberschreitende Kandidaten und atomare Merge/Split-Entscheidungen nachweislich erhalten.
Mehrfachmitgliedschaft benoetigt einen neuen fachlichen Vertrag und ein erweitertes Korpus fuer
Sammelartikel und Liveblogs.

## Abgrenzung

Dieses Dokument erzeugt keine Flyway-Migration, keinen produktiven Scheduler, keinen Embedding-
Client, keinen Vektorindex und keine REST API. Es waehlt kein konkretes Persistenzprodukt. Die
erste technische Umsetzung muss Schema, Job und Tests aus diesem Vertrag ableiten und darf die
hier festgelegten Freigabe-Gates nicht still erweitern.
