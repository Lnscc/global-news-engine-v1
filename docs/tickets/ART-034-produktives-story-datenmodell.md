# ART-034: Produktives Story-Datenmodell implementieren

Status: offen
Bereich: stories, architecture, operations

## Kontext

ART-030 definiert den fachlichen Story-Begriff. ART-031 und ART-032 belegen Datenqualitaet,
Embedding-Modell und die erste konservative Entscheidungsregel an realen Daten. ART-033 legt mit
`docs/story-processing-contract.md` den deterministischen Lebenszyklus fuer Clustering-Versionen,
Embeddings, Snapshots, Stories, Mitgliedschaften, Merge, Split, Retry und Reprocessing fest.

Der Anwendung fehlt noch ein produktives Persistenzmodell, auf dem der spaetere Embedding- und
Cluster-Job diese Invarianten sicher und nachvollziehbar umsetzen kann. Insbesondere existieren
noch keine dauerhaften Clustering-Versionen, unveraenderlichen Embedding-Artefakte, historisierten
Story-Mitgliedschaften oder Auditdaten fuer Runs und Entscheidungen.

## Ziel

Ein versioniertes PostgreSQL-Datenmodell bildet den Verarbeitungsvertrag aus ART-033 ohne
Informationsverlust ab. Flyway-Migrationen, Constraints, Indizes und Persistenztests schaffen die
Grundlage fuer den anschliessenden Embedding- und Cluster-Job, aktivieren aber noch keine
produktive Verarbeitung.

Das Modell muss mindestens folgende fachliche Aggregate dauerhaft abbilden koennen:

```text
- unveraenderliche Clustering-Versionen samt Betriebsstatus
- unveraenderliche, zwischen Artikeln wiederverwendbare Titel-Embedding-Artefakte
- versionierte Artikel-Inputs und ihr aktueller Embedding-/Verarbeitungszustand
- eingefrorene Snapshots und idempotente Verarbeitungslaeufe
- stabile Story-Identitaeten, Zustand, Identitaetsanker und repraesentativer Artikel
- historisierte, je Version eindeutige Artikelmitgliedschaften
- Story-Lineage fuer Merge und Split
- Pair-, Zuordnungs-, Zustands- und Publish-Entscheidungen samt Erklaerungsdaten
- UNASSIGNED-Entscheidungen und Embedding-Fehlerhistorie
```

## Umfang

