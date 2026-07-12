# ART-019: GKG-Themes normalisieren

Status: offen
Bereich: gdelt, articles

## Kontext

GKG-Themes liegen als semikolongetrennter Rohstring vor. ART-013 filtert deshalb derzeit per
Stringoperation und ART-016 muesste dieselben Strings erneut zerlegen. Exakte Suche,
Deduplizierung, Indexierung und Aggregation sollten auf einzelnen Theme-Eintraegen arbeiten.

## Ziel

Themes werden zusaetzlich zum unveraenderten Rohwert als einzelne, dem GKG-Record zugeordnete
Eintraege persistiert.

## Zielmodell

```text
gdelt_gkg_themes
- gkg_record_id
- position
- theme

UNIQUE (gkg_record_id, theme)
```

## Umfang

```text
- Theme-Listenformat und Normalisierungsregeln dokumentieren
- leere Eintraege entfernen und vollstaendige Eintraege deduplizieren
- urspruengliche Reihenfolge ueber position nachvollziehbar halten
- bestehende GKG-Records idempotent backfillen
- ART-013-Themefilter auf exakten relationalen Match umstellen
- Top-Themes und ART-016-Aggregationen auf normalisierte Eintraege umstellen
- Parser-, Backfill-, Query- und Contract-Tests
```

## Akzeptanzkriterien

```text
- CLIMATE matched CLIMATE, aber nicht CLIMATE_CHANGE
- ein Theme kommt pro GKG-Record hoechstens einmal vor
- der originale themes_raw-Wert bleibt fuer Provenienz und erneutes Parsen erhalten
- Backfill und Neuimport erzeugen dieselben Theme-Eintraege
- Themefilter und Aggregationen verwenden keine Teilzeichenfolgensuche mehr
- Pagination und Artikelanzahl werden durch Joins nicht vervielfacht
```

## Abgrenzung

Uebersetzung, Hierarchien, semantische Gruppierung und Aehnlichkeitsbeziehungen zwischen Themes
sind nicht Teil dieses Tickets.
