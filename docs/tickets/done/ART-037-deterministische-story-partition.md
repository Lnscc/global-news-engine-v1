# ART-037: Deterministische Story-Partition berechnen

Status: erledigt
Bereich: stories

## Kontext

ART-036 erzeugt unveraenderliche Story-Snapshots und exakte Pair-Entscheidungen. Es fehlt die im
Story-Verarbeitungsvertrag definierte kanonische Partition dieser Snapshot-Mitglieder in
Story-Komponenten mit einem repraesentativen Medoid.

## Ziel

Ein deterministischer Clusterkern berechnet fuer einen eingefrorenen Snapshot reproduzierbare
Komponenten und Medoide. Gleiche Eingaben liefern unabhaengig von Eingabe- oder Worker-Reihenfolge
dasselbe Ergebnis.

## Umfang

- kanonische Singleton-Ausgangsmenge bilden
- Komponenten nach der Medoid-Regel aus `docs/story-processing-contract.md` zusammenfuehren
- Radius-, Zeitfenster- und Tie-Break-Regeln der Clustering-Version anwenden
- abgelehnte Vereinigungen nach Komponentenaenderungen erneut bewerten
- Single-Linkage-Ketten verhindern
- Ergebnis mit Komponentenmitgliedern, Medoid und entscheidungsrelevanter Evidenz bereitstellen

## Akzeptanzkriterien

- jedes verwendbare Snapshot-Mitglied gehoert genau einer Komponente an
- der Medoid ist ein Mitglied mit der hoechsten mittleren quantisierten Cosine Similarity
- Gleichstaende werden ueber `(effectiveAt, articleRef)` stabil aufgeloest
- eine Vereinigung wird nur akzeptiert, wenn jedes Mitglied Radius und Zeitfenster zum neuen
  Medoid einhaelt
- Eingabereihenfolge und wiederholte Ausfuehrung veraendern weder Komponenten noch Medoide
- das Kettenszenario aus dem Verarbeitungsvertrag bleibt in mindestens zwei Komponenten getrennt
- Unit-Tests decken Singletons, Mehrartikel-Komponenten, Gleichstaende, Grenzwerte und abgelehnte
  Vereinigungen ab
- das Evaluationskorpus aus ART-032 kann ohne Story-Leakage gegen das Ergebnis ausgewertet werden

## Abgrenzung

Dieses Ticket persistiert keine Stories oder Mitgliedschaften und veraendert keinen
Clustering-Versionsstatus. Publishing, Merge-/Split-Lineage, REST API und UI sind nicht enthalten.

## Offene Fragen

Keine.

## Abgleich ART-041

Der Clusterkern bleibt unveraendert erforderlich. Er liefert die aktuelle Partition mit
Evidenz; dauerhafte Zuordnungshistorie und Story-Identitaeten gehoeren nicht in diesen Kern.

## Implementierungskommentar

Implementiert am 2026-09-25:

- `StoryPartitionService.calculate(snapshotId)` berechnet die kanonische Partition aus
  eingefrorenen Snapshot-Mitgliedern und dem gespeicherten Versionsvertrag.
- Singleton-Start, quantisierte Medoid-Wahl, stabile Zeit-/Referenz-Tie-Breaks und
  Radius-/Zeitpruefung entsprechen dem Verarbeitungsvertrag. Abgelehnte Vereinigungen
  werden nach Aenderung einer beteiligten Komponente erneut geprueft.
- Das Ergebnis enthaelt Komponenten, Medoide, Snapshot-Referenzen sowie Mitgliedsevidenz
  fuer akzeptierte und abgelehnte Merge-Versuche. `ExactCosine` und die bestehende
  Vektorvalidierung werden wiederverwendet.
- Unit-Tests decken leere Snapshots, Singletons, Medoid-Wahl, Gleichstaende, quantisierte
  Schwellen, Zeitgrenzen, Ketten, abgelehnte Merges, erneute Versuche und Eingabepermutationen ab.
- Der PostgreSQL-Snapshot-Test prueft die Berechnung aus gespeicherten Artefakten und
  identische Ergebnisse eines alten Snapshots nach Aenderung aktueller Inputs.
- Ein lokaler Korpusexport und Java-Korpustest berechnen Kalibrierung und Evaluation
  getrennt ohne Story-Leakage. Ergebnis: `12/0/2/29` beziehungsweise `18/0/1/41`
  fuer TP/FP/FN/TN; dies ist kein neuer Holdout-Freigabenachweis.
- Aufruf, Korpusauswertung und Kapazitaetsgrenzen sind in `docs/story-processing-contract.md`
  dokumentiert. Der Kern publiziert und persistiert keine Stories und wird noch nicht
  automatisch vom Scheduler ausgefuehrt.

Validierung: 77 Java-Tests einschliesslich des lokalen Korpustests und vier bestehende
Python-Auswertungstests erfolgreich. Nach dem Start der lokalen PostgreSQL-Compose-Instanz
wurden am 2026-09-25 auch die vier PostgreSQL-Snapshot-Integrationstests erfolgreich ausgefuehrt
(keine Fehler, keine uebersprungenen Tests). Die sechs Clusterkern-Unit-Tests bestanden im
selben Lauf erneut:

```powershell
.\mvnw.cmd verify "-Dtest=StoryPartitionServiceTests" "-Dit.test=StorySnapshotServicePostgresIT"
```
