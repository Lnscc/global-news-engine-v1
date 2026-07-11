# ART-007: Article Enrichment vorbereiten

Status: offen
Bereich: articles

## Kontext

`articles` speichert aktuell nur die kanonische URL und wenige technische Metadaten. Titel,
Publikationszeitpunkt, Sprache, Hauptbild und Text kommen erst spaeter ueber Crawling oder
Enrichment.

## Ziel

Das Zielmodell fuer angereicherte Artikel planen, ohne die erste Extraktionsschicht zu
ueberladen.

## Umfang

```text
- title
- published_at
- language
- main_image_url
- extracted_text oder article_content Tabelle
- enrichment_status und Fehlerbehandlung
```

## Akzeptanzkriterien

```text
- Zielmodell ist dokumentiert
- Entscheidung getroffen: Felder direkt auf articles oder separate Enrichment-Tabelle
- Crawling/Enrichment bleibt getrennt von GDELT-Extraktion
- Migrationspfad fuer spaetere Umsetzung ist beschrieben
```
