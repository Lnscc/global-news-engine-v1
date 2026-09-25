# ART-038: Story-Ergebnisse atomar publizieren

Status: offen
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
