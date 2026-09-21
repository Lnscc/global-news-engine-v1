# ART-039: Story-Clustering-Version freigeben

Status: offen
Bereich: stories, operations, architecture

## Kontext

Story-Ergebnisse werden zunaechst in einer `SHADOW`-Clustering-Version erzeugt. Ohne einen
kontrollierten Freigabeprozess darf diese Version nicht zur produktsichtbaren Story-Wahrheit
werden.

## Ziel

Eine fachlich freigegebene und technisch vollstaendige Shadow-Version kann atomar zur aktuellen
`ACTIVE`-Version promoviert werden. Die vorherige Version und ihre Auditdaten bleiben lesbar.

## Umfang

- technische Vollstaendigkeit und Embedding-Dimension pruefen
- versionierte Evaluations-Gates gegen das Korpus aus ART-032 auswerten
- Diff fuer Storyanzahl, Singleton-Anteil, Mitgliedschaften, Merge und Split bereitstellen
- dokumentierte fachliche Freigabe verlangen
- Public IDs nach den Membership-Overlap- und Tie-Break-Regeln des Vertrags zuordnen
- den sichtbaren Versionswechsel atomar und historisiert ausfuehren
- fehlgeschlagene oder wiederholte Promotion ohne Teilzustand behandeln

## Akzeptanzkriterien

- ohne erfolgreiche technische Pruefung, Evaluations-Gates und dokumentierte Freigabe findet
  keine Promotion statt
- eine erfolgreiche Promotion macht genau eine Clustering-Version zur aktuellen `ACTIVE`-Version
- der Wechsel ist fuer Leser atomar sichtbar
- Public IDs werden bei gleicher Eingabe deterministisch zugeordnet
- die vorherige Version wird nicht geloescht oder nachtraeglich veraendert
- Statuswechsel und Freigabegrund sind auditierbar
- die Wiederholung einer erfolgreichen Promotion ist ein No-op
- PostgreSQL-Integrationstests decken erfolgreiche, abgelehnte, wiederholte und konkurrierende
  Promotion ab

## Abgrenzung

Dieses Ticket aendert weder Clustering-Regeln noch Story-Inhalte und stellt keine REST API oder UI
bereit.

## Offene Fragen

Keine.
