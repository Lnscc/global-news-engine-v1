# ART-038: Story-Ergebnisse atomar publizieren

Status: erledigt
Bereich: stories, operations

## Kontext

Nach ART-037 liegt eine deterministische Partition vor. ART-041 begrenzt das Produkt auf
aktuelle Stories und begruendete Zuordnungen ohne erforderliche Mitgliedschafts- oder
Merge-/Split-Historie. Das bestehende Schema enthaelt noch weitergehende Historienstrukturen.
ART-042/ART-043 sind auf Nutzerwunsch zurueckgestellt und keine Voraussetzung fuer dieses
Ticket. Vorhandene Strukturen duerfen intern weiterverwendet werden, ohne eine fachliche
Historienfunktion anzubieten.

## Ziel

Ein Publisher ueberfuehrt ein Clusterergebnis atomar und idempotent in Stories und
Mitgliedschaften der zugehoerigen Clustering-Version. Erweiterungen, Wiedereroeffnungen, Merge,
Split und `UNASSIGNED` liefern einen erklaerbaren aktuellen Stand und sind retry-sicher.

## Umfang

- Komponenten gegen den zuletzt publizierten Zustand derselben Clustering-Version abgleichen
- stabile Story-IDs, Identitaetsanker und repraesentative Artikel bestimmen
- aktuelle Assignment-Entscheidungen und Mitgliedschaften mit Begruendung schreiben
- Story-Zustaende und erlaubte Uebergaenge anwenden
- bestehende IDs nach den Merge-/Split-Regeln erhalten, ohne Nachfolgerhistorie
- Publish mit Lease, Fencing-Token, optimistischer Versionierung und Publish-Key absichern
- aktuelle Story-Ableitungen, Mitgliedschaften, Begruendungen und Commit atomar schreiben
- Run-, Konflikt- und Ergebnismetriken bereitstellen

## Akzeptanzkriterien

- ein erfolgreicher Lauf erzeugt fuer jede Komponente genau eine aktuelle Story-Zuordnung
- ein Artikel besitzt je Clustering-Version hoechstens eine aktuelle Mitgliedschaft
- unveraenderte Zuordnungen sind ein No-op und erzeugen keine neue Mitgliedschaft
- Erweiterung, Schliessen und Wiedereroeffnen erhalten die Story-ID
- bei einem Merge ueberlebt deterministisch die im Vertrag festgelegte Story-ID
- bei einem Split behaelt die Ankerkomponente oder der vertragliche Ersatz ihre Story-ID;
  nur abgetrennte Komponenten erhalten neue IDs, auch bei gleichzeitigem Merge und Split
- unbrauchbare oder nicht entscheidbare Artikel erhalten eine begruendete `UNASSIGNED`-Entscheidung
- ein Retry desselben Snapshots erzeugt weder neue IDs noch doppelte aktuelle Zuordnungen
- ein veralteter Retry kann keinen abgeloesten Stand wieder sichtbar machen
- geaenderte Nachbarartikel koennen bei gleichem eigenem Input-Fingerprint neu zugeordnet werden
- ein Publisher mit abgelaufenem Fencing-Token kann keinen Teilzustand veroeffentlichen
- PostgreSQL-Integrationstests decken Neuaufnahme, No-op, Erweiterung, Merge, Split, Retry und
  konkurrierende Publisher, fehlenden Anker, kombinierte Merge/Split-Faelle und veraltete Retries ab
- der Lauf bleibt fuer eine `SHADOW`-Version nicht produktsichtbar

## Abgrenzung

Die Promotion einer Clustering-Version, eine REST API und eine UI sind nicht enthalten.
Keine allgemeine Schemavereinfachung oder Entfernung bestehender Historienstrukturen.
Falls das bestehende Schema eine geforderte Funktion verhindert, den konkreten Konflikt
und die kleinste notwendige Korrektur vor einer Schemaaenderung mit dem Nutzer klaeren.

## Offene Fragen

- Wie wird eine neue Entscheidung bei gleichem Input, aber geaenderter Nachbarschaft
  trotz des bisherigen inputbezogenen Unique-Constraints gespeichert?
- Wie erhalten unbrauchbare Inputs einen eingefrorenen Snapshot-Kontext fuer UNASSIGNED?

Diese Fragen bei der Umsetzung gegen den aktuellen Code pruefen; sie legen keine
Schemaaenderung fest.

## Implementierungskommentar

- Der Snapshot-Lauf publiziert Partition, Stories, aktuelle Mitgliedschaften,
  Assignment-/Paarentscheidungen und Publish-Commit atomar. Lease, monotones Fencing,
  optimistische Story-Versionen und Snapshot-Reihenfolge sichern konkurrierende Laeufe
  sowie bereits publizierte und veraltete Retries ab.
