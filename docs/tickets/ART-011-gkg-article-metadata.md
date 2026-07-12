# ART-011: GKG-Artikelmetadaten extrahieren

Status: offen
Bereich: articles, gdelt

## Kontext

Die GKG-Rohzeilen werden vollstaendig in `gdelt_raw_gkg.raw_tsv` gespeichert, der Staging-Parser
wertet bisher aber nur Felder bis Index 15 aus. Das letzte TSV-Feld 27 (`V2EXTRASXML`) enthaelt in
den real importierten Daten nahezu immer `<PAGE_TITLE>` und teilweise weitere Seitenmetadaten.

## Ziel

Relevante GKG-Seitenmetadaten werden robust aus Feld 27 extrahiert, normalisiert und ohne externen
HTTP-Abruf in das Artikelmodell uebernommen. Der GDELT-Titel ist die primaere Titelquelle.

## Umfang

```text
- V2EXTRASXML als Feld 27 im GKG-Parser beruecksichtigen
- PAGE_TITLE extrahieren, HTML-Entities dekodieren, trimmen und Laengenlimit definieren
- PAGE_PRECISEPUBTIMESTAMP und PAGE_AUTHORS analysieren und ihre Persistenz entscheiden
- fehlende, leere, mehrfach vorkommende und fehlerhaft formatierte Tags behandeln
- title und title_source additiv im Artikelmodell persistieren
- Konfliktregel fuer mehrere GKG-Signale desselben Artikels definieren
- bestehende Raw-/Staging-Daten kontrolliert und idempotent nachziehen
- Quellenmatrix aktualisieren und API-Folgeticket ART-012 vorbereiten
- reale anonymisierungsfreie GKG-Beispiele als Test-Fixtures verwenden
```

## Akzeptanzkriterien

```text
- PAGE_TITLE wird aus realistischen 27-spaltigen GKG-Zeilen korrekt extrahiert
- numerische und benannte HTML-Entities werden dekodiert
- fehlendes oder defektes XMLExtras verhindert die uebrige GKG-Verarbeitung nicht
- ein GKG-Titel wird idempotent dem normalisierten Artikel zugeordnet
- Konflikte zwischen mehreren GKG-Titeln wechseln nicht stillschweigend die Quelle
- kein externer HTTP-Abruf ist fuer die Titelversorgung erforderlich
- Backfill und Neuimport erzeugen dasselbe Ergebnis
- Tests und Dokumentation belegen Normalisierung, Prioritaet und reale Abdeckung
```

## Abhaengigkeiten

ART-012 soll nach dieser Umsetzung den GDELT-basierten Titel ausgeben. ART-010 bleibt fuer einen
spaeteren, bedarfsgetriebenen Crawler zurueckgestellt. Das fruehere ART-008-Schema wurde durch V6
entfernt und ist keine Voraussetzung.
