# ART-038: Story-Ergebnisse atomar publizieren

Status: offen
Bereich: stories, operations

## Kontext

Das Story-Datenmodell enthaelt Stories, Zuordnungsentscheidungen, historisierte Mitgliedschaften,
Zustandswechsel, Lineage und Publish-Commits. Nach ART-037 liegt eine deterministische Partition
vor, wird aber noch nicht in dieses Lebenszyklusmodell ueberfuehrt.

## Ziel

Ein Publisher ueberfuehrt ein Clusterergebnis atomar und idempotent in Stories und
Mitgliedschaften der zugehoerigen Clustering-Version. Erweiterungen, Wiedereroeffnungen, Merge,
Split und `UNASSIGNED` bleiben nachvollziehbar und wiederholbar.

## Umfang

- Komponenten gegen den zuletzt publizierten Zustand derselben Clustering-Version abgleichen
- stabile Story-IDs, Identitaetsanker und repraesentative Artikel bestimmen
- Assignment-Entscheidungen und Mitgliedschaftsintervalle historisieren
- Story-Zustaende und erlaubte Uebergaenge anwenden
- Merge- und Split-Lineage nach dem Story-Verarbeitungsvertrag erzeugen
- Publish mit Lease, Fencing-Token, optimistischer Versionierung und Publish-Key absichern
- Story-Ableitungen, Mitgliedschaften, Zustandswechsel, Lineage und Commit atomar schreiben
- Run-, Konflikt- und Ergebnismetriken bereitstellen

## Akzeptanzkriterien

- ein erfolgreicher Lauf erzeugt fuer jede Komponente genau eine aktuelle Story-Zuordnung
- ein Artikel besitzt je Clustering-Version hoechstens eine aktuelle Mitgliedschaft
- unveraenderte Zuordnungen sind ein No-op und erzeugen keine neue Mitgliedschaft
- Erweiterung, Schliessen und Wiedereroeffnen erhalten die Story-ID
- bei einem Merge ueberlebt deterministisch die im Vertrag festgelegte Story-ID
- bei einem Split behaelt die Ankerkomponente ihre Story-ID; alle Nachfolger sind aufloesbar
- unbrauchbare oder nicht entscheidbare Artikel erhalten eine begruendete `UNASSIGNED`-Entscheidung
- ein Retry desselben Snapshots erzeugt weder neue IDs noch doppelte Historien- oder Lineage-Daten
- ein Publisher mit abgelaufenem Fencing-Token kann keinen Teilzustand veroeffentlichen
- PostgreSQL-Integrationstests decken Neuaufnahme, No-op, Erweiterung, Merge, Split, Retry und
  konkurrierende Publisher ab
- der Lauf bleibt fuer eine `SHADOW`-Version nicht produktsichtbar

## Abgrenzung

Die Promotion einer Clustering-Version, eine REST API und eine UI sind nicht enthalten.

## Offene Fragen

Keine.
