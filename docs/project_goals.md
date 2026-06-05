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
- Tests fuer Kontextstart, Flyway-Migration und Raw-Importer

## Naechster Meilenstein

Importstatus und Betrieb sollen einfach nachvollziehbar werden.

Definition of Done:

- Der letzte erfolgreiche Import ist sichtbar.
- Fehlgeschlagene Dateien sind schnell auffindbar.
- Der Rueckstand zum neuesten vollstaendigen GDELT-Zeitfenster ist erkennbar.
- Es gibt einfache SQL-Abfragen oder eine kleine Statusausgabe fuer den Betrieb.

## Danach

1. PostgreSQL-Integrationstest ergaenzen.
2. Import-Performance messen.
3. Bei Bedarf JDBC-Batches durch PostgreSQL `COPY` ersetzen.
4. Raw-Zeilen in strukturierte Staging-Tabellen ueberfuehren.
5. Artikel- und Story-Modell entwerfen.
6. Erste Auswertungen fuer Quellen, Laender, Themen und Zeitverlauf bauen.

## Offene Entscheidungen

- Wie lange behalten wir Raw-Daten?
- Brauchen wir Partitionierung nach Datum?
- Wann wechseln wir von Batch-Import zu kontinuierlichem Polling?
- Welche Felder werden zuerst aus Events, Mentions und GKG normalisiert?
- Soll der Import Rueckstaende automatisch tageweise aufholen?
