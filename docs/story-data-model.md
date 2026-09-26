# Produktives Story-Datenmodell

## Zweck

Diese Dokumentation beschreibt den implementierten Ist-Zustand aus ART-034 bis ART-038.
ART-041 hat den fachlichen Zielumfang inzwischen reduziert: nur aktuelle Zuordnungen mit
Begruendung, keine erforderliche Merge-/Split-Historie, ein produktives Verfahren und
Weiterverwendung bestehender Story-IDs auch bei Regelwechsel. Die unten beschriebenen
Historientabellen und Schutztrigger existieren weiterhin; sie sind nicht mehr pauschal
fachliche MVP-Anforderungen. ART-042 prueft ihre technischen Abhaengigkeiten, ART-043
setzt gegebenenfalls eine bestaetigte Migration um. Keine bestehende Migration wird geaendert.

ART-038 ergaenzt snapshotbezogene Zuordnungsschluessel und Publish-Nachweise gegen
veraltete Retries. Weiter zu pruefen sind aktuelle Zuordnungen ohne Historienpflicht und
interne Versions-Fremdschluessel bei stabilen oeffentlichen IDs. Ein
Verfahrenswechsel darf technisch neue Zeilen erzeugen, aber nicht alle Story-IDs ersetzen.
Die vorhandenen 48-/72-h-Versionen erfordern keinen dauerhaften Parallelbetrieb.

Migration `V22__create_story_domain_model.sql` bildet den fachlichen Verarbeitungsvertrag aus
`story-processing-contract.md` als persistierbares PostgreSQL-Schema ab. Migration
`V23__enforce_story_model_immutability` ergaenzt PostgreSQL-Schutztrigger. Das Schema speichert
Versionen, unveraenderliche Embeddings, Artikel-Inputs, eingefrorene Snapshots, Runs, Stories,
historisierte Mitgliedschaften, Lineage und Entscheidungen.

V22 aktiviert keine Version. Die 24-, 48- und 72-Stunden-Versionen werden ausschliesslich mit
Status `SHADOW` angelegt. Der mit ART-036 ergaenzte Snapshot-Job materialisiert diese vorhandenen
Tabellen, ohne das Schema oder den Versionsstatus zu aendern.

## Tabellen und Kardinalitaeten

| Tabelle | Zweck | Zentrale Beziehungen |
|---|---|---|
| `story_clustering_versions` | Unveraenderlicher fachlicher Vertrag einer Clustering-Version | 1:n zu Inputs, Snapshots, Runs, Stories und Entscheidungen |
| `story_clustering_version_status_history` | Audit der erlaubten Statuswechsel | n:1 zur Clustering-Version |
| `story_embedding_artifacts` | Dedupliziertes Titel-Embedding samt kanonischem Vektor | 1:n zu Artikel-Inputs und Snapshot-Mitgliedern |
| `story_embedding_attempts` | Append-only Versuch- und Fehlerhistorie | n:1 zum Embedding-Artefakt |
| `story_article_inputs` | Versionierter entscheidungsrelevanter Zustand eines Artikels | n:1 zu Version, Artikel und Embedding |
| `story_snapshots` | Benannter, eingefrorener Eingabestand | n:1 zur Version |
| `story_snapshot_members` | Materialisierte Inputs und Embedding-Hashes eines Snapshots | n:1 zu Snapshot und Artikel-Input |
| `story_processing_runs` | Inkrementeller, Backfill- oder Reprocessing-Lauf | n:1 zu Version und Snapshot; optionaler Retry-Bezug |
| `stories` | Stabile Story-Identitaet, Zustand, Anker und Medoid | n:1 zur Version; n:1 zu erzeugendem und letztem Run |
| `story_pair_decisions` | Reproduzierbare Kandidaten- und Pair-Entscheidung | n:1 zu Run, Snapshot, Inputs und Embeddings |
| `story_assignment_decisions` | Wirksame Zuordnung oder `UNASSIGNED` | n:1 zu Run, Snapshot und Artikel-Input |
| `story_memberships` | Gueltigkeitsintervall einer Artikelzuordnung | n:1 zu Story, Artikel und Zuordnungsentscheidung |
| `story_lineage` | Nachfolgerkanten fuer Merge, Split und Reprocessing | n:1 von alter zu neuer Story |
| `story_state_changes` | Audit fuer Story-Zustandswechsel | n:1 zu Story und Run |
| `story_publish_commits` | Atomarer, idempotenter Publish-Nachweis | 1:1 zu erfolgreichem Run und Snapshot |

Alle Tabellen, die versionierte Fachdaten verbinden, verwenden zusammengesetzte Fremdschluessel
mit `clustering_version_id`. Ein Snapshot oder Run kann dadurch keine Inputs oder Stories einer
anderen Version referenzieren.

