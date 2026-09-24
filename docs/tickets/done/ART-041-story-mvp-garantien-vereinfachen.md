# ART-041: Story-MVP-Garantien auf notwendigen Umfang begrenzen

Status: erledigt
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

Keine. Die Produktentscheidungen sind in `../../analysis/ART-041-story-mvp-scope.md`
festgehalten. Technische Aufbewahrung und Schemafolgen werden in ART-042/ART-043 geprueft.

## Umsetzungskommentar

2026-09-24: Bestehenden Story-Vertrag, fachliche Definition, Datenmodell-Dokumentation und
ART-037 bis ART-040 abgeglichen. Der Entscheidungsentwurf
`../../analysis/ART-041-story-mvp-scope.md` stuft die geprueften Garantien als erforderlich oder
vertagt vor und benennt sichtbare Folgen sowie den Anpassungsbedarf der Folgetickets.
Die fachliche Bestaetigung der ID-, Historien- und Betriebszusagen steht noch aus.
Verbindlicher Vertrag, Produktionscode, Schema und Ticketstatus bleiben bis dahin unveraendert.

2026-09-24, Abstimmung: Stories werden bei neuen Artikeln mit bestehender ID aktualisiert.
Beim Merge bleibt eine Story bestehen; beim Split bleibt eine bestehen und der abgetrennte
Teil erhaelt eine neue ID. Der Nutzer benoetigt keine spaetere Nachvollziehbarkeit von
Zusammenfuehrungen und Aufteilungen. Merge-/Split-Historie und Nachfolgerverweise sind
damit fuer den MVP vertagt. Der Entwurf wurde entsprechend korrigiert. Andere Historien,
Versionswechsel und Betriebsumfang sind noch offen; der Vertragsabgleich folgt nach der
Abstimmung. Keine Datenloeschung oder Schemaaenderung beschlossen.

2026-09-24, weitere Abstimmung: Der Nutzer benoetigt nur den aktuellen Stand der
Artikelzuordnungen mit Begruendung. Fruehere Zuordnungen muessen nicht nachgeschlagen
oder rekonstruiert werden koennen. Eine dauerhafte Mitgliedschafts- und Zuordnungshistorie
ist daher im MVP vertagt. Der Entscheidungsentwurf ist aktualisiert; Versionswechsel
und Betriebsumfang bleiben offen. Technische Retention und vorhandene Daten bleiben
von dieser Produktentscheidung unberuehrt.

2026-09-24, Betriebsumfang: Der Nutzer entscheidet sich fuer ein produktives
Gruppierungsverfahren. Dauerhafte parallele Vergleichsvarianten sind im MVP vertagt.
Verbesserungen werden vor dem Einsatz getestet und ersetzen danach das bisherige
Verfahren. Der Entwurf wurde aktualisiert. Story-Identitaet bei Verfahrenswechsel ist
noch offen; Workerzahl und Rollback folgen nicht automatisch aus dieser Entscheidung.

2026-09-24, Abschluss des Vertragsabgleichs: Der Nutzer waehlt auch beim Wechsel des
Gruppierungsverfahrens die Weiterverwendung und Aktualisierung bestehender Stories.
Story-Vertrag, fachliche Definition, Datenmodell-Dokumentation und ART-037 bis ART-040
sind auf die bestaetigten Entscheidungen abgestimmt: aktuelle Zuordnungen mit Begruendung,
keine erforderliche Merge-/Split- oder Zuordnungshistorie, ein produktives Verfahren und
ID-Erhalt nach einheitlichen Regeln. Nicht bestaetigte Reduktionen von Betriebszustaenden,
inkrementeller Verarbeitung und technischem Rollback wurden nicht uebernommen.
Die Entscheidungsmatrix benennt Folgen, erhaltene Garantien und technische Folgearbeit.
Nur Dokumentation wurde geaendert; Schema, Daten und Ticketstatus bleiben unveraendert.

2026-09-24: Auf ausdruecklichen Nutzerwunsch abgeschlossen und nach done verschoben.