```text
- Tabellen, Schluessel und Beziehungen aus dem Vertrag von ART-033 konkret entwerfen
- stabile Artikelreferenz auf Basis des kanonischen URL-Hashs mit der lokalen articles-Zeile
  verknuepfen
- Clustering-Version einschliesslich Modell-, Dimensions-, Normalisierungs-, Zeitfenster-,
  Similarity-, Pair- und Komponentenregel unveraenderlich speichern
- Betriebsstatus SHADOW, ACTIVE und RETIRED sowie Statuswechselhistorie modellieren
- Embedding-Artefakte ueber den vollstaendigen fachlichen Artefaktschluessel deduplizieren
- Vektor, kanonischen Vektor-Hash, Dimension, Norm, Anbieter-Request-ID und Erzeugungsdaten
  speichern
- konkrete PostgreSQL-Vektorrepräsentation entscheiden und dokumentieren; exakte Suche und
  reproduzierbare Float32-/Float64-Verarbeitung muessen moeglich bleiben
- Artikel-Input-Fingerprint, effectiveAt samt Zeitquelle, titleInputHash,
  Verwendbarkeitsgrund und Embedding-Zustand versioniert speichern
- Embedding-Zustaende NOT_REQUIRED, PENDING, READY, RETRYABLE_FAILURE und TERMINAL_FAILURE
  einschliesslich Versuch, naechstem Retry und Fehlerhistorie abbilden
- Snapshot, Snapshot-Watermark, snapshotInputHash und die eingefrorenen Artikel- sowie
  Embedding-Referenzen speichern
- Runs mit Modus, Status, Fencing-Token, Retry-Bezug, Zeitstempeln und Health-Zaehlern modellieren
- stabile Story-ID, Clustering-Version, Identitaetsanker, Medoid, Zustand und optimistische
  Datensatzversion speichern
- Mitgliedschaften mit validFromRunId und nullable validToRunId historisieren
- hoechstens eine aktuelle Mitgliedschaft je Artikel und Clustering-Version durch einen
  PostgreSQL-Constraint beziehungsweise partiellen Unique Index erzwingen
- Story-Lineage fuer Merge und Split mit Grund, verursachendem Run und einem oder mehreren
  Nachfolgern speichern
- Pair- und Mitgliedschaftsentscheidungen mit Eingabe-Fingerprints, Regelversion,
  quantisierter Cosine Similarity, Zeitabstand, Ergebnis und ausgeloester Regel auditierbar machen
- beste abgelehnte Top-1-Diagnose sowie optionale, explizit als non_decisive markierte Evidenz
  speichern koennen
- fachliche Idempotenzschluessel fuer Embedding, Bewertung, Snapshot, Entscheidung und Publish
  durch geeignete Unique Constraints absichern
- Fremdschluessel-, Check-, Eindeutigkeits- und Zustandsconstraints fuer alle Invarianten
  definieren
- Indizes fuer aktuelle Mitgliedschaften, Version, Artikel, Story, effectiveAt, Run-Status,
  Retry-Faelligkeit und Lineage anlegen
- Loesch- und Update-Regeln so waehlen, dass veroeffentlichte Story-IDs, Embeddings,
  Mitgliedschafts- und Entscheidungshistorie nicht still ueberschrieben oder kaskadierend
  entfernt werden
- Flyway-Migration und PostgreSQL-Integrationstests fuer Schema, Constraints, Idempotenz und
  zentrale Query-Pfade ergaenzen
- Datenmodell und getroffene Persistenzentscheidungen in der Story-Dokumentation festhalten
```

## Modellregeln

```text
- eine Clustering-Version ist nach ihrer ersten Verwendung fachlich unveraenderlich
- die erste Version und ihre Challenger starten ausschliesslich als SHADOW
- ein Embedding-Artefakt ist durch Modell, Modellversion, Dimension, Titel-Normalisierung und
  titleInputHash eindeutig und wird nach READY nicht ueberschrieben
- derselbe Artikel-Input-Fingerprint wird je Clustering-Version hoechstens einmal wirksam
  entschieden
- ein Artikel besitzt je Clustering-Version hoechstens eine aktuelle Mitgliedschaft
- historische Mitgliedschaften und Entscheidungen werden beendet, nicht ersetzt oder geloescht
- eine Story-ID bleibt dauerhaft aufloesbar; SUPERSEDED-Stories verweisen auf ihre Nachfolger
- Mitgliedschaften, Story-Ableitungen, Zustandswechsel und Lineage eines Publish-Schritts sind
  atomar persistierbar
- ein bereits erfolgreicher Publish-Schluessel kann bei Retry kein zweites Ergebnis erzeugen
- Vektoren anderer Dimension, nicht endliche Werte und Nullnormen werden nicht als READY
  akzeptiert
- Daten verschiedener Clustering-Versionen bleiben strikt unterscheidbar und parallel lesbar
```

## Zu klaerende Persistenzentscheidungen

Die Implementierung muss die folgenden technischen Entscheidungen anhand der Vertragsinvarianten
und des erwarteten MVP-Datenvolumens dokumentieren:

```text
- PostgreSQL-Repräsentation des 1.536-dimensionalen Float32-Vektors
- normalisierte Spalten gegen strukturierte JSON-Diagnoseevidenz
- Materialisierung der Snapshot-Mitglieder gegen unveraenderlich referenzierte Eingabemengen
- Aufteilung von Pair-, Mitgliedschafts-, Zustands- und Publish-Auditdaten
- Datenbankseitige Durchsetzung der fachlichen Unveraenderlichkeit
- Aufbewahrung und kontrollierte Bereinigung nicht mehr aktueller Shadow-Artefakte
```

