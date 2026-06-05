# Project Goals

Stand: 2026-06-05

## Zielbild

Die Anwendung soll GDELT-Daten dauerhaft importieren, roh speichern und daraus spaeter nutzbare Nachrichten-, Story- und Analyseobjekte ableiten.

GDELT ist dabei die Eingangsschicht. Das spaetere Produktmodell soll nicht nur einzelne Events anzeigen, sondern Artikel, Stories, Topics und langfristige Themes sichtbar machen.

## Aktueller Fokus

Raw-Ingestion fuer GDELT 2.0:

- Events
- Mentions
- GKG

Die heruntergeladenen ZIP-Dateien werden nicht archiviert. Sie werden gestreamt, entpackt, in PostgreSQL gespeichert und danach verworfen.

## Erreicht

- PostgreSQL per `compose.yaml`
- Spring-Boot-Anwendung startbar
- Maven Wrapper unter Windows repariert
- IntelliJ Run/Debug Configuration unter `.run`
- Flyway-Migration fuer Raw-Tabellen
- Raw-Tabellen fuer Events, Mentions und GKG
- Importprotokoll ueber `gdelt_import_files`
- Idempotenz fuer bereits importierte Dateien
- Discovery fuer vollstaendige GDELT-Zeitfenster
- Automatischer Polling-Import vollstaendiger Zeitfenster
- Retry fuer fehlgeschlagene Zeitfenster
- Operations-Doku mit SQL-Abfragen fuer Importstatus
- Polling-Log mit Zeitfenster, Datei- und Zeilenzaehlern
- Tests fuer Kontextstart, Flyway-Migration und Raw-Importer
- PostgreSQL-Integrationstest gegen lokale Compose-Datenbank

## Naechster Meilenstein

Raw-Daten in eine erste Staging-Schicht ueberfuehren.

Definition of Done:

- Staging-Tabellen fuer Events, Mentions und GKG sind per Flyway angelegt.
- Ein Transformationsjob liest nur erfolgreich importierte Raw-Dateien.
- Der Job ist idempotent und verarbeitet Raw-Zeilen nicht doppelt.
- Fehlerhafte Raw-Zeilen werden mit Fehlermeldung separat protokolliert.
- Der erste Schnitt extrahiert nur Kernfelder, die wir fuer Artikel, Ereignisse und Quellen brauchen.
- Tests decken Parsing, Idempotenz und Fehlerfaelle ab.

## Danach

1. Rueckstand zum neuesten vollstaendigen GDELT-Zeitfenster sichtbar machen.
2. Kleine Statusausgabe fuer den Betrieb bauen.
3. Import-Performance messen.
4. Bei Bedarf JDBC-Batches durch PostgreSQL `COPY` ersetzen.
5. Artikel- und Story-Modell entwerfen.
6. Erste Auswertungen fuer Quellen, Laender, Themen und Zeitverlauf bauen.

## Offene Entscheidungen

- Wie lange behalten wir Raw-Daten?
- Brauchen wir Partitionierung nach Datum?
- Welche Felder werden zuerst aus Events, Mentions und GKG normalisiert?
- Soll der Import Rueckstaende automatisch tageweise aufholen?
- Soll die Staging-Transformation automatisch nach jedem Raw-Import laufen oder separat gestartet werden?