- Split-Nominierung (Anker, sonst groesste Ueberlappung und stabiler Tie-Break) erfolgt
  vor der Merge-Auswahl. Erweiterung, Schliessen und Wiedereroeffnen erhalten IDs;
  No-ops erzeugen keine neuen Mitgliedschaften. Lineage wird nicht geschrieben.
- Mit ausdruecklicher Nutzerfreigabe erweitert V24 den Assignment-Unique-Schluessel um
  den Snapshot. Unbrauchbare und nicht fertige Inputs werden ohne Vektor eingefroren
  und begruendet UNASSIGNED; dafuer ist keine weitere Schemaaenderung erforderlich.
- Run-/Konflikt-/Ergebnismetriken sind vorhanden. SHADOW-Versionen bleiben SHADOW.
- PostgreSQL-Integrationstests pruefen Neuaufnahme, No-op, Erweiterung, Schliessen,
  Wiedereroeffnung, Merge, Split, fehlenden Anker, kombinierte Merge/Split-Faelle,
  Retry, veraltete Retries, konkurrierende Worker, Lease-Ablauf mit Rollback,
  Fencing-/Versionskonflikte, eingefrorene Nichtverfuegbarkeit und Legacy-Runs.
- Verifiziert am 2026-09-25: 76 regulaere Tests erfolgreich; der optionale ART-037-
  Korpustest ist ohne `art037.inputs` uebersprungen. Alle 31 PostgreSQL-Integrationstests
  sind erfolgreich, davon 14 fuer den Story-Lauf. Im Gesamtprueflauf wurde die feste
  Migrationserwartung des Importtests von 23 auf 24 angepasst; dessen gezielter
  Wiederholungslauf ist ebenfalls erfolgreich.
- Nach dem gemeldeten Heap-Abbruch bei ca. 12.000 Snapshot-Artikeln: Score-Cache auf
  65.536 Eintraege begrenzt, Auswahl ohne vollstaendige Merge-Kandidatenliste,
  keine Merge-Diagnosehistorie im Publisher, Wiederverwendung der geladenen Vektoren
  und weniger Zwischenkopien der Paarentscheidungen. Der Publisher verarbeitet
  unabhaengige Gruppen des exakten Kandidatengraphen separat bei identischer Partition.
  Ein Regressionstest prueft 2.200 Artikel / 2.418.900 Paare mit 64 MB Heap.

### Pruefung der laufenden Datenbank, 2026-09-25, 20:29 MESZ

- V24 ist angewendet. Run 164 / Snapshot 163 der 24-h-SHADOW-Version wurde nach
  Wiederaufnahme erfolgreich publiziert: 11.536 Snapshot-Mitglieder und ebenso viele
  Entscheidungen, 11.506 aktuelle Mitgliedschaften in 6.855 Stories sowie 30 UNASSIGNED
  (29 TITLE_MISSING, 1 TITLE_GENERIC). Laufzeit: 101,49 Sekunden.
- Rein lesende Konsistenzpruefung: keine doppelten aktuellen Mitgliedschaften,
  keine Story-/Assignment-/Mitgliedschafts-Erzeugung ohne Publish-Commit, keine Commits
  fehlgeschlagener Runs, keine aktuellen Mitglieder supersedierter Stories.
- Aktuelle Entscheidungen stimmen mit Mitgliedschaften ueberein. Repraesentanten sind
  Mitglieder; Zeitspannen, gespeicherte Medoid-Scores und Zeitfenster sind konsistent.
  Alle drei Clustering-Versionen bleiben SHADOW. Die Scores wurden hier nicht neu berechnet.
- Der Live-Stand enthaelt bisher eine Publikation; No-op, Merge/Split, Schliessen,
  Wiedereroeffnung und Konflikt-Rollback sind durch die 14 erfolgreichen PostgreSQL-Tests
  belegt, nicht durch diesen ersten Live-Lauf. Fuer die Betriebsabnahme noch einen
  erfolgreichen Folgelauf pruefen. Die alten RUNNING-Claims der Versionen 2 und 3 haben
  noch keinen Publish-Commit; Lease-Enden: 20:28:50 und 20:39:05 MESZ.

### Abschluss, 2026-09-26

Auf ausdruecklichen Nutzerwunsch abgeschlossen und nach `done` verschoben.
Die technische Publikation ist durch Integrationstests und den erfolgreichen Live-Lauf
belegt. Die Wiederaufnahme der beiden anderen Shadow-Versionen bleibt ein Betriebscheck.
Die [inhaltliche Stichprobe](../../analysis/ART-038-story-quality-spotcheck.md) zeigt
Verbesserungsbedarf bei der Clustering-Qualitaet; dieser ist gesondert zu bearbeiten
und kein Bestandteil der atomaren Publikation dieses Tickets.
