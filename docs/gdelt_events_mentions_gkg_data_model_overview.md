# GDELT Datenmodell – Events, Mentions und GKG

# Übersicht

GDELT besteht aus drei zentralen Datensystemen:

```text
1. Events
2. Mentions
3. GKG (Global Knowledge Graph)
```

Diese drei Systeme ergänzen sich:

```text
Events   → Was ist passiert?
Mentions → Wie verbreitet sich das Ereignis?
GKG      → Worum geht es semantisch?
```

---

## Gemeinsames Persistenzmodell

Alle drei Datensatztypen verwenden dasselbe Persistenzmuster:

```text
*_payloads.id -> Parsing und Normalisierung -> Fachtabelle.id (identisch)
```

Nur die Payload-Tabellen enthalten `raw_tsv`. Fachzeilen enthalten ausschliesslich erfolgreich
geparste und normalisierte Werte. Fehlversuche werden dauerhaft in `gdelt_processing_errors`
historisiert. `gdelt_pipeline_health_view` zeigt je Datensatztyp die gesamte und ausstehende
Payload-Menge, offene Fehler und vorhandene Fachzeilen. Die Payload-Retention loescht erfolgreich
verarbeitete Quellzeilen nach einer konfigurierbaren, bei `parsed_at` beginnenden Frist in
begrenzten Batches. Payloads ohne Fachzeile, alle Fachtabellen und die Fehlerhistorie bleiben
dauerhaft erhalten.

# 1. EVENTS

## Persistenzmodell

EVENTS werden mit einer stabilen Identitaet verarbeitet:

```text
gdelt_event_payloads.id
-> Parsing
gdelt_events.id (identisch zur Payload-ID)
-> Article-Extraktion
article_signals.source_id
```

`gdelt_event_payloads` enthaelt die unveraenderte Quellzeile in `raw_tsv` und die
Import-Provenienz. `gdelt_events` enthaelt ausschliesslich erfolgreich geparste, typisierte
Felder sowie `ingested_at` und `parsed_at`; `raw_tsv` und ein technischer Verarbeitungsstatus
werden dort nicht gespeichert. Fehlerhafte Payloads bleiben ohne Fachzeile erhalten und koennen
erneut verarbeitet werden. Historische Parsing-Fehler liegen dauerhaft in
`gdelt_processing_errors`.

# Event-Felder

| Feld                 | Bedeutung                    |
| -------------------- | ---------------------------- |
| GLOBALEVENTID        | eindeutige Event-ID          |
| SQLDATE              | Datum                        |
| MonthYear            | Monat/Jahr                   |
| Year                 | Jahr                         |
| FractionDate         | Datum als Float              |
| Actor1Code           | Code Akteur 1                |
| Actor1Name           | Name Akteur 1                |
| Actor1CountryCode    | Land Akteur 1                |
| Actor1KnownGroupCode | Gruppe                       |
| Actor1EthnicCode     | Ethnie                       |
| Actor1Religion1Code  | Religion                     |
| Actor1Religion2Code  | Religion 2                   |
| Actor1Type1Code      | Typ 1                        |
| Actor1Type2Code      | Typ 2                        |
| Actor1Type3Code      | Typ 3                        |
| Actor2Code           | Code Akteur 2                |
| Actor2Name           | Name Akteur 2                |
| Actor2CountryCode    | Land Akteur 2                |
| Actor2KnownGroupCode | Gruppe                       |
| Actor2EthnicCode     | Ethnie                       |
| Actor2Religion1Code  | Religion 1                   |
| Actor2Religion2Code  | Religion 2                   |
| Actor2Type1Code      | Typ 1                        |
| Actor2Type2Code      | Typ 2                        |
| Actor2Type3Code      | Typ 3                        |
| IsRootEvent          | Haupt-Event                  |
| EventCode            | genauer CAMEO-Code           |
| EventBaseCode        | Basistyp                     |
| EventRootCode        | Oberkategorie                |
| QuadClass            | Konflikt-/Kooperationsklasse |
| GoldsteinScale       | Intensität                   |
| NumMentions          | Anzahl Erwähnungen           |
| NumSources           | Anzahl Quellen               |
| NumArticles          | Anzahl Artikel               |
| AvgTone              | durchschnittliche Tonalität  |
| Actor1Geo_Type       | Geo-Typ Akteur 1             |
| Actor1Geo_FullName   | Geo-Name Akteur 1            |
| Actor1Geo_CountryCode| Land Geo Akteur 1            |
| Actor1Geo_ADM1Code   | ADM1 Akteur 1                |
| Actor1Geo_ADM2Code   | ADM2 Akteur 1                |
| Actor1Geo_Lat        | Breitengrad Akteur 1         |
| Actor1Geo_Long       | Längengrad Akteur 1          |
| Actor1Geo_FeatureID  | Geo-ID Akteur 1              |
| Actor2Geo_Type       | Geo-Typ Akteur 2             |
| Actor2Geo_FullName   | Geo-Name Akteur 2            |
| Actor2Geo_CountryCode| Land Geo Akteur 2            |
| Actor2Geo_ADM1Code   | ADM1 Akteur 2                |
| Actor2Geo_ADM2Code   | ADM2 Akteur 2                |
| Actor2Geo_Lat        | Breitengrad Akteur 2         |
| Actor2Geo_Long       | Längengrad Akteur 2          |
| Actor2Geo_FeatureID  | Geo-ID Akteur 2              |
| ActionGeo_Type       | Geo-Typ Event                |
| ActionGeo_FullName   | Event-Ort                    |
| ActionGeo_CountryCode| Land Event                   |
| ActionGeo_ADM1Code   | ADM1 Event                   |
| ActionGeo_ADM2Code   | ADM2 Event                   |
| ActionGeo_Lat        | Breitengrad Event            |
| ActionGeo_Long       | Längengrad Event             |
| ActionGeo_FeatureID  | Geo-ID Event                 |
| DATEADDED            | Zeitpunkt Aufnahme           |
| SOURCEURL            | Ursprungsartikel             |

