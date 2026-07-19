# ART-033: Story-Lebenszyklus und Verarbeitungsvertrag festlegen

Status: offen
Bereich: stories, architecture, operations

## Kontext

Artikel und ihre Signale treffen inkrementell und teilweise zeitversetzt ein. Ein Artikel kann
zunaechst nur ueber EVENTS oder MENTIONS bekannt sein und spaeter einen GKG-Titel, Entitaeten oder
eine genauere Publikationszeit erhalten. Ein Story-Clusterer muss deshalb mehr leisten als einen
einmaligen statischen Gruppenlauf.

Vor dem produktiven Datenmodell muss entschieden werden, wie Story-Identitaet, Mitgliedschaften,
Neubewertung und konkurrierende Job-Laeufe deterministisch behandelt werden.

## Ziel

Ein technologieunabhaengiger Verarbeitungsvertrag definiert den Lebenszyklus von Stories und die
idempotente Verarbeitung neuer oder nachtraeglich angereicherter Artikel.

## Umfang

```text
- effektiven Artikelzeitpunkt mit eindeutiger Fallback-Regel festlegen
- Eintritts- und Neubewertungsausloeser fuer Artikel definieren
- Kandidatenfenster und Behandlung verspaeteter Signale fachlich festlegen
- Zustaende einer Story und erlaubte Zustandsuebergaenge beschreiben
- Regeln fuer Erweitern, Schliessen und Wiedereroeffnen definieren
- Merge- und Split-Verhalten sowie Stabilitaet externer Story-IDs entscheiden
- Kardinalitaet und Historisierung von Artikelmitgliedschaften festlegen
- Idempotenzschluessel und Wiederanlaufverhalten beschreiben
- Parallelverarbeitung, Sperren oder optimistische Konflikterkennung konzeptionell klaeren
- Backfill, Reprocessing nach Regelwechseln und Versionierung des Clusterers festlegen
- benoetigte Health-, Audit- und Erklaerbarkeitsinformationen definieren
```

## Zu entscheidende Invarianten

```text
- identische Eingangsdaten und dieselbe Clustering-Version liefern dieselbe Zuordnung
- ein Retry erzeugt weder doppelte Stories noch doppelte Mitgliedschaften
- eine sichtbare Story-ID verschwindet bei Merge oder Split nicht ohne dokumentierte Regel
- die Ursache jeder automatischen Zuordnung bleibt nachvollziehbar
- spaet eintreffende Daten koennen verarbeitet werden, ohne abgeschlossene Zeitraeume unkontrolliert umzubauen
- Regel- oder Modellwechsel sind von normalen inkrementellen Laeufen unterscheidbar
```

## Akzeptanzkriterien

```text
- Story-Zustaende und alle erlaubten Uebergaenge sind dokumentiert
- effektiver Artikelzeitpunkt und Fallback-Reihenfolge sind eindeutig festgelegt
- Verhalten fuer neue Artikel, neue Signale, geaenderte Features und verspaetete Daten ist beschrieben
- Mitgliedschaft, Merge, Split, Schliessen und Wiedereroeffnen besitzen deterministische Regeln
- stabile Identitaet, Idempotenz, Retry und Parallelverarbeitung sind konzeptionell geklaert
- Backfill und vollstaendiges Reprocessing besitzen getrennte, sichere Ablaeufe
- mindestens zehn Ende-zu-Ende-Szenarien beschreiben Eingang, Ausgang und erwartete Invarianten
- offene technische Alternativen sind mit Entscheidungskriterien statt als ungebundene TODOs dokumentiert
- das Ergebnis reicht aus, um anschliessend Story-Schema, Cluster-Job und Tests zu entwerfen
- es erfolgen noch keine Aenderungen an produktivem Datenbankschema oder REST API
```

## Abhaengigkeiten

Das Ticket baut auf ART-030 auf und beruecksichtigt die Erkenntnisse aus ART-031 und ART-032. Es
ist die letzte fachlich-architektonische Voraussetzung vor dem produktiven Story-Datenmodell und
Cluster-Job.

## Abgrenzung

Flyway-Migrationen, produktiver Scheduler, Clustering-Implementierung, Story REST API und konkrete
Persistenztechnologie sind nicht Teil dieses Tickets.
