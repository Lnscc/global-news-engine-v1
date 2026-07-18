# ART-028: Artikelbeschreibung untersuchen und bereitstellen

Status: offen
Bereich: articles, gdelt

## Kontext

Die Article API liefert derzeit URL, Domain, Titel, Publikationszeit, Hauptbild und GDELT-Signale.
Eine kurze Beschreibung beziehungsweise ein Teaser fehlt. Dadurch muss ein Client fuer Listen,
Vorschauen oder Suchergebnisse entweder ganz auf erklaerenden Text verzichten oder selbst eine
nicht nachvollziehbare Beschreibung erzeugen.

Die bereits importierten GKG-Daten enthalten mit `V2EXTRASXML` eine optionale Erweiterungsstruktur.
Fuer Titel, Publikationszeit und weitere Metadaten wurde sie bereits teilweise ausgewertet; eine
belastbare Quelle fuer eine Artikelbeschreibung ist im aktuellen Modell jedoch noch nicht
nachgewiesen. Ein externer Seitenabruf ist nicht aktiv und darf durch dieses Ticket nicht
stillschweigend wieder eingefuehrt werden.

## Begriffsabgrenzung

`description` bezeichnet einen kurzen, von der Quelle gelieferten Beschreibungstext, zum Beispiel
einen redaktionellen Teaser oder eine Meta-Description. Das Feld ist keine Zusammenfassung des
vollstaendigen Artikels und wird nicht aus Titel, Themes oder anderen Signalen zusammengesetzt.

## Ziel

An realen Daten wird untersucht, ob eine ausreichend abgedeckte und fachlich belastbare
Beschreibung ohne externen HTTP-Abruf aus den vorhandenen GDELT-Payloads gewonnen werden kann. Bei
positivem Ergebnis wird sie normalisiert am verursachenden GKG-Fachrecord persistiert und
deterministisch in `ArticleSummary`, `ArticleDetail` und der REST API projiziert.

## Umfang

```text
- V2EXTRASXML und weitere vorhandene GKG-Felder auf Beschreibungskandidaten untersuchen
- Abdeckung, Laengenverteilung, HTML-Inhalte, Duplikate und erkennbare Fehlwerte messen
- eine reproduzierbare Stichprobe ueber mehrere Domains und Sprachen dokumentieren
- fachliche Semantik der Kandidaten klaeren: Beschreibung, Teaser oder sonstiger Seitentext
- Normalisierung fuer HTML-Entities, Whitespace, Leerwerte und maximale Laenge definieren
- Beschreibung und Quelle als konsistentes nullable Wertepaar am GKG-Fachrecord modellieren
- historische, noch vorhandene Payloads kontrolliert und idempotent backfillen
- Konfliktregel fuer mehrere GKG-Records desselben Artikels definieren
- description und descriptionSource in ArticleSummary und ArticleDetail ausgeben
- Parser-, Migrations-, Query-, Controller- und PostgreSQL-Integrationstests ergaenzen
- Article-Dokumentation und Quellenmatrix aktualisieren
- Postman-Collection und Postman-Tests fuer nullable und vorhandene Beschreibungen aktualisieren
- Postman-Collection als valides JSON pruefen
```

## Fachliche Regeln

```text
- description bleibt nullable, wenn keine belastbare Quelle vorhanden ist
- descriptionSource ist genau dann gesetzt, wenn description gesetzt ist
- Beschreibung und Quelle bleiben am verursachenden GKG-Record; articles erhaelt keine zweite Wahrheit
- die Article-Projektion waehlt deterministisch den fruehesten geeigneten GKG-Record nach
  source_timestamp und id
- leere, rein technische, offensichtlich defekte oder ueberlange Kandidaten werden nicht uebernommen
- optionale fehlerhafte Beschreibungstags blockieren weder GKG-Parsing noch andere Metadaten
- ein spaeter eintreffender Konflikt ueberschreibt die gewaehlte Beschreibung nicht stillschweigend
- Sprache, Autoren, Volltext und generierte Zusammenfassungen sind eigenstaendige Felder
```

## API-Zielbild

Vorhandene Beschreibung:

```json
{
  "description": "A concise publisher-provided description of the article.",
  "descriptionSource": "GKG_PAGE_DESCRIPTION"
}
```

Keine belastbare Beschreibung:

```json
{
  "description": null,
  "descriptionSource": null
}
```

Der konkrete Quellenbezeichner wird erst nach der Quellenanalyse festgelegt und muss die
tatsaechlich verwendete GDELT-Struktur benennen.

## Akzeptanzkriterien

```text
- die Analyse dokumentiert untersuchte Felder, Stichprobe, Abdeckung und Qualitaetsprobleme
- die Entscheidung fuer oder gegen eine Persistenz ist anhand vorab dokumentierter Mindestkriterien
  nachvollziehbar
- bei positiver Entscheidung werden HTML-Entities dekodiert und Leerwerte deterministisch behandelt
- Beschreibung und Quelle sind nullable, konsistent und idempotent
- mehrere GKG-Records liefern bei unveraenderten Daten immer dieselbe Article-Projektion
- historische Payloads werden ohne doppelte oder widerspruechliche Fachwerte nachgezogen
- ArticleSummary und ArticleDetail enthalten description und descriptionSource
- Artikel ohne Beschreibung bleiben unveraendert abrufbar
- bestehende Suche, Filter, Pagination, Sortierung und Statuscodes bleiben unveraendert
- bei negativer Entscheidung werden Datenbedarf, Messergebnis und ein klar abgegrenzter Folgeschritt
  dokumentiert
- Postman-Collection und Postman-Tests decken vorhandene und fehlende Beschreibungen ab
- Postman-Collection ist valides JSON
```

## Abhaengigkeiten

Das Ticket baut auf der GKG-Metadatenextraktion aus ART-011, dem GKG-Fachmodell aus ART-025 und der
einheitlichen Artikelzuordnung aus ART-027 auf. Es kann unabhaengig von ART-015 und ART-016
analysiert und umgesetzt werden.

## Abgrenzung

LLM-Zusammenfassungen, Volltextextraktion, Story-Zusammenfassungen, Autorenmodellierung und
Spracherkennung sind nicht Teil dieses Tickets. Falls keine ausreichend belastbare Beschreibung in
den vorhandenen GDELT-Daten existiert, wird ART-010 nicht automatisch aktiviert; ein externer
Crawler erfordert weiterhin einen eigenen Nachweis und die dort dokumentierten Sicherheits- und
Betriebsvoraussetzungen.
