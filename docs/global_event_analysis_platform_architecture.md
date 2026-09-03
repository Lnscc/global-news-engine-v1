# Global News Engine - Projektuebersicht

Global News Engine ist ein Spring-Boot-Dienst, der GDELT-2.0-Daten importiert und daraus
abfragbare Artikel sowie reproduzierbare Vorstufen fuer Story-Clustering erzeugt.

```text
GDELT-Signale -> Artikel -> Stories -> Topics -> Themes
```

GDELT ist die Quelle, nicht das zentrale Produktmodell. Artikel buendeln Inhalte und Signale;
Stories sollen Berichte ueber dasselbe konkrete Geschehen gruppieren. Topics und Themes sind
spaetere Aggregationsstufen.

## Stand

Implementiert sind:

- Import, Parsing und Normalisierung von GDELT EVENTS, MENTIONS und GKG;
- dauerhafte Fachzeilen, Fehlerhistorie, Pipeline-Health und Payload-Retention;
- deduplizierte Artikel mit GDELT-Signalen und lesender REST API;
- versionierte Titel-Inputs und OpenAI-Titel-Embeddings;
- unveraenderliche Snapshots und exakte Kandidatenpaar-Entscheidungen im `SHADOW`-Modus.

Noch nicht implementiert sind Story-Zuordnung und -Veroeffentlichung, eine Story API, Topics,
Themes und Visualisierung. Das Datenbankschema bereitet den Story-Lebenszyklus bereits vor, der
laufende Snapshot-Job schreibt aber nur Snapshots, Runs und Paarentscheidungen.

Volltext-Crawling, Volltext-Embeddings, generative Zusammenfassungen und approximative
Vektorsuche gehoeren nicht zum aktuellen Story-MVP.

## Architektur

Die Anwendung ist ein modular gegliederter Monolith. Spring Scheduling startet die Jobs; JDBC
steuert die Persistenzpfade. PostgreSQL speichert die Laufzeitdaten, Flyway versioniert das
Schema.

```text
GDELT Masterfile
    -> Download und Payload-Import
    -> Parsing und Normalisierung
    -> GDELT-Fachzeilen
    -> URL-Normalisierung und Artikel-Upsert
    -> Artikel REST API
    -> Titel-Embedding
    -> Snapshot
    -> Kandidatenpaare im Shadow-Modus
```

| Bereich | Verantwortung |
|---|---|
| `gdelt.discovery` | vollstaendige GDELT-Zeitfenster erkennen |
| `gdelt.raw` | Quelldateien laden und Payloads idempotent speichern |
| `gdelt.staging` | Payloads parsen, normalisieren und Fehler historisieren |
| `gdelt.retention` | erfolgreich verarbeitete Payloads fristgerecht entfernen |
| `articles` | URLs normalisieren, Artikel ableiten und REST-Abfragen bedienen |
| `stories.embedding` | Titel-Inputs, Embeddings, Retries und Health verwalten |
| `stories.snapshot` | Inputs einfrieren und Kandidatenpaare berechnen |

## Datenfluesse

Alle GDELT-Datensaetze folgen demselben Muster:

```text
*_payloads.id -> Parsing -> Fachtabelle.id -> article_id
```

Payloads enthalten die unveraenderte `raw_tsv`-Quellzeile. Erfolgreiche Fachzeilen verwenden
dieselbe ID und enthalten typisierte Werte. Der Retention-Job entfernt nur Payloads mit
vorhandener Fachzeile nach Ablauf der Frist. Fachzeilen, unverarbeitete Payloads und Fehler bleiben
erhalten.

Artikel werden ueber die normalisierte URL und ihren SHA-256-Hash dedupliziert. Titel und
Publikationszeitpunkt projiziert die API aus geeigneten GKG-Feldern.

Fuer jede `SHADOW`-Clustering-Version werden aktuelle Artikel-Inputs versioniert und verwendbare
Titel eingebettet. Der Snapshot-Job friert `READY`-Inputs ein und berechnet innerhalb des
versionierten Zeitfensters alle zulaessigen Paare mit exakter Cosine Similarity. Treffer ab
`0.700000` werden als `SAME_STORY`, die beste Diagnose ohne Treffer als `UNCERTAIN` gespeichert.
Der Job erzeugt noch keine Stories oder Mitgliedschaften.

## REST API

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/articles` | Artikel suchen und paginieren |
| `GET` | `/articles/{id}` | Artikeldetail mit GDELT-Signalen |
| `GET` | `/articles/domains/top` | haeufigste Domains |
| `GET` | `/articles/themes/top` | haeufigste GKG-Themes |
| `GET` | `/articles/extraction/health` | Pipeline- und Extraktionsfehler |

Collection und Vertragstests liegen in [`postman`](postman).

## Technologie

- Java 21, Spring Boot und Spring Modulith
- Spring JDBC/JPA, PostgreSQL und Flyway
- Micrometer fuer Job-Metriken
- OpenAI Embeddings API fuer Titel-Embeddings
- Maven Wrapper und Docker Compose
- H2 fuer Tests ohne erforderliche PostgreSQL-Semantik

Live-Ingestion benoetigt Zugriff auf `data.gdeltproject.org`. Ohne `OPENAI_API_KEY` laeuft die
Anwendung weiter; Embedding-Artefakte bleiben `PENDING`.

## Konfiguration und Betrieb

Die Defaults stehen in
[`application.properties`](../src/main/resources/application.properties). Die Praefixe
`gdelt.ingestion.*`, `gdelt.staging.*`, `gdelt.retention.*`, `articles.*`,
`stories.embeddings.*` und `stories.snapshots.*` steuern die jeweiligen Jobs. Snapshot-Backfill
ist standardmaessig deaktiviert.

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Die Anwendung startet auf `http://localhost:8080`; PostgreSQL laeuft lokal auf Port `5432`.

```powershell
docker compose up -d
.\mvnw.cmd verify
```

PostgreSQL-Integrationstests verwenden temporaere Schemas. Eine produktive Deployment- oder
Infrastrukturdefinition ist nicht vorhanden.

## Leitplanken

- Ein gemeinsames GDELT-Event, Theme, eine Entitaet oder Domain allein begruendet keine Story.
- Clustering-Ergebnisse muessen versioniert, reproduzierbar und auditierbar bleiben.
- Nur eine spaetere Publish-Stufe darf Stories und Mitgliedschaften materialisieren.
- Eine `SHADOW`-Version wird nur nach bewusster fachlicher Freigabe produktsichtbar.
- Vor kuerzerer Payload-Retention muessen Backup und Reimport geklaert sein.

## Vertiefung

- [`articles.md`](articles.md) - Artikelmodell und Extraktion
- [`gdelt_events_mentions_gkg_data_model_overview.md`](gdelt_events_mentions_gkg_data_model_overview.md) - GDELT-Fachmodell
- [`stories.md`](stories.md) - fachliche Story-Definition
- [`story-processing-contract.md`](story-processing-contract.md) - Clustering-Vertrag
- [`story-data-model.md`](story-data-model.md) - Story-Schema und Invarianten
- [`operations.md`](operations.md) - Betrieb und Diagnose