Eine approximative Vektorsuche oder ein produktiver Vektorindex wird durch dieses Ticket nicht
vorweggenommen. Die gespeicherte Darstellung darf die in ART-033 geforderte exakte und
reproduzierbare Cosine-Berechnung nicht verhindern.

## Akzeptanzkriterien

```text
- das Schema bildet alle fuer den spaeteren Cluster-Job benoetigten Invarianten aus ART-033 ab
- die Beziehungen zwischen Clustering-Version, Artikel-Input, Embedding, Snapshot, Run, Story,
  Mitgliedschaft, Lineage und Entscheidung sind dokumentiert und durch Fremdschluessel gesichert
- mehrere Artikel koennen dasselbe unveraenderliche Embedding-Artefakt verwenden
- ein doppelter Embedding-, Snapshot-, Bewertungs- oder Publish-Idempotenzschluessel wird
  datenbankseitig verhindert
- mehr als eine aktuelle Mitgliedschaft desselben Artikels in derselben Clustering-Version wird
  abgelehnt; historische, nicht ueberlappende Mitgliedschaften bleiben moeglich
- Merge und Split koennen alte Story-IDs mit allen Nachfolgern dauerhaft aufloesbar halten
- ACTIVE, CLOSED und SUPERSEDED sowie SHADOW, ACTIVE und RETIRED sind eindeutig typisiert und
  ungueltige Werte werden abgelehnt
- Embedding-Fehler, UNASSIGNED-Gruende und spaetere erfolgreiche Neubewertung sind ohne Verlust
  der Historie darstellbar
- ein eingefrorener Snapshot referenziert exakt die verwendeten Artikel-Inputs und
  Embedding-Artefakte
- konkurrierende oder wiederholte Publish-Versuche koennen ueber Fencing, optimistische Version
  und Unique Constraints sicher entschieden werden
- Migrationstests laufen gegen PostgreSQL und pruefen mindestens alle zentralen Unique-, Check-,
  Fremdschluessel- und Historisierungsregeln
- bestehende Artikel-, GDELT- und Health-Daten bleiben durch die Migration unveraendert
- es werden weder Embedding-Aufrufe noch Story-Clustering oder produktive Statusaktivierungen
  ausgefuehrt
- die Story-Dokumentation beschreibt Tabellen, Kardinalitaeten, Indizes und die getroffenen
  Persistenzentscheidungen
```

## Abhaengigkeiten und Reihenfolge

Das Ticket baut auf ART-030, ART-031, ART-032 und insbesondere dem Verarbeitungsvertrag aus
ART-033 auf. Es ist die Persistenzgrundlage fuer den anschliessenden Embedding- und Cluster-Job.

Nach diesem Ticket sollten die technische Reihenfolge und Ticketgrenzen mindestens fuer folgende
Arbeiten festgelegt werden:

```text
1. Titel-Normalisierung, Artikel-Input-Fingerprint und Embedding-Erzeugung
2. Snapshot- und exakter Kandidatenlauf
3. deterministische Clusterbildung und atomarer Publisher
4. inkrementelle Verarbeitung, Revisit, Backfill und Reprocessing
5. Health-, Audit- und Story-REST-API
```

## Abgrenzung

Dieses Ticket implementiert keinen Embedding-Client, keinen Scheduler, keinen Cluster-Algorithmus,
keinen Publisher, keinen Backfill und keine REST API. Es aktiviert keine Clustering-Version,
erzeugt keine produktiven Stories und fuehrt keinen externen Modellaufruf aus. Topics, Themes,
generierte Story-Titel oder Zusammenfassungen, Mehrfachmitgliedschaft und approximative
Vektorsuche bleiben ausserhalb des MVP.