---

# 2. MENTIONS

MENTIONS verwenden `gdelt_mention_payloads.id` als stabile Identitaet der unveraenderten
Quellzeile und derselben erfolgreich geparsten Fachzeile in `gdelt_mentions.id`.

# Mention-Felder

| Feld                      | Bedeutung               |
| ------------------------- | ----------------------- |
| GLOBALEVENTID             | Referenz auf Event      |
| EventTimeDate             | Zeitpunkt des Events    |
| MentionTimeDate           | Zeitpunkt der Erwähnung |
| MentionType               | Typ der Quelle          |
| MentionSourceName         | Medium                  |
| MentionIdentifier         | URL                     |
| SentenceID                | Satzposition            |
| Actor1CharOffset          | Zeichenposition         |
| Actor2CharOffset          | Zeichenposition         |
| ActionCharOffset          | Position der Aktion     |
| InRawText                 | im Rohtext erkannt      |
| Confidence                | Sicherheit              |
| MentionDocLen             | Artikellänge            |
| MentionDocTone            | Tonalität               |
| MentionDocTranslationInfo | Übersetzung             |
| ExtrasXML                 | Zusatzdaten             |

---

# 3. GKG – GLOBAL KNOWLEDGE GRAPH

GKG verwendet `gdelt_gkg_payloads.id` als stabile Identitaet. Parsing und Normalisierung legen
dieselbe ID in `gdelt_gkg` an. Die Article-Extraktion ergaenzt danach die nullable `article_id`;
fehlerhafte Payloads bleiben ohne Fachzeile erneut verarbeitbar.

# GKG-Felder

| Feld                       | Bedeutung                |
| -------------------------- | ------------------------ |
| GKGRECORDID                | eindeutige ID            |
| DATE                       | Zeitpunkt                |
| SourceCollectionIdentifier | Quellen-Typ              |
| SourceCommonName           | Domain                   |
| DocumentIdentifier         | URL                      |
| Counts                     | Mengen/Zahlen            |
| V2Counts                   | erweiterte Counts        |
| Themes                     | Themen                   |
| V2Themes                   | Themen + Confidence      |
| V2EnhancedThemes           | erweiterte Themen        |
| Locations                  | Orte                     |
| V2Locations                | detaillierte Orte        |
| V2EnhancedLocations        | erweiterte Orte          |
| Persons                    | Personen                 |
| V2Persons                  | Personen erweitert       |
| V2EnhancedPersons          | erweiterte Personen      |
| Organizations              | Organisationen           |
| V2Organizations            | Organisationen erweitert |
| V2EnhancedOrganizations    | erweiterte Organisationen|
| V2Tone                     | Stimmung                 |
| Dates                      | erkannte Datumsangaben   |
| GCAM                       | Emotionen                |
| V2GCAM                     | erweiterte Emotionen     |
| SharingImage               | Hauptbild                |
| RelatedImages              | weitere Bilder           |
| SocialImageEmbeds          | Social Media Bilder      |
| SocialVideoEmbeds          | Videos                   |
| Quotes                     | Zitate                   |
| AllNames                   | alle Entities            |
| Amounts                    | Geld/Zahlen              |
| TranslationInfo            | Übersetzungen            |
| ExtrasXML                  | Zusatzinformationen      |
