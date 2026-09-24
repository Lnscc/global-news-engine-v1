# ART-037: Deterministische Story-Partition berechnen

Status: offen
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
