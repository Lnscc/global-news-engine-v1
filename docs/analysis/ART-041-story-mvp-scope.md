# ART-041: Abgestimmter Story-MVP-Umfang

Stand: 2026-09-24. Die Produktentscheidungen sind abgestimmt und in
`../story-processing-contract.md`, `../stories.md`, `../story-data-model.md` sowie
ART-037 bis ART-040 abgeglichen. ART-041 wurde auf Nutzerwunsch abgeschlossen und nach
`../tickets/done` verschoben; die Status der Folgetickets bleiben unveraendert.

## Bestaetigte Produktentscheidungen

- Stories werden aktualisiert. Neue Artikel erzeugen keine neue Story-ID.
- Beim Zusammenfuehren bleibt eine Story erhalten; beim Aufteilen bleibt eine erhalten
  und abgetrennte Teile erhalten neue IDs. Vergangene Zusammenfuehrungen und Aufteilungen
  muessen nicht nachvollziehbar sein; Nachfolgerverweise sind nicht erforderlich.
- Nur die aktuelle Artikelzuordnung mit Begruendung ist erforderlich. Fruehere
  Zuordnungen muessen weder abrufbar noch rekonstruierbar bleiben.
- Genau ein Gruppierungsverfahren laeuft produktiv. Verbesserungen werden vor dem
  Einsatz getestet und ersetzen das bisherige Verfahren.
- Auch beim Verfahrenswechsel werden bestehende Stories weiterverwendet und aktualisiert.
  Alle Stories pauschal neu anzulegen ist ausgeschlossen.

## Erforderliche und vertagte Garantien

| Garantie | Einstufung | Begruendung und Konsequenz |
|---|---|---|
| Titel-Embedding, effektive Artikelzeit, exakte Suche, deterministische Medoid-Partition | erforderlich, unveraendert | Gleiche gespeicherte Eingaben liefern dieselben Gruppen; die fachlichen Regeln werden nicht vereinfacht. |
| Hoechstens eine aktuelle Mitgliedschaft, Singleton und begruendetes UNASSIGNED | erforderlich | Eindeutige Ergebnisse ohne erzwungene Verbindungen. |
| Unveraenderliche Regeln und gespeicherte Embeddings | erforderlich | Regelwechsel und Retry bleiben unterscheidbar; aktuelle Ergebnisse sind ohne erneuten Modellaufruf erklaerbar. |
| Eingefrorener Snapshot und Evidenz des aktuellen Ergebnisses | erforderlich | Referenzierte Inputs, Vektoren und Scores muessen erhalten bleiben. |
| Dauerhafte Mitgliedschafts- und Zuordnungshistorie | vertagt, Nutzerentscheidung | Keine Rekonstruktion frueherer Artikelzuordnungen als Produktversprechen. |
| Dauerhafte Aufbewahrung aller vergangenen Snapshots und Paarentscheidungen | vertagt als Produktgarantie | Technische Aufbewahrung fuer aktuelle Evidenz, offene Laeufe und Retries bleibt erforderlich. Keine Loeschfreigabe. |
| Stabile ID bei Erweiterung und Retry | erforderlich | Die bestehende Story wird aktualisiert. |
| Identitaet bei Merge, Split und Verfahrenswechsel | erforderlich | Bestehende Stories nach denselben deterministischen Regeln weiterverwenden; neue IDs nur fuer neue oder abgetrennte Komponenten. |
| Merge-/Split-Historie und Nachfolgeraufloesung | vertagt, Nutzerentscheidung | Abgeloeste IDs koennen 404 liefern; erhaltene IDs zeigen den aktuellen Stand. |
| Neubewertung nach korrigierten oder spaeten Eingaben | erforderlich | Aktuelle Gruppen entsprechen weiterhin der kanonischen Partition. |
| ACTIVE/CLOSED und Wiedereroeffnen | erforderlich, bisheriger Vertrag beibehalten | Aktueller Betriebszustand ist von einer dauerhaften Zuordnungshistorie getrennt; diese Anforderung wurde nicht abbestellt. |
| SUPERSEDED als dauerhaft abrufbare Story mit Nachfolgern | vertagt | Ein interner Status darf bestehen bleiben, ohne historischen Produktzugriff zu versprechen. |
| Atomare Veroeffentlichung und idempotenter Retry | erforderlich | Keine halben oder doppelten Zuordnungen; alte Retries duerfen keinen veralteten Stand wiederherstellen. |
| Ein gueltiger Publisher je Version, Lease/Fencing und Konflikterkennung | erforderlich, bisheriger Vertrag beibehalten | Verhindert konkurrierende Veroeffentlichungen; Versionswechsel wird mit dem aktuellen Stand koordiniert. |
| Parallele Vorbereitung von Embeddings und Berechnungen | erlaubt | Ein produktives Verfahren bedeutet nicht einen einzigen Worker. |
| Dauerhafte parallele Vergleichsvarianten | vertagt, Nutzerentscheidung | Ein produktiver Regelsatz reicht; separate Tests vor einem Wechsel bleiben moeglich. |
| Neue Regelversion, vollstaendiges Reprocessing und atomarer Ersatz | erforderlich | Keine Vermischung alter und neuer Regeln; dabei bestehende Story-Identitaeten erhalten. |
| Neuer Holdout, bestehende Qualitaets-Gates und dokumentierte Freigabe | erforderlich | Der alte Evaluationssplit ist als unabhaengiger Nachweis verbraucht. |
| Technische Versions-/Freigabehistorie und expliziter Rollback | erforderlich, bisheriger Vertrag beibehalten | Sichere Betriebswechsel bleiben erhalten; kein dauerhaft laufendes zweites Verfahren und kein Produktzugriff auf alte Mitgliedschaften. |
| Inkrementelle Verarbeitung und begrenzter Backfill | erforderlich, bisheriger Vertrag beibehalten | Keine unbestaetigte Umstellung auf laufende Vollberechnung; das Ergebnis muss der kanonischen Partition entsprechen. |
| Laufstatus, Fehlergruende, Mengen und Embedding-Health | erforderlich | Fehler, Rueckstand und Retry-Verhalten bleiben beobachtbar; Betriebszaehler erfordern keine fachliche Lineage. |

