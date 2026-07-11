# ART-007: Article Enrichment vorbereiten

Status: erledigt
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

## Umsetzungskommentar

Dokumentiert am 2026-07-11:

- `docs/articles.md` beschreibt `article_enrichments` als separate 1:1-Tabelle fuer Titel,
  Publikationszeitpunkt, Sprache, Hauptbild, extrahierten Text und den Verarbeitungszustand.
- Das Modell definiert Status, Retry- und Fehlerfelder sowie die Regeln fuer erfolgreiche,
  fehlgeschlagene und parallel ausgefuehrte Enrichment-Versuche.
- GDELT-Extraktion und Crawling bleiben getrennte Jobs; der bestehende Extractor erhaelt keine
  Crawling-Verantwortung.
- Ein additiver, schrittweiser Migrations- und Backfill-Pfad bis zur spaeteren API-Erweiterung ist
  festgehalten. Diese Planung fuehrt noch keine Datenbank- oder API-Aenderung ein.
