# ART-042: Vereinfachungspotenzial des Datenbankschemas belegen

Status: offen
Bereich: architecture, stories, gdelt, articles

## Kontext

Viele Tabellen und Beziehungen erschweren den Ueberblick. Daraus folgt noch nicht, dass ihre
Trennung unnoetig ist. Insbesondere fuer Story-Vorbereitung, Veroeffentlichung und Historie fehlt
eine aktuelle, belegte Bewertung, welche Strukturen benoetigt, redundant oder nur vorbereitet sind.
ART-041 klaert den fachlichen Mindestumfang.

## Ziel

Eine priorisierte, nachvollziehbare Bewertung benennt konkrete Vereinfachungen mit ihrem Nutzen,
ihren Kosten und den zu erhaltenden Garantien. Reine Darstellungsprobleme werden als solche erkannt.

## Umfang

- aktuelles Schema gegen produktive Schreib- und Lesepfade sowie bestehende Tickets abgleichen
- Tabellen, redundante Felder, Beziehungen, Constraints und Historien auf begruendeten Bedarf pruefen
- vorhandene Daten und deren Nutzung einbeziehen, soweit eine Datenbank verfuegbar ist
- Story-Verarbeitung und vorbereitete Veroeffentlichung vertieft bewerten
- bei GDELT und Artikeln Rohdaten-Retention, dauerhafte Fachzeilen und Deduplikation beruecksichtigen
- vorhandene Views auf ihre Eignung fuer verstaendliche Datenabfragen pruefen

## Akzeptanzkriterien

- jede Tabelle ist als produktiv verwendet, fuer eine konkrete Anforderung vorbereitet oder ohne
  nachgewiesenen Bedarf eingeordnet; fehlende Belege sind als unbekannt gekennzeichnet
- jeder Vereinfachungsvorschlag nennt Quellen, betroffene Anforderungen und einen konkreten Nutzen
- leere Tabellen gelten nicht automatisch als ungenutzt; Referenzen aus ART-037 bis ART-040 sind beruecksichtigt
- Datenverlust-, Integritaets- und Kompatibilitaetsrisiken sind je Vorschlag benannt
- Vorschlaege entsprechen dem Ergebnis von ART-041; offene Entscheidungen blockieren nur die davon
  abhaengigen Empfehlungen
- der Bericht unterscheidet Schemaaenderungen von Verbesserungen durch Dokumentation oder Views
- falls kein sinnvoller Umbau nachweisbar ist, haelt der Bericht dies ausdruecklich fest

## Abgrenzung

Analyse und Empfehlung, keine Migration, Datenbereinigung oder Produktionscodeaenderung.
Keine pauschale Zusammenlegung von Tabellen und keine Zielvorgabe fuer deren Anzahl.

## Offene Fragen

- Welche konkrete Abfrage oder Wartungsaufgabe verursacht heute den groessten Aufwand?
- Steht eine repraesentative Datenbank fuer eine lesende Nutzungspruefung zur Verfuegung?
