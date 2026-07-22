# ART-033: Story-Lebenszyklus und Verarbeitungsvertrag festlegen

Status: erledigt
Bereich: stories, architecture, operations

## Kontext

Artikel und ihre Signale treffen inkrementell und teilweise zeitversetzt ein. Ein Artikel kann
zunaechst nur ueber EVENTS oder MENTIONS bekannt sein und spaeter einen GKG-Titel, Entitaeten oder
eine genauere Publikationszeit erhalten. Ein Story-Clusterer muss deshalb mehr leisten als einen
einmaligen statischen Gruppenlauf.

Vor dem produktiven Datenmodell muss entschieden werden, wie Story-Identitaet, Mitgliedschaften,
Neubewertung und konkurrierende Job-Laeufe deterministisch behandelt werden.

## Ziel

Ein technologieunabhaengiger Verarbeitungsvertrag definiert den Lebenszyklus von Stories und die
idempotente Verarbeitung neuer oder nachtraeglich angereicherter Artikel.

## Umfang

```text
- effektiven Artikelzeitpunkt mit eindeutiger Fallback-Regel festlegen
- Eintritts- und Neubewertungsausloeser fuer Artikel definieren
- Erzeugung, Wiederverwendung und Invalidierung von Titel-Embeddings definieren
- Modellkennung, Modellversion, Vektordimension, Titel-Normalisierung und Eingabe-Hash als Teil
  der Clustering-Version festlegen
- Kandidatenfenster und Behandlung verspaeteter Signale fachlich festlegen
- deterministische Kandidatensuche aus Zeitfenster, Top-k, Similarity und stabiler Tie-Break-Regel
  festlegen; exakte und approximative Vektorsuche bewusst gegeneinander entscheiden
- Zustaende einer Story und erlaubte Zustandsuebergaenge beschreiben
- Regeln fuer Erweitern, Schliessen und Wiedereroeffnen definieren
- Merge- und Split-Verhalten sowie Stabilitaet externer Story-IDs entscheiden
- Kardinalitaet und Historisierung von Artikelmitgliedschaften festlegen
- Idempotenzschluessel und Wiederanlaufverhalten beschreiben
- Parallelverarbeitung, Sperren oder optimistische Konflikterkennung konzeptionell klaeren
- Backfill, Reprocessing nach Regelwechseln und Versionierung des Clusterers festlegen
- Verhalten bei Embedding-Timeout, Rate Limit, ungueltiger Vektordimension, Modellwechsel und
  nicht verwendbarem Titel festlegen
- benoetigte Health-, Audit- und Erklaerbarkeitsinformationen definieren
```

## Zu entscheidende Invarianten

```text
- identische Eingangsdaten und dieselbe Clustering-Version liefern dieselbe Zuordnung
- dieselbe Modellversion und derselbe normalisierte Titel werden ueber einen stabilen Eingabe-Hash
  erkannt und erzeugen nicht mehrfach konkurrierende Embedding-Artefakte
- ein Titel- oder Modellwechsel ist eine explizite Neubewertung und kein stiller normaler Retry
- ein Retry erzeugt weder doppelte Stories noch doppelte Mitgliedschaften
- eine sichtbare Story-ID verschwindet bei Merge oder Split nicht ohne dokumentierte Regel
- die Ursache jeder automatischen Zuordnung bleibt nachvollziehbar
- spaet eintreffende Daten koennen verarbeitet werden, ohne abgeschlossene Zeitraeume unkontrolliert umzubauen
- Regel- oder Modellwechsel sind von normalen inkrementellen Laeufen unterscheidbar
- eine Zuordnung protokolliert Similarity, Modellversion, Zeitfenster, verglichene Kandidaten und
  die strukturierten Bestaetigungs- oder Trennsignale
```

## Akzeptanzkriterien

