# Global News Engine - Projektuebersicht

Global News Engine ist ein Spring-Boot-Dienst, der GDELT-2.0-Daten importiert, in ein dauerhaftes
Fachmodell ueberfuehrt und daraus abfragbare Artikel sowie reproduzierbare Vorstufen fuer
Story-Clustering erzeugt. Das langfristige Produktziel ist eine globale Ereignis- und
Analyseplattform mit der fachlichen Kette:

```text
GDELT-Signale -> Artikel -> Stories -> Topics -> Themes
```

GDELT ist dabei die Quelle, nicht das zentrale Produktmodell. Artikel buendeln Inhalte und
Signale; Stories sollen Berichte ueber dasselbe konkrete Geschehen gruppieren. Topics und Themes
sind spaetere Aggregationsstufen.

## Umfang und aktueller Stand

Implementiert sind:

- regelmaessige Erkennung und idempotenter Import vollstaendiger GDELT-Zeitfenster fuer EVENTS,
  MENTIONS und GKG;
- Trennung temporaerer Roh-Payloads von dauerhaft geparsten und normalisierten Fachzeilen;
- dauerhafte Fehlerhistorie, Pipeline-Health und sichere Payload-Retention;
- kanonische, deduplizierte Artikel mit GDELT-Signalen und lesender REST API;
- versionierte Titel-Inputs und OpenAI-Titel-Embeddings fuer `SHADOW`-Clustering-Versionen;
- unveraenderliche Snapshots, exakte Cosine-Suche und persistierte Kandidatenpaar-Entscheidungen.

Noch nicht implementiert sind die Bildung und Veroeffentlichung von Story-Mitgliedschaften,
Story-Merge/Split, eine Story API, Topics, Themes und eine Visualisierung. Das Datenbankschema
enthaelt bereits Tabellen und Invarianten fuer den spaeteren Story-Lebenszyklus; der laufende
Snapshot-Job schreibt jedoch nur Snapshots, Runs und Paarentscheidungen. Alle vorhandenen
Clustering-Versionen bleiben `SHADOW` und sind nicht produktsichtbar.

Bewusste Nicht-Ziele des aktuellen Story-MVP sind Volltext-Crawling, Volltext-Embeddings,
generative LLM-Zusammenfassungen und approximative Vektorsuche. Titel-Embeddings und Zeit sind
die einzigen entscheidenden Clustering-Signale.

## Architektur

Die Anwendung ist ein modular gegliederter Monolith. Spring Scheduling startet die
Hintergrundjobs; JDBC bildet die datenintensiven, explizit kontrollierten Persistenzpfade ab. Die
Produktionsdaten liegen in PostgreSQL und werden ausschliesslich ueber versionierte
Flyway-Migrationen strukturiert.

```text
GDELT Masterfile
    -> Download und Payload-Import
    -> Parsing und Normalisierung
    -> dauerhafte GDELT-Fachzeilen
    -> URL-Normalisierung und Artikel-Upsert
    -> Artikel REST API
    -> Titel-Input und Embedding
    -> eingefrorener Snapshot
    -> exakte Kandidatenpaare im Shadow-Modus
```

Fehler werden an der jeweiligen Schicht festgehalten. Parsing-Fehler landen in
`gdelt_processing_errors`; fachlich gueltige GDELT-Zeilen mit nicht verwendbarer Artikel-URL in
`article_extraction_errors`; Embedding-Versuche und Providerfehler in
`story_embedding_attempts`. Wiederholte Joblaeufe verwenden stabile Schluessel, Unique
Constraints und Transaktionsgrenzen, statt bestehende Ergebnisse zu duplizieren.

### Module und Verantwortlichkeiten

| Bereich | Verantwortung | Wichtige Orte |
|---|---|---|
| `gdelt.discovery` | GDELT-Masterfile lesen und nur vollstaendige Zeitfenster bestimmen | `src/main/java/.../gdelt/discovery` |
| `gdelt.raw` | ZIP-Dateien laden und Quellzeilen samt Import-Provenienz idempotent speichern | `src/main/java/.../gdelt/raw` |
| `gdelt.staging` | EVENTS, MENTIONS und GKG parsen, normalisieren und Fehler historisieren | `src/main/java/.../gdelt/staging` |
| `gdelt.retention` | erfolgreich verarbeitete Payloads nach Ablauf der Frist batchweise entfernen | `src/main/java/.../gdelt/retention` |
| `articles` | URLs normalisieren, Artikel ableiten, Health berechnen und REST-Abfragen bedienen | `src/main/java/.../articles` |
| `stories.embedding` | Titel klassifizieren und normalisieren, Embeddings erzeugen, Retries und Health verwalten | `src/main/java/.../stories/embedding` |
| `stories.snapshot` | Inputs einfrieren, Runs koordinieren, Vektoren pruefen und Kandidatenpaare berechnen | `src/main/java/.../stories/snapshot` |
| Datenbankmigrationen | Schema, Views, Constraints, Seed-Versionen und Unveraenderlichkeits-Trigger | `src/main/resources/db/migration` |

