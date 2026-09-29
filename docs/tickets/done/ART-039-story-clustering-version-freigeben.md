# ART-039: Story-Clustering-Version freigeben

Status: erledigt
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

## Geklaerte Schemafrage

Fremdschluessel binden Stories, Mitgliedschaften und Entscheidungen an dieselbe
Clustering-Version; bestehende Historientabellen sind gegen Aenderungen geschuetzt.
Die freigegebene separate Public ID erlaubt stabile oeffentliche Identitaeten ohne
Umbau dieser internen Beziehungen (siehe Umsetzungsvorschlag).

## Anforderungsentscheidung (2026-09-26)

Mit dem Nutzer abgestimmt: Der alte Story-Stand muss nach einer erfolgreichen Promotion
nicht aufbewahrt werden. Erforderlich bleibt die technische Freigabehistorie mit vorheriger
und neuer Version, Zeitpunkt, dokumentierter fachlicher Freigabe und Freigabegrund.
Eine Rekonstruktion alter Story-Mitgliedschaften ist keine Anforderung.
Public-ID-Erhalt, atomarer Wechsel und unveraenderter sichtbarer Stand bei fehlgeschlagener
Promotion bleiben erforderlich. Diese Entscheidung legt noch keine Schemaaenderung fest.

## Freigegebener Umsetzungsvorschlag (2026-09-26)

Die bestehende interne `stories.id` und ihre Fremdschluessel bleiben erhalten.
Eine zusaetzliche `stories.public_id` traegt die versionsuebergreifende oeffentliche ID:

- `public_id UUID NOT NULL`, initial mit der bisherigen `id` befuellt;
  eindeutig je `(clustering_version_id, public_id)`.
- Bei Promotion erhaelt die passende Shadow-Story die bisherige Public ID.
  Die Zuordnung verwendet dieselben Merge-/Split-Regeln wie der normale Publisher.
  Identitaetsanker und urspruenglicher Entstehungszeitpunkt muessen dabei erhalten bleiben,
  damit spaetere Laeufe dieselben Identitaetsregeln anwenden.
- Oeffentliche Aufloesung erfolgt ueber Public ID und aktuelle `ACTIVE`-Version.
  Interne Mitgliedschaften und Entscheidungen referenzieren weiterhin die interne ID.
- Ein partieller Unique-Index auf den Versionsstatus erlaubt hoechstens eine
  `ACTIVE`-Version. ID-Zuordnung, Statuswechsel und Freigabeprotokoll werden in einer
  Transaktion geschrieben; ein geaenderter Ausgangsstand erfordert einen neuen Abgleich.
- Die vorhandene `story_clustering_version_status_history` wird fuer das
  Freigabeprotokoll weiterverwendet.

Damit entfaellt ein Umbau der sechs Story-Fremdschluessel und ihrer geschuetzten
Historientabellen. Bereits gespeicherte technische Zeilen bleiben vorerst bestehen;
eine Rekonstruktion oder Aufbewahrung alter Story-Staende wird nicht zugesichert.
Eine automatische Datenbereinigung ist nicht Teil dieses Vorschlags.


## Implementierungskommentar (2026-09-26)

Umgesetzt: V25 fuehrt Public IDs ein; V26 begrenzt PostgreSQL auf eine aktive Version.
`StoryPromotionService` prueft Vollstaendigkeit, Modell/Dimension und publizierte Partition,
berechnet Diff und versionierte Holdout-Gates und verlangt eine dokumentierte Freigabe.
Promotion und normaler Publisher verwenden gemeinsame Identitaetsregeln. Ein veraenderter
Stand erzwingt einen neuen Abgleich; ID-Uebernahme, Statuswechsel und Audit sind atomar.
Wiederholungen sind No-ops. Aktive Versionen werden weiterverarbeitet, retirierte nicht.
Technische Zeilen bleiben bestehen; eine alte Story-Ansicht wird nicht zugesichert.

PostgreSQL-Tests pruefen Promotion, Ablehnung, Wiederholung, konkurrierende Aufrufe,
Rueckabwicklung, atomare Lesbarkeit, ID-Erhalt, Erweiterung, Merge/Split, geaenderten
Ausgangsstand und Dimensionsfehler. Gate-Tests verhindern die Wiederverwendung des
ART-032-Korpus und unvollstaendige Evaluationsnachweise. Bedienung und Gate-Grenzen stehen
in `docs/operations.md`.

Keine echte Promotion durchgefuehrt: Die Implementierung ist abgeschlossen, die vorhandene
24-h-Version bleibt jedoch `SHADOW`. Die bekannte Stichprobe dokumentiert Qualitaetsprobleme;
ein neuer unabhaengiger fachlich gelabelter Holdout und eine fachliche Freigabe sind vor einem
realen Wechsel weiterhin erforderlich. Testdaten sind kein Freigabenachweis.

Validierung am 2026-09-29: `mvnw verify` bestand vollstaendig, einschliesslich aller Unit-Tests
und 40 PostgreSQL-Integrationstests. Darin enthalten sind neun Promotionstests, die erfolgreichen,
abgelehnten, wiederholten und konkurrierenden Wechsel sowie ID-Erhalt, Merge/Split,
Ausgangsstands-Aenderungen, atomare Lesbarkeit und Rollback pruefen.
