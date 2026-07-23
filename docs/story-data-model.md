# Produktives Story-Datenmodell

## Zweck

Migration `V22__create_story_domain_model.sql` bildet den fachlichen Verarbeitungsvertrag aus
`story-processing-contract.md` als persistierbares PostgreSQL-Schema ab. Migration
`V23__enforce_story_model_immutability` ergaenzt PostgreSQL-Schutztrigger. Das Schema speichert
Versionen, unveraenderliche Embeddings, Artikel-Inputs, eingefrorene Snapshots, Runs, Stories,
historisierte Mitgliedschaften, Lineage und Entscheidungen.

V22 startet keinen Job und aktiviert keine Version. Die 24-, 48- und 72-Stunden-Versionen werden
ausschliesslich mit Status `SHADOW` angelegt.

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

### Auditdaten

Entscheidungsrelevante Werte liegen in typisierten Spalten. Optionale strukturierte
Diagnoseevidenz wird als `TEXT` gespeichert und muss vom spaeteren Writer kanonisch serialisiert
und als `non_decisive` gekennzeichnet werden. Sie beeinflusst weder Unique Constraints noch
fachliche Entscheidungen.

Pair-, Zuordnungs-, Zustands- und Publish-Daten sind getrennt, weil sie verschiedene
Idempotenzschluessel und Lebenszyklen besitzen:

```text
Pair: Entscheidungshash und kanonisches Artikelpaar im Snapshot
Zuordnung: Version, Artikelreferenz und Artikel-Input-Fingerprint
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

## Nicht enthalten

Das Schema enthaelt keinen Embedding-Client, Scheduler, Cluster-Algorithmus, Publisher-Service,
Backfill-Job oder REST-Endpunkt. Es erzeugt keine Story-Mitgliedschaften und macht keine
Shadow-Version produktsichtbar.