Die Paketgrenzen spiegeln Verantwortlichkeiten wider; die Anwendung wird derzeit als ein Prozess
und ein deploybares Artefakt betrieben.

## Daten und zentrale Ablaeufe

### GDELT-Pipeline

EVENTS, MENTIONS und GKG folgen demselben Muster:

```text
*_payloads.id -> Parsing/Normalisierung -> Fachtabelle.id -> article_id
```

Die Payload-Tabellen enthalten die unveraenderte `raw_tsv`-Quellzeile. Erfolgreiche Fachzeilen
verwenden dieselbe stabile ID und enthalten nur typisierte beziehungsweise normalisierte Werte.
`gdelt_pipeline_health_view` stellt Bestand, ausstehende Payloads, offene Fehler und Fachzeilen je
Datensatztyp gegenueber. Der Retention-Job entfernt nur Payloads, deren Fachzeile existiert und
deren `parsed_at` die konfigurierte Frist ueberschritten hat; Fachzeilen, unverarbeitete Payloads
und die Fehlerhistorie bleiben erhalten.

### Artikel und REST-Schnittstelle

Die Artikelidentitaet basiert auf einer normalisierten URL und deren SHA-256-Hash. EVENTS,
MENTIONS und GKG referenzieren den abgeleiteten Artikel direkt ueber `article_id`. Titel und
Publikationszeitpunkt werden fuer die API aus geeigneten GKG-Feldern projiziert, ohne diese
Metadaten am Artikel zu duplizieren.

Die aktuelle HTTP-Schnittstelle ist rein lesend:

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/articles` | Artikel suchen und paginieren |
| `GET` | `/articles/{id}` | Artikeldetail mit GDELT-Signalen |
| `GET` | `/articles/domains/top` | haeufigste Domains |
| `GET` | `/articles/themes/top` | haeufigste GKG-Themes |
| `GET` | `/articles/extraction/health` | Extraktions- und offene Processing-Fehler |

Der Request/Response-Vertrag und seine Tests liegen in [`postman`](postman).

### Story-Vorstufe

Fuer jede `SHADOW`-Clustering-Version wird der aktuelle, entscheidungsrelevante Artikelzustand als
`story_article_inputs` versioniert. Verwendbare normalisierte Titel werden ueber die OpenAI
Embeddings API eingebettet. Die kanonischen Float32-Vektoren liegen ohne `pgvector` in `BYTEA`;
Hashes, Dimensionen und Datenbank-Constraints schuetzen ihre Reproduzierbarkeit.

Der Snapshot-Job friert alle bis zum Watermark aktuellen Inputs mit `READY`-Embedding ein. Fuer
die vorhandenen 24-, 48- und 72-Stunden-Versionen berechnet er alle zulaessigen Paare mit exakter
Cosine Similarity und ohne Top-k-Grenze. Treffer ab `0.700000` werden als `SAME_STORY`
persistiert; fuer Inputs ohne positiven Treffer bleibt eine deterministische beste Diagnose als
`UNCERTAIN`. Snapshot-, Run- und Entscheidungshashes sowie Advisory Locks und Fencing-Tokens
machen Wiederholung und konkurrierende Ausfuehrung kontrollierbar.

## Technologie und Integrationen

| Technologie | Rolle |
|---|---|
| Java 21 | Anwendungssprache |
| Spring Boot | Laufzeit, Dependency Injection, Web und Scheduling |
| Spring Modulith | Modulkonventionen und Modultests |
| Spring JDBC/JPA | explizite SQL-Zugriffe und Datenbankintegration |
| PostgreSQL | produktive Persistenz und transaktionale Koordination |
| Flyway | versionierte Schemaentwicklung |
| Micrometer | Metriken fuer Embedding- und Snapshot-Jobs |
| OpenAI Embeddings API | Titel-Embeddings; nur aktiv mit `OPENAI_API_KEY` |
| Maven Wrapper | reproduzierbarer Build und Testlauf |
| Docker Compose | lokale PostgreSQL-Instanz |
| H2 | schnelle Tests, soweit keine PostgreSQL-Semantik erforderlich ist |

Live-Ingestion benoetigt Zugriff auf `data.gdeltproject.org`. Ohne `OPENAI_API_KEY` laeuft die
Anwendung weiter; verwendbare Embedding-Artefakte bleiben dann `PENDING` und die Health-Metrik
meldet deaktivierte Modellaufrufe.

## Konfiguration

Die Defaults stehen in
[`src/main/resources/application.properties`](../src/main/resources/application.properties).
Wichtige Praefixe sind:

| Praefix | Bedeutung |
|---|---|
| `gdelt.ingestion.*` | Discovery, Download, Polling und Begrenzung je Lauf |
| `gdelt.staging.*` | Parsing, Retry, Batchgroesse und Laufbegrenzung |
| `gdelt.retention.*` | Aufbewahrungsfrist und begrenzte Loeschlaeufe |
| `articles.*` | inkrementelle Artikel-Extraktion |
| `stories.embeddings.*` | Provider, Timeout, Batches, inkrementelle und Repair-Jobs |
| `stories.snapshots.*` | inkrementelle Snapshots, Claims und optionaler Backfill |

Die Ingestion-, Staging-, Retention-, Artikel-, Embedding- und inkrementellen Snapshot-Jobs sind
standardmaessig aktiv. Snapshot-Backfill ist standardmaessig deaktiviert und benoetigt ein festes
Watermark. Spring-Konfiguration kann ueber Umgebungsvariablen, Kommandozeilenargumente oder eine
externe Konfigurationsdatei ueberschrieben werden.

## Entwicklung und Betrieb

Voraussetzungen sind JDK 21 sowie Docker mit Compose. Maven muss nicht separat installiert sein.

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

Die Anwendung startet auf `http://localhost:8080`; Flyway migriert die lokale Datenbank beim
Start. `compose.yaml` stellt PostgreSQL auf `localhost:5432` mit Datenbank und Benutzer `gne`
bereit.

