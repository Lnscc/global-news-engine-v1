# ART-019: GKG-Themes normalisieren

Status: erledigt
Bereich: gdelt, articles

## Kontext

GKG-Themes liegen als semikolongetrennter Rohstring vor. ART-013 filtert deshalb derzeit per
Stringoperation und ART-016 muesste dieselben Strings erneut zerlegen. Exakte Suche,
Deduplizierung, Indexierung und Aggregation sollten auf einzelnen Theme-Eintraegen arbeiten.

## Ziel

Themes werden einmalig normalisiert und als geordnete, duplikatfreie Liste direkt am dauerhaften
GKG-Record persistiert. Die unveraenderte Quelle bleibt in Raw und Staging verfuegbar und wird im
Produktmodell nicht erneut dupliziert.

## Zielmodell

```text
gdelt_gkg_records
- themes TEXT[] NOT NULL
```

## Umfang

```text
- Theme-Listenformat und Normalisierungsregeln dokumentieren
- leere Eintraege entfernen und vollstaendige Eintraege deduplizieren
- urspruengliche Reihenfolge der gueltigen ersten Vorkommen im Array erhalten
- bestehende GKG-Records idempotent backfillen
- ART-013-Themefilter auf exakten Array-Match umstellen
- Top-Themes und ART-016-Aggregationen auf normalisierte Eintraege umstellen
- Parser-, Backfill-, Query- und Contract-Tests
```

## Akzeptanzkriterien

```text
- CLIMATE matched CLIMATE, aber nicht CLIMATE_CHANGE
- ein Theme kommt im Array eines GKG-Records hoechstens einmal vor
- das Produktmodell enthaelt keinen zusaetzlichen themes_raw-Rohstring
- Backfill und Neuimport erzeugen dasselbe Theme-Array
- Themefilter und Aggregationen verwenden keine Teilzeichenfolgensuche mehr
- Pagination und Artikelanzahl werden durch Joins nicht vervielfacht
```

## Abgrenzung

Uebersetzung, Hierarchien, semantische Gruppierung und Aehnlichkeitsbeziehungen zwischen Themes
sind nicht Teil dieses Tickets.

## Implementierungskommentar

Implementiert am 2026-07-12 und am 2026-07-13 auf ein einfacheres Zielmodell konsolidiert.
Migration V9 normalisiert vorhandene Rohwerte kontrolliert; Migration V10 uebernimmt das Ergebnis
als geordnetes `TEXT[]` nach `gdelt_gkg_records.themes` und entfernt anschliessend den duplizierten
Rohwert sowie die zwischenzeitliche Detailtabelle. Leere Eintraege werden verworfen, Werte getrimmt
und Duplikate unter Beibehaltung des ersten Vorkommens entfernt. Neue GKG-Extraktionen schreiben
das normalisierte Array direkt. Article-Themefilter, Top-Theme-Aggregation und REST-Details verwenden
die Arrays, sodass exakte Treffer wie `CLIMATE` nicht `CLIMATE_CHANGE` erfassen und Clients keine
Semikolon-Strings mehr zerlegen muessen. Migration, Extraktion, Query-Verhalten und Postman-Vertrag
sind durch Tests abgedeckt.