Die nicht bestaetigten Vorschlaege, Story-Zustaende, inkrementelle Verarbeitung und
Rollback zu streichen, wurden nicht uebernommen. Ihre bisherigen Garantien bleiben bestehen.

## Auswirkungen und Abgleich

- Der Story-Vertrag beschreibt aktuelle Zuordnungen statt verpflichtender Historien,
  ein produktives Verfahren sowie ID-Erhalt auch bei Verfahrenswechsel. Bei fehlendem
  Split-Anker bleibt die ID bei der Komponente mit den meisten bisherigen Mitgliedern;
  stabile Tie-Breaks und eine Split-vor-Merge-Zuordnung verhindern doppelte IDs.
- ART-037 behaelt den deterministischen Clusterkern ohne Story-Persistenz.
- ART-038 publiziert aktuelle Ergebnisse mit stabilen IDs, ohne Historienpflicht.
  Geaenderte Nachbarschaften und veraltete Retries werden ausdruecklich verifiziert.
- ART-039 behaelt Freigabe und atomaren Versionswechsel; IDs werden nach den normalen
  Merge-/Split-Regeln weiterverwendet. Dauerhafte Challenger sind keine Voraussetzung.
- ART-040 liefert aktuelle Stories, abgeloeste IDs liefern 404 ohne Nachfolger.
  Die API ist noch nicht implementiert; Postman-Umsetzung bleibt Bestandteil von ART-040.
- Die Datenmodell-Dokumentation trennt implementiertes Schema und neuen Zielumfang.
  Insbesondere Historien-Constraints, Zuordnungsschluessel und Versionsbindung von IDs
  muessen vor einer Implementierung gegen ART-042/ART-043 geprueft werden.

## Aufbewahrung und weitere Arbeit

Keine Migration, Datenloeschung oder Produktionsaenderung ist Bestandteil von ART-041.
Vorhandene Historien und Shadow-Versionen bleiben bestehen. ART-042 belegt, welche
Strukturen weiterhin technisch erforderlich sind. Erst ein bestaetigter Vorschlag in
ART-043 darf Aufbewahrungsfristen, Datenerhalt und eine konkrete Migration festlegen.
Diese technischen Folgeentscheidungen blockieren den fachlichen MVP-Vertrag nicht.

Offene Produktentscheidungen fuer ART-041: keine.