Alle Unit- und PostgreSQL-Integrationstests laufen mit:

```powershell
docker compose up -d
.\mvnw.cmd verify
```

Tests mit PostgreSQL legen temporaere Schemas an und entfernen sie anschliessend. Fuer das
Packaging steht der normale Maven-Lifecycle zur Verfuegung; eine produktive Deployment- oder
Infrastrukturdefinition ausserhalb der lokalen Compose-Datenbank ist im Repository nicht
vorhanden.

## Leitplanken und offene Entscheidungen

- Die Story-Definition ist fachlich und nicht Event-first: ein gemeinsames GDELT-Event, Theme,
  eine Entitaet oder Domain allein begruendet keine gemeinsame Story.
- Clustering muss aus versionierten Inputs, gespeicherten Embeddings, Snapshots und Entscheidungen
  reproduzierbar sowie auditierbar bleiben.
- Erst die noch fehlende Assignment- und Publish-Stufe darf `stories`, Mitgliedschaften, Lineage
  und Zustandsaenderungen materialisieren.
- Die Promotion einer `SHADOW`-Clustering-Version in einen produktsichtbaren Zustand braucht eine
  bewusste fachliche Freigabe; die Anwendung nimmt sie nicht automatisch vor.
- Backup- und Reimport-Anforderungen muessen geklaert sein, bevor die Payload-Retention verkuerzt
  wird, weil geloeschte `raw_tsv`-Zeilen nicht anderweitig im System archiviert werden.
- Das Ziel und der Vertrag fuer eine spaetere Story API, Topics, Themes, Visualisierung und ein
  produktives Deployment sind noch offen.

## Vertiefende Dokumentation

- [`articles.md`](articles.md) - Artikelmodell, Normalisierung und Extraktion
- [`gdelt_events_mentions_gkg_data_model_overview.md`](gdelt_events_mentions_gkg_data_model_overview.md) - GDELT-Fachmodell
- [`stories.md`](stories.md) - fachliche Story-Definition und MVP-Abgrenzung
- [`story-processing-contract.md`](story-processing-contract.md) - Lebenszyklus, Idempotenz und Clustering-Regeln
- [`story-data-model.md`](story-data-model.md) - persistiertes Story-Schema und Invarianten
- [`operations.md`](operations.md) - Betrieb, Health, Metriken und Diagnoseabfragen
- [`tickets/README.md`](tickets/README.md) - repository-lokaler Ticketprozess