## Persistenzentscheidungen

### Embedding-Vektor

Der normalisierte Vektor wird ohne `pgvector` als kanonische Float32-Bytefolge in `BYTEA`
gespeichert:

```text
Laenge = embedding_dimension * 4
Startversion = 1.536 Dimensionen = 6.144 Bytes
vector_hash = SHA-256 der kanonischen Bytefolge
vector_norm = positive, endliche NUMERIC-Norm
```

`BYTEA` erhaelt exakt die vom ART-033-Vertrag definierte Dimensionsreihenfolge und Float32-
Darstellung. `vector_hash` und die Laengenpruefung erkennen abweichende oder beschaedigte
Artefakte. Die spaetere exakte Cosine-Suche liest diese Bytes und akkumuliert mit Float64. Ein
approximativer Index wird nicht vorweggenommen.

Mehrere Artikel duerfen dasselbe Artefakt referenzieren. Der Unique Constraint ueber Modell,
Modellversion, Dimension, Titel-Normalisierung und `title_input_hash` verhindert konkurrierende
Artefakte fuer denselben fachlichen Schluessel.

### Artikel-Inputs und aktueller Zustand

Ein Input ist ueber Clustering-Version, `article_ref` und `article_input_fingerprint` eindeutig.
`article_ref` verweist gemeinsam mit der lokalen ID auf `(articles.id, articles.url_hash)`.

Aktuelle Inputs und Mitgliedschaften tragen `current_marker = 1`; historische Zeilen tragen
`NULL` und ein Ende. Ein Unique Constraint auf Version, Artikelreferenz und Marker erlaubt
beliebig viele historische Zeilen, aber hoechstens eine aktuelle Zeile. Diese Darstellung ist
portabel und benoetigt keinen partiellen Index.

### Eingefrorene Snapshots

Snapshot-Mitglieder werden materialisiert. Jede Zeile speichert den exakten Artikel-Input-
Fingerprint und bei einem verwendbaren Titel die konkrete Kombination aus Embedding-Artefakt und
Vektor-Hash. Zusammengesetzte Fremdschluessel verhindern Versions- oder Hash-Mischung. Ein spaeter
veraenderter Artikel erzeugt einen neuen Input und Snapshot, statt einen vorhandenen Snapshot
umzudeuten.

ART-036 bildet `snapshot_input_hash`, `snapshot_key`, `run_key` und `decision_hash` aus
laengenpraefigierten, kanonischen UTF-8-Feldern und SHA-256. Watermarks werden vor Hashing und
Persistenz auf PostgreSQL-Mikrosekunden normalisiert. Snapshot-Inputs sind nach
`effective_at, article_ref` sortiert. Transaktionale Advisory Locks und die vorhandenen Unique
Constraints sorgen dafuer, dass konkurrierende Worker dieselbe fachliche Snapshot- und Run-Zeile
verwenden. Ein abgebrochener oder fehlgeschlagener Run wird nach Ablauf seines Claim-Timeouts mit
demselben Snapshot und einem neuen Fencing-Token fortgesetzt. Snapshot-Materialisierung und
Run-Claim erfolgen atomar unter diesem Versions-Lock; ein frischer `RUNNING`-Run blockiert deshalb
einen zweiten Snapshot derselben Version auch nach einem Anwendungsneustart.

Die exakte Kandidatensuche dekodiert die big-endian Float32-Bytefolge dimensionsgetreu, prueft
Byte-Laenge, SHA-256, endliche Werte und positive Norm und akkumuliert Skalarprodukt und Normen
mit Float64. Der persistierte Score wird auf sechs Nachkommastellen gerundet. Paarreihenfolge,
Rangfolge und Top-1-Tie-Breaks sind deterministisch. Aufgrund der kanonischen zeitlichen
Sortierung endet die Suche je linkem Artikel am ersten rechten Artikel ausserhalb des
versionierten Zeitfensters.

### Auditdaten

Entscheidungsrelevante Werte liegen in typisierten Spalten. Optionale strukturierte
Diagnoseevidenz wird als `TEXT` gespeichert und muss vom spaeteren Writer kanonisch serialisiert
und als `non_decisive` gekennzeichnet werden. Sie beeinflusst weder Unique Constraints noch
fachliche Entscheidungen.

Pair-, Zuordnungs-, Zustands- und Publish-Daten sind getrennt, weil sie verschiedene
Idempotenzschluessel und Lebenszyklen besitzen:

```text
Pair: Entscheidungshash und kanonisches Artikelpaar im Snapshot
Zuordnung: Version, Snapshot, Artikelreferenz und Artikel-Input-Fingerprint (V24)
Publish: Version und Publish-Schluessel sowie hoechstens ein Commit je Snapshot
```

### Historie und Bereinigung

Veroeffentlichte Story-IDs, Embeddings, Snapshots, Entscheidungen, Mitgliedschaften und
Lineage-Kanten besitzen keine kaskadierenden Loeschregeln. Historische Zeilen werden beendet oder
ueber Statuswerte aus dem aktiven Pfad genommen, nicht ueberschrieben.

Eine spaetere Bereinigung darf nur nicht referenzierte Shadow-Artefakte entfernen. Sie benoetigt
einen eigenen Retention-Vertrag und ist nicht Teil von ART-034.

## Datenbankseitige Invarianten

V22 erzwingt insbesondere:

```text
- nur definierte Versions-, Embedding-, Run- und Story-Zustaende
- nur erlaubte Versions- und Story-Zustandsuebergaenge in den Historientabellen
- exakte READY-Vektorgroesse, Hash, positive Norm und READY-Zeitpunkt
- konsistente Titel-, Embedding- und Retry-Payloads
- hoechstens einen aktuellen Input und eine aktuelle Mitgliedschaft je Artikel und Version
- keine versionsfremden Snapshot-, Run-, Story- oder Entscheidungsreferenzen
- kanonische Reihenfolge und eindeutige Speicherung von Artikelpaaren
- dauerhafte, versionstreue Merge- und Split-Lineage
- eindeutige Embedding-, Snapshot-, Bewertungs- und Publish-Schluessel
- nicht negative Health-Zaehler, Fencing-Tokens und optimistische Versionen
```

Die fachliche Unveraenderlichkeit wird durch neue Identitaeten fuer neue Inputs oder
Clustering-Vertraege, append-only Historientabellen, restriktive Fremdschluessel und
PostgreSQL-Trigger durchgesetzt. V23 verhindert Definitionaenderungen an Clustering-Versionen,
Aenderungen an `READY`-Embeddings und Snapshot-Aenderungen; Snapshot-Mitglieder werden ab dem
ersten Run eingefroren. Historien- und Entscheidungstabellen sind append-only. Eine aktuelle
Mitgliedschaft darf nur einmalig und ohne Aenderung ihres fachlichen Inhalts beendet werden.
Ein Regel-, Titel- oder Modellwechsel erzeugt neue Zeilen.

## Atomare Publikation (ART-038)

V24 erweitert ausschliesslich den Unique-Schluessel der Assignment-Entscheidungen um
`snapshot_id`. Dadurch koennen neue Nachbarn bei unveraendertem Artikel-Fingerprint eine
neue Entscheidung ausloesen. Append-only-Schutz und aktuelle Mitgliedschaftseindeutigkeit
bleiben bestehen. Die Schemaaenderung wurde fuer ART-038 ausdruecklich bestaetigt.

`StoryPublisher` gleicht die Partition gegen die aktuellen Mitgliedschaften derselben
Clustering-Version ab. Er nominiert pro bisheriger Story eine Split-Komponente und waehlt
bei mehreren Nominierungen den Merge-Gewinner nach `(created_at, story_id)`. Neue IDs
werden deterministisch aus Version, Snapshot-Input-Hash und erstem Komponentenmitglied
gebildet. Bestehende Identitaetsanker bleiben erhalten. Lineage wird nicht geschrieben.

Publikation, Paar- und Assignment-Entscheidungen, Mitgliedschaften und Run-Abschluss teilen
eine Transaktion. Der Versions-Lock, die Lease mit Fencing-Token und die gelesenen
optimistischen Story-Versionen verhindern konkurrierende Teilstaende. Publish-Commits
verhindern doppelte Publikation; die Reihenfolge `(snapshot_watermark, snapshot_id)`
verhindert das Wiederherstellen abgeloester Staende. Vor ART-038 erfolgreiche Runs ohne
Publish-Commit koennen mit neuem Fencing-Token publiziert werden.

Ein Snapshot enthaelt auch Inputs ohne verwendbaren Vektor. Nullwerte fuer Artefakt und
Hash frieren deren Nichtverfuegbarkeit ein; ein spaeter fertiges Embedding wird erst in
einem neuen Snapshot verwendet. Aktuelle UNASSIGNED-Evidenz liegt in den Entscheidungen
publizierter Snapshots. Bei einem No-op bleiben die vorhandene Mitgliedschaft und ihre
Evidenz erhalten; die neue Snapshot-Entscheidung lautet `NO_CHANGE`.

REST-Endpunkte und die Promotion einer Shadow-Version sind nicht enthalten.
