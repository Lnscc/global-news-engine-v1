# ART-039: Story-Clustering-Version freigeben

Status: offen
Bereich: stories, operations, architecture

## Kontext

Story-Ergebnisse werden zunaechst in einer `SHADOW`-Clustering-Version erzeugt. Ohne einen
kontrollierten Freigabeprozess darf diese Version nicht zur produktsichtbaren Story-Wahrheit
werden.

## Ziel

Eine fachlich freigegebene und technisch vollstaendige Shadow-Version kann atomar zur aktuellen
`ACTIVE`-Version promoviert werden. Es laeuft genau ein Verfahren produktiv. Bestehende
Stories werden weiterverwendet und aktualisiert; die technische Freigabehistorie bleibt lesbar.

## Umfang

- technische Vollstaendigkeit und Embedding-Dimension pruefen
- versionierte Evaluations-Gates auf einem neuen unabhaengigen Holdout auswerten;
  der fuer die Schwellenwahl verwendete ART-032-Split ist kein Freigabenachweis
- Diff fuer Storyanzahl, Singleton-Anteil, Mitgliedschaften, Merge und Split bereitstellen
- dokumentierte fachliche Freigabe verlangen
- bestehende Public IDs nach denselben Merge-/Split-Regeln wie im normalen Lauf weiterverwenden
- den sichtbaren Versionswechsel atomar ausfuehren und die technische Freigabe protokollieren
- fehlgeschlagene oder wiederholte Promotion ohne Teilzustand behandeln

## Akzeptanzkriterien

- ohne erfolgreiche technische Pruefung, Evaluations-Gates und dokumentierte Freigabe findet
  keine Promotion statt
- eine erfolgreiche Promotion macht genau eine Clustering-Version zur aktuellen `ACTIVE`-Version
- der Wechsel ist fuer Leser atomar sichtbar
- Public IDs werden bei gleicher Eingabe deterministisch zugeordnet
- unveraenderte und erweiterte Stories behalten ihre IDs; neue oder abgetrennte Komponenten
  erhalten neue IDs, ohne erforderliche Nachfolgerhistorie
- ein inzwischen geaenderter sichtbarer Stand erzwingt vor dem Wechsel einen neuen ID-Abgleich
- dauerhafte Challenger-Laeufe sind keine Freigabevoraussetzung
- der vorherige Story-Stand mit seinen Mitgliedschaften muss nach erfolgreicher Promotion
  nicht erhalten bleiben; bestehende Stories duerfen unter Erhalt ihrer Public IDs aktualisiert werden
- Statuswechsel und Freigabegrund sind auditierbar
- die Wiederholung einer erfolgreichen Promotion ist ein No-op
- PostgreSQL-Integrationstests decken erfolgreiche, abgelehnte, wiederholte und konkurrierende
  Promotion sowie ID-Erhalt, Merge/Split und Aenderung des Ausgangsstandes ab

## Abgrenzung

Dieses Ticket aendert weder Clustering-Regeln noch Story-Inhalte und stellt keine REST API oder UI
bereit.
Die zurueckgestellte Schemavereinfachung aus ART-042/ART-043 ist keine Voraussetzung.
Das bestehende Schema ist Ausgangspunkt; notwendige Korrekturen fuer den Versionswechsel
vor einer Schemaaenderung konkret mit dem Nutzer klaeren. Kein allgemeiner Datenbankumbau.

## Offene Fragen

- Welche minimale Schemaanpassung erlaubt das Aktualisieren bestehender Stories auf die
  neue Version unter Erhalt ihrer Public IDs? Der globale Story-Primaerschluessel allein
  loest dies nicht: Fremdschluessel binden Stories, Mitgliedschaften und Entscheidungen
  an dieselbe Clustering-Version; bestehende Historientabellen sind gegen Aenderungen geschuetzt.

Diese Frage bei der Umsetzung gegen den aktuellen Code pruefen; die Zurueckstellung
der Vereinfachung hebt den geforderten ID-Erhalt nicht auf.

## Anforderungsentscheidung (2026-09-26)

Mit dem Nutzer abgestimmt: Der alte Story-Stand muss nach einer erfolgreichen Promotion
nicht aufbewahrt werden. Erforderlich bleibt die technische Freigabehistorie mit vorheriger
und neuer Version, Zeitpunkt, dokumentierter fachlicher Freigabe und Freigabegrund.
Eine Rekonstruktion alter Story-Mitgliedschaften ist keine Anforderung.
Public-ID-Erhalt, atomarer Wechsel und unveraenderter sichtbarer Stand bei fehlgeschlagener
Promotion bleiben erforderlich. Diese Entscheidung legt noch keine Schemaaenderung fest.
