# ART-026: GDELT-Payload-Modell abschliessend konsolidieren

Status: offen
Bereich: gdelt, architecture

## Kontext

ART-023 bis ART-025 stellen EVENTS, MENTIONS und GKG einzeln auf temporaere Payload- und
dauerhafte Fachtabellen um. Nach den drei vertikalen Migrationen muessen gemeinsame Abfragen,
End-to-End-Tests und Dokumentation abschliessend auf das neue Gesamtmodell vereinheitlicht werden.

## Ziel

Das neue GDELT-Modell ist ueber alle Datensatztypen konsistent, besitzt keine produktiven
Abhaengigkeiten von den ersetzten Raw-, Staging- und GKG-Record-Tabellen und ist als gemeinsamer
Pfad von Download bis Article-Extraktion getestet und dokumentiert.

## Zielmodell

```text
Temporaere Payloads:
- gdelt_event_payloads
- gdelt_mention_payloads
- gdelt_gkg_payloads

Dauerhafte Fachdaten:
- gdelt_events
- gdelt_mentions
- gdelt_gkg

Dauerhafte Fehlerhistorie:
- gdelt_processing_errors
```

Die Fachtabellen enthalten nur erfolgreich geparste und normalisierte Daten. Payload und
Fachzeile verwenden dieselbe stabile ID. Die Fachtabellen enthalten weder `raw_tsv` noch
`processing_status`.

## Umfang

```text
- gemeinsame Health-Metriken fuer ausstehende Payloads, offene Fehler und vorhandene Fachzeilen vereinheitlichen
- datensatzuebergreifende Debug-Views und Analyseskripte auf das neue Modell umstellen
- verbliebene Referenzen auf gdelt_raw_*, gdelt_stage_* und gdelt_gkg_records entfernen
- gemeinsamen PostgreSQL-End-to-End-Test fuer Download, Payload-Import, Parsing, Normalisierung und Extraktion ergaenzen
- Idempotenz bei Neuimport, Retry und Neustart datensatzuebergreifend pruefen
- Architektur-, Article- und Betriebsdokumentation abschliessend aktualisieren
- Voraussetzungen fuer die Payload-Retention aus ART-022 validieren
```

## Akzeptanzkriterien

```text
- Produktionscode und aktuelle Dokumentation referenzieren keine ersetzten Raw-, Staging- oder GKG-Record-Tabellen
- Health-Metriken unterscheiden ausstehende Payloads, offene Fehler und Fachzeilen konsistent
- der vollstaendige PostgreSQL-Pfad ist fuer EVENTS, MENTIONS und GKG abgedeckt
- wiederholter Import und wiederholte Verarbeitung erzeugen keine Duplikate
- bestehende Article API, Suche, Filter, Pagination, Sortierung und Statuscodes bleiben unveraendert
- Dokumentation beschreibt ausschliesslich Payload-, Fach- und Processing-Error-Tabellen als aktuelles Modell
- ART-022 kann ohne weitere Schemaaenderung auf allen drei Payload-Typen umgesetzt werden
```

## Abhaengigkeiten

Das Ticket baut auf ART-021, ART-023, ART-024 und ART-025 auf.

## Abgrenzung

Die automatische Payload-Loeschung selbst ist Bestandteil von ART-022. Neue Article-Funktionen,
weitere fachliche Normalisierung und eine Zusammenlegung von `gdelt_import_files` sind nicht Teil
dieses Tickets.
