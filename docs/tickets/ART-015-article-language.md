# ART-015: Artikelsprache pruefen und modellieren

Status: offen
Bereich: articles, gdelt

## Kontext

Die Artikelschicht speichert derzeit keine Sprache. Fuer Filterung, Darstellung und spaetere
Story-Bildung ist sie nuetzlich, aber in den aktuell persistierten GKG-Feldern existiert keine
bereits nachgewiesene, eindeutige Sprachangabe. Domain oder Titel allein sind keine belastbare
fachliche Quelle.

## Ziel

An realen Daten wird geklaert, ob die Artikelsprache ohne externen Seitenabruf verlaesslich aus den
vorhandenen GDELT-Daten bestimmt werden kann. Bei ausreichender Qualitaet wird sie mit Quelle und
normalisiertem Sprachcode am Artikel persistiert und ueber die API ausgegeben.

## Umfang

```text
- GKG-Rohfelder, V2EXTRASXML und verfuegbare GDELT-Datensaetze auf Sprachsignale untersuchen
- Abdeckung und Qualitaet anhand einer reproduzierbaren, mehrsprachigen Stichprobe messen
- keine Ableitung allein aus TLD, Domain oder Publisher-Namen
- geeigneten Sprachstandard festlegen, bevorzugt BCP 47 beziehungsweise ISO-639-basiert
- Mindestanforderungen fuer eine automatische Uebernahme dokumentieren
- bei belastbarer Quelle language und language_source nullable am Artikel persistieren
- Konfliktregel fuer mehrere Sprachsignale definieren
- bei Persistenz ArticleSummary, ArticleDetail und REST-Responses erweitern
- Analyse, Tests, Quellenmatrix und gegebenenfalls Postman-Collection aktualisieren
```

## Akzeptanzkriterien

```text
- Analyse dokumentiert untersuchte Quellen, Stichprobe, Abdeckung und erkennbare Fehlklassifikationen
- Entscheidung ist begruendet: vorhandene GDELT-Quelle nutzen, lokale Erkennung einfuehren oder vertagen
- Sprachcodes und Normalisierung sind eindeutig dokumentiert
- Sprache wird nicht aus Domain oder TLD geraten
- falls implementiert: language und languageSource sind nullable, idempotent und API-seitig getestet
- falls nicht implementiert: konkreter Datenbedarf und ein abgegrenzter Folgeschritt sind dokumentiert
- ein externer Crawler wird nicht ohne erneuten Nachweis und die Voraussetzungen aus ART-010 aktiviert
- bei einer API-Aenderung ist die Postman-Collection aktualisiert und valides JSON
```

## Abhaengigkeit

Das Ticket kann unabhaengig von ART-014 analysiert werden. Eine spaetere Sprachfilterung in
`GET /articles` baut auf dem Ergebnis dieses Tickets und ART-013 auf.

