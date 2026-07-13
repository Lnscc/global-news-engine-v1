# ART-014: Analyse von PAGE_PRECISEPUBTIMESTAMP

## Quelle und Semantik

GDELT beschreibt `PAGE_PRECISEPUBTIMESTAMP` als `YYYYMMDDHHMMSS`. Die vom Publisher erkannte
lokale Zeit wird laut GDELT nach UTC konvertiert. Bei vorhandener Publikations- und
Aenderungszeit verwendet GDELT die Publikationszeit.

Quelle: https://blog.gdeltproject.org/new-gkg-2-0-article-metadata-fields/

## Stichprobe vom 2026-07-13

Die reproduzierbare Abfrage steht in `ART-014-publication-time.sql`. Untersucht wurden alle zu
diesem Zeitpunkt lokal gestagten GKG-Zeilen aus der Web-Quellensammlung
`source_collection_identifier = 1`.

```text
Staging-Zeilen:                         17.232
PAGE_PRECISEPUBTIMESTAMP vorhanden:     9.148 (53,09 %)
Format YYYYMMDDHHMMSS:                  9.148 (100 % der vorhandenen Werte)
Andere nichtleere Formate:                  0
Fruehester Wert:                  1994-04-13T23:58:00Z
Spaetester Wert:                  2026-07-14T00:56:00Z
Akzeptiert:                             8.860
Mehr als 15 Minuten in der Zukunft:       288
Artikel mit mehreren Kandidaten:            2
Artikel mit unterschiedlichen Kandidaten:   0
```

`document_date` und der lokale `source_timestamp` waren in der Stichprobe identisch. Alte Werte
waren anhand ihrer URLs plausible Archivartikel und werden deshalb nicht allein wegen ihres Alters
verworfen. Die 288 Werte ausserhalb des 15-Minuten-Fensters lagen bis zu elf Stunden nach dem
GKG-Dokumentzeitpunkt und zeigten ein domainbezogenes Muster, das trotz der dokumentierten
UTC-Normalisierung auf fehlerhafte Zeitzonenmetadaten der Quellseiten hindeutet.

## Entscheidung

- Exakt 14-stellige, kalendarisch gueltige Werte werden strikt als UTC geparst.
- Ein Wert darf hoechstens 15 Minuten nach `document_date` liegen. Das entspricht einem
  GDELT-Aktualisierungsfenster und toleriert kleine Batchgrenzen, verwirft aber klare Zukunftswerte.
- Fuer alte Werte gibt es keine pauschale Untergrenze, weil GDELT auch erneut gefundene
  Archivartikel enthalten kann.
- Fehlende, syntaktisch ungueltige und unplausible Werte werden als `null` behandelt und blockieren
  weder Staging noch Artikel-Extraktion.
- Der Kandidat bleibt am verursachenden `gdelt_gkg_record`. Die Article API projiziert den Wert des
  fruehesten GKG-Records mit gueltigem Kandidaten nach `source_timestamp` und `id`.
- `publishedAtSource` ist bei vorhandenem Wert `GKG_PAGE_PRECISE_PUB_TIMESTAMP`, sonst `null`.
- `firstSeenAt` und die Standardsortierung von `GET /articles` bleiben unveraendert.

