# ART-046: Story-Architektur-Dokumentation aktualisieren

Status: offen
Bereich: stories, operations, architecture

## Kontext

Mehrere Uebersichtsdokumente beschreiben noch den Stand vor ART-037 bis ART-040. Sie behaupten
unter anderem, Story-Partition, atomare Veroeffentlichung, Promotion und Story API seien noch nicht
implementiert. Der Code und die Betriebsdokumentation enthalten diese Funktionen inzwischen.

## Ziel

Projekt- und Datenbankuebersichten beschreiben den tatsaechlich implementierten Story-Datenfluss
von Titel-Inputs bis zur lesenden API konsistent und ohne veraltete Aussagen.

## Umfang

- `docs/global_event_analysis_platform_architecture.md` auf den aktuellen Story-MVP-Stand bringen
- `docs/database-overview.md` um Partition, Veroeffentlichung, Promotion, Public IDs und Story API
  aktualisieren
- Datenflussdiagramme und Modulverantwortungen an den implementierten Stand anpassen
- `SHADOW`, `ACTIVE` und `RETIRED` sowie Story-Zustaende eindeutig unterscheiden
- Querverweise auf Story-Vertrag, Betrieb, Datenmodell und REST API pruefen

## Akzeptanzkriterien

- kein Uebersichtsdokument bezeichnet Story-Partition, atomare Veroeffentlichung, Promotion oder
  Story API faelschlich als nicht implementiert
- der dokumentierte Datenfluss umfasst Inputs, Embeddings, Snapshots, Paarentscheidungen,
  Partition, Publish, Promotion und REST-Ausgabe
- die Moduluebersicht nennt die tatsaechlichen Verantwortlichkeiten der Story-Pakete
- die Datenbankuebersicht beschreibt Public IDs, aktive Version und aktuelle Mitgliedschaften
  korrekt
- Diagramme und Text unterscheiden Versionsstatus und Story-Zustand eindeutig
- relative Links auf weiterfuehrende Repository-Dokumente sind gueltig

## Abgrenzung

Produktionscode, Datenbankmigrationen, neue API-Endpunkte und eine allgemeine Neugestaltung aller
Dokumente sind nicht enthalten.

## Offene Fragen

Keine.
