# ART-041: Story-MVP-Garantien auf notwendigen Umfang begrenzen

Status: offen
Bereich: stories, architecture

## Kontext

Der Story-Verarbeitungsvertrag fordert versionierte Ergebnisse, reproduzierbare Snapshots,
historisierte Mitgliedschaften, Merge-/Split-Lineage und kontrollierte Veroeffentlichung.
Diese Anforderungen begruenden einen erheblichen Teil der Datenbankkomplexitaet. Welche davon
bereits fuer den ersten produktiven Nutzen erforderlich sind, soll vor einer Schemaaenderung
explizit entschieden werden. ART-037 bis ART-040 setzen den bisherigen Vertrag voraus.

## Ziel

Ein abgestimmter minimaler Story-MVP-Vertrag trennt notwendige Garantien von bewusst vertagten
Funktionen und schafft eine belastbare Grundlage fuer die Vereinfachung des Schemas.

## Umfang

- Bedarf an parallelen Clustering-Versionen und vollstaendigem Reprocessing klaeren
- Umfang und Aufbewahrung von Snapshots, Entscheidungen und Mitgliedschaftshistorie klaeren
- Anforderungen an stabile Story-IDs, Merge, Split und Nachfolger-Aufloesung klaeren
- notwendige Garantien fuer atomare, idempotente Veroeffentlichung und Parallelbetrieb festhalten
- beschlossene Aenderungen mit Story-Vertrag, Datenmodell-Dokumentation und ART-037 bis ART-040 abgleichen

## Akzeptanzkriterien

- jede gepruefte Garantie ist als erforderlich oder vertagt eingestuft und fachlich begruendet
- Auswirkungen vertagter Garantien auf sichtbare Ergebnisse und spaetere Nachvollziehbarkeit sind benannt
- offene Produktentscheidungen sind ausdruecklich markiert; sie gelten nicht stillschweigend als Freigabe
- der vereinbarte Umfang ist im Story-Verarbeitungsvertrag dokumentiert
- bestehende Umsetzungstickets widersprechen den beschlossenen Anforderungen nicht mehr
- eine Entscheidung, den bisherigen Umfang beizubehalten, ist als gueltiges Ergebnis moeglich

## Abgrenzung

Keine Schema-, Produktionscode- oder Datenaenderung. Keine Reduktion von Garantien allein zur
Verringerung der Tabellenzahl. Bestehende Ticketstatus bleiben unveraendert.

## Offene Fragen

- Welche historischen Ergebnisse muessen Nutzer spaeter noch erklaeren oder reproduzieren koennen?
- Muessen stabile Story-IDs schon im ersten Release Merge und Split ueberstehen?
- Werden mehrere Clustering-Versionen und parallele Publisher im MVP tatsaechlich benoetigt?
