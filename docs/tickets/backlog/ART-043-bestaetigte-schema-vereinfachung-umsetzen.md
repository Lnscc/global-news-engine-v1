# ART-043: Bestaetigte Datenbankvereinfachung umsetzen

Status: offen
Bereich: architecture
Prioritaet: zurueckgestellt

## Zurueckstellung

2026-09-25: Auf Nutzerwunsch zusammen mit ART-042 zurueckgestellt, bis das Projekt
fachlich und funktional steht. Das bestehende Schema bleibt vorerst bestehen.
Keine Tabellenzusammenlegung, Historienentfernung oder Indexbereinigung im Rahmen
der weiteren Story-Tickets. Wiederaufnahme nur nach ausdruecklicher Priorisierung.

## Kontext

Eine sichere Schemavereinfachung benoetigt den fachlichen Mindestumfang aus ART-041 und belegte
Vorschlaege aus ART-042. Konkrete Tabellen oder Felder fuer einen Umbau sind noch nicht ausgewaehlt.

## Ziel

Die kleinste bestaetigte Schemavereinfachung reduziert nachweislich Pflege- oder Abfrageaufwand
und erhaelt alle vereinbarten fachlichen Garantien sowie weiterhin benoetigte Daten.
Massgeblich sind weniger Tabellen und ein besserer Ueberblick im spaeter abgestimmten
Zielmodell. Die bisherigen Einzelvorschlaege sind noch nicht zur Umsetzung ausgewaehlt.

## Umfang

- vor Umsetzung den ausgewaehlten Vorschlag aus ART-042 und dessen messbaren Nutzen in diesem Ticket festhalten
- Schema und notwendige Produktionszugriffe konsistent anpassen
- vorhandene Daten entsprechend der vereinbarten Aufbewahrung erhalten beziehungsweise ueberfuehren
- Datenbankuebersicht, Datenmodell-Dokumentation und betroffene Betriebsabfragen aktualisieren

## Akzeptanzkriterien

- ART-041 und ART-042 liefern fuer die ausgewaehlte Aenderung eine eindeutige Entscheidungsgrundlage
- konkrete Aenderung, zu erhaltende Daten und Garantien sind vor Implementierungsbeginn im Ticket beschrieben
- ohne bestaetigten Vereinfachungsvorschlag wird kein Schemaumbau vorgenommen
- bereits angewendete Flyway-Migrationen bleiben unveraendert; Schemaaenderungen erfolgen ueber neue Migrationen
- Upgrade einer bestehenden Datenbank und Neuaufbau werden mit PostgreSQL verifiziert
- Tests belegen den Datenerhalt und die weiterhin benoetigten Integritaets-, Retry- und Idempotenzgarantien
- benoetigte historische Daten bleiben lesbar; destruktive Schritte setzen eine ausdrueckliche
  Entscheidung zu Aufbewahrung und Wiederherstellung voraus
- bestehende REST-Vertraege bleiben erhalten; eine ausdruecklich beschlossene Vertragsaenderung
  umfasst Postman-Collection, Vertragstests und JSON-Validierung
- Vorher/Nachher-Vergleich zeigt den vereinbarten Nutzen; weniger Tabellen allein gilt nicht als Erfolg

## Abgrenzung

Bis zur Auswahl eines Vorschlags ist dieses Ticket nicht implementierungsreif. Keine zusaetzlichen
Story-Funktionen, keine generelle Neumodellierung und kein pauschales Entfernen von Auditdaten.

## Offene Fragen

- Welcher Vorschlag aus ART-042 wird ausgewaehlt?
- Welche bestehenden Daten muessen migriert werden und welche Wiederherstellung ist erforderlich?
- Woran wird die konkrete Vereinfachung gemessen?