```text
- Story-Zustaende und alle erlaubten Uebergaenge sind dokumentiert
- effektiver Artikelzeitpunkt und Fallback-Reihenfolge sind eindeutig festgelegt
- Verhalten fuer neue Artikel, neue Signale, geaenderte Features und verspaetete Daten ist beschrieben
- Verhalten fuer fehlende, fehlgeschlagene, veraltete und nach Titelkorrektur neu zu erzeugende
  Embeddings ist beschrieben
- Mitgliedschaft, Merge, Split, Schliessen und Wiedereroeffnen besitzen deterministische Regeln
- Kandidatensuche und Tie-Breaking liefern bei identischem Embedding-Bestand eine deterministische
  Reihenfolge; eine approximative Suche ist nur mit dokumentierter Reproduzierbarkeitsgrenze zulaessig
- stabile Identitaet, Idempotenz, Retry und Parallelverarbeitung sind konzeptionell geklaert
- Embedding-Idempotenzschluessel, Modell-/Input-Versionierung, Timeout-, Rate-Limit- und
  Dimensionsfehler sowie deren Health-Metriken sind konzeptionell geklaert
- Backfill und vollstaendiges Reprocessing besitzen getrennte, sichere Ablaeufe
- mindestens zehn Ende-zu-Ende-Szenarien beschreiben Eingang, Ausgang und erwartete Invarianten
- offene technische Alternativen sind mit Entscheidungskriterien statt als ungebundene TODOs dokumentiert
- das Ergebnis reicht aus, um anschliessend Story-Schema, Cluster-Job und Tests zu entwerfen
- es erfolgen noch keine Aenderungen an produktivem Datenbankschema oder REST API
```

## Abhaengigkeiten

Das Ticket baut auf ART-030 auf und beruecksichtigt die Erkenntnisse aus ART-031 und ART-032. Es
ist die letzte fachlich-architektonische Voraussetzung vor dem produktiven Story-Datenmodell und
Cluster-Job.

## Abgrenzung

Flyway-Migrationen, produktiver Scheduler, Clustering- oder Embedding-Implementierung, Story REST
API, konkrete Persistenztechnologie und Auswahl eines Vektorindexprodukts sind nicht Teil dieses
Tickets.

## Implementierungskommentar (2026-07-22)

Der technologieunabhaengige Vertrag ist in `docs/story-processing-contract.md` dokumentiert und
vom Architektur-Einstieg sowie der Story-Definition verlinkt. Festgelegt wurden insbesondere
`publishedAt` mit Fallback auf `firstSeenAt`, eine exakte symmetrische 24-h-Similarity-Range ohne
Top-k-Begrenzung, versionierte und unveraenderliche Embedding-Artefakte, eventgetriebene
Neubewertung plus Revisit
nach 24 und 72 Stunden, ein eingefrorener Snapshotvertrag sowie ein einzelner, durch Fencing und
optimistische Konfliktpruefung geschuetzter Publisher je Clustering-Version.

Die auf Top-50 und Cosine `0,224867` beruhende Embedding-Baseline aus ART-032 wurde auf Wunsch zu
einem reinen Embedding-Zeit-Vertrag weiterentwickelt. Die erste Pair-Rule
verbindet alle innerhalb 24 Stunden liegenden Artikelpaare mit exakter Cosine Similarity
`>= 0,70`; Entitaeten und GDELT-Signale beeinflussen die Entscheidung nicht. Leere und durch
ART-031 belegte generische Titel werden als Eingangsqualitaetsregel nicht geclustert. Auf dem
bisherigen Evaluationssplit ergibt die Paarregel 18 TP, 0 FP, 1 FN und 41 TN, also Precision
1,0000, Recall 0,9474 und F1 0,9730. Da der Schwellwert unter Kenntnis dieses Splits gewaehlt wurde,
startet die Version ausschliesslich im Shadow-Modus und benoetigt vor einer Produktionsfreigabe
einen neuen unabhaengigen Holdout.

Die Story-Partition wird nicht per Single-Linkage gebildet. Ein deterministisches agglomeratives
Medoid-Verfahren fuehrt immer das aehnlichste zulaessige Clusterpaar zusammen und akzeptiert den
Merge nur, wenn danach jedes Mitglied zum neuen Medoid mindestens Cosine `0,70` und hoechstens 24
Stunden Abstand besitzt. Parallel laufende 48-h- und 72-h-Challenger duerfen das 24-h-Fenster nur
nach einem Recall-Gewinn ohne Unterschreitung von Precision `0,98` ersetzen.

Der Vertrag definiert `ACTIVE`, `CLOSED` und `SUPERSEDED` samt erlaubten Uebergaengen,
deterministische Merge-/Split- und Story-ID-Lineage-Regeln, historisierte Einzelmitgliedschaft,
Idempotenzschluessel, Embedding-Fehlerbehandlung, getrennte Backfill- und Reprocessing-Ablaufe,
Health- und Auditdaten sowie achtzehn Ende-zu-Ende-Szenarien. Approximative Suche, mehrere
Publisher und Mehrfachmitgliedschaft sind fuer das MVP mit messbaren Kriterien bewusst vertagt.

Produktives Datenbankschema, Datenwerte und REST API wurden nicht veraendert. Der Ticketstatus
wurde nach ausdruecklicher Freigabe am 22. Juli 2026 auf `erledigt` gesetzt.
