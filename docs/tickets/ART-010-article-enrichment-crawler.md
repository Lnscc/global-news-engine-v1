# ART-010: Article-Enrichment-Crawler implementieren

Status: offen
Bereich: articles
Prioritaet: zurueckgestellt

## Kontext

Die Analyse der real importierten GKG-Daten hat ergeben, dass Feld 27 (`V2EXTRASXML`) seit 2019
nahezu flaechendeckend einen Artikeltitel in `<PAGE_TITLE>` enthaelt. Der zuvor implementierte
Crawler wurde deshalb wieder aus der aktiven Anwendung entfernt. GDELT-Metadaten werden zuerst in
ART-011 erschlossen.

## Spaeteres Ziel

Ein Crawler wird erst wieder eingefuehrt, wenn Felder benoetigt werden, die GDELT nicht ausreichend
liefert, insbesondere Volltext, Hauptbild, Sprache oder eine Qualitaetsvalidierung. GDELT-Titel sind
dann die primaere Quelle; ein gecrawlter Titel darf sie nur nach einer dokumentierten Qualitaetsregel
ersetzen.

## Voraussetzungen fuer eine Wiederaufnahme

```text
- messbarer Bedarf fuer mindestens ein nicht durch GDELT gedecktes Feld
- geklaerte rechtliche und operative Rahmenbedingungen
- SSRF-Schutz, Timeouts, Response- und Redirect-Limits
- globale und domainbezogene Parallelitaetsgrenzen
- Retry- und Domain-Backoff insbesondere fuer HTTP 429
- Lasttest und Betriebsmetriken
- klare Quellen- und Konfliktregeln je Feld
```

## Abgrenzung

Die Extraktion von `PAGE_TITLE`, `PAGE_PRECISEPUBTIMESTAMP`, `PAGE_AUTHORS` und weiteren
GKG-Extras ist Gegenstand von ART-011 und nicht dieses Tickets. Die persistente Vorbereitung aus
ART-008 wurde mit Migration V6 wieder entfernt; ein spaeterer Crawler erhaelt ein neu begruendetes
Schema statt eines vorsorglich ungenutzten Modells.

## Implementierungskommentar

Ein funktionsfaehiger Crawler-Prototyp wurde umgesetzt und mit realen Daten getestet. Dabei wurden
hoher Durchsatz, aber auch vermeidbare externe Requests, Timeouts, HTTP 429 und Consent-Seiten als
Titelquelle sichtbar. Nach dem Nachweis der nahezu vollstaendigen GDELT-Titelabdeckung wurde der
aktive Job samt HTTP-Client, Parser, Konfiguration und Tests bewusst wieder entfernt. Auch die tote
`article_enrichments`-Tabelle wird durch V6 geloescht. Das Ticket bleibt zurueckgestellt und sein
Status wurde nicht geaendert.
