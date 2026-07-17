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

## Umsetzungskommentar

Implementiert am 2026-07-17:

- Migration V19 fuehrt `gdelt_pipeline_health_view` ein. Die View weist fuer EVENTS, MENTIONS und
  GKG jeweils den gesamten Payload-Bestand, Payloads ohne Fachzeile, offene Processing-Fehler und
  vorhandene Fachzeilen nach derselben Definition aus.
- Ein gemeinsamer PostgreSQL-End-to-End-Test prueft HTTP-Download, Payload-Import, Parsing,
  GKG-Normalisierung und Article-Extraktion fuer alle drei Datensatztypen. Neuimport sowie erneute
  Verarbeitung nach simuliertem Neustart erzeugen keine doppelten Payloads, Fachzeilen, Artikel
  oder Signale.
- Architektur-, Article-, Analyse- und Betriebsdokumentation beschreiben das aktuelle Payload-,
  Fach- und Processing-Error-Modell. Die Betriebsdokumentation enthaelt die gemeinsame Health-
  Abfrage und dokumentiert den vollstaendigen PostgreSQL-Testpfad.
- ART-022 kann alle drei Payload-Tabellen anhand ihrer stabilen IDs und der vorhandenen Fachzeilen
  behandeln; die dauerhaften Fachzeilen und `gdelt_processing_errors` benoetigen dafuer keine
  weitere Schemaaenderung.
- Der bestehende Article-REST-Contract einschliesslich Suche, Filter, Pagination, Sortierung,
  Statuscodes und `/articles/extraction/health` blieb unveraendert. Daher war keine Anpassung der
  Postman-Collection erforderlich; ihre JSON-Struktur wurde validiert.
- Verifiziert mit `.\mvnw.cmd verify`: 54 Unit-/Kontexttests und 5 PostgreSQL-Integrationstests,
  jeweils ohne Fehler oder uebersprungene Tests.
