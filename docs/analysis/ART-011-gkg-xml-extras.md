# Analyse: GKG-Feld 27 als Quelle fuer Artikelmetadaten

## Anlass

Bei der Planung des Article-Enrichment-Crawlers wurde zunaechst angenommen, dass die importierten
GDELT-Daten keinen belastbaren Artikeltitel enthalten. Eine Pruefung der vollstaendigen Payload-Zeilen
hat diese Annahme widerlegt.

## Technischer Befund

`gdelt_gkg_payloads.raw_tsv` speichert jede heruntergeladene GKG-Zeile unverkuerzt. In der untersuchten
lokalen Datenbank hatten alle 3.235 Zeilen genau 27 TSV-Felder. Feld 27 ist `V2EXTRASXML` und war in
allen Zeilen befuellt. 3.233 Zeilen enthielten einen nicht leeren `<PAGE_TITLE>`-Block; das entspricht
einer Abdeckung von rund 99,94 Prozent.

Beobachtete Inhalte waren unter anderem:

```xml
<PAGE_TITLE>ENWO hosts awareness prog on child protection</PAGE_TITLE>

<PAGE_PRECISEPUBTIMESTAMP>20260711181500</PAGE_PRECISEPUBTIMESTAMP>
<PAGE_TITLE>Typhoon Bavi makes landfall in east China with more than one million evacuated</PAGE_TITLE>
```

Weitere beobachtete Tags sind `PAGE_AUTHORS`, `PAGE_LINKS` sowie alternative AMP- oder Mobile-URLs.
Das Feld ist eine Erweiterungsstruktur; einzelne Tags sind optional und muessen unabhaengig
voneinander behandelt werden.

## Vergleich mit gecrawlten Titeln

Fuer 1.564 erfolgreich gecrawlte und per URL vergleichbare Artikel waren 939 Titel exakt gleich
und 941 bei ignorierter Gross-/Kleinschreibung gleich. Abweichungen hatten mehrere Ursachen:

```text
- GDELT kodiert Nicht-ASCII-Zeichen teilweise als HTML-Entities
- Webseiten fuegen Marken- oder Domain-Suffixe hinzu
- Ueberschriften koennen nach der GDELT-Erfassung geaendert werden
- der Crawler kann statt des Artikels eine Consent- oder Datenschutzeinstellungsseite erhalten
```

Beispiele:

```text
GDELT:   First Rain Exposes Flaws In &#x20B9;28 Lakh ...
Crawler: First Rain Exposes Flaws In ₹28 Lakh ...

GDELT:   Which Is the Better Small-Cap ETF, Schwab's SCHA or iShares' IJR?
Crawler: Ihre Datenschutzeinstellungen
```

## Schlussfolgerung

`PAGE_TITLE` ist fuer aktuelle GKG-Daten die primaere und nahezu vollstaendige Titelquelle. Vor der
Persistenz muessen HTML-Entities dekodiert und Leerwerte normalisiert werden. Der Titel rechtfertigt
keinen externen Crawl. Ein spaeterer Crawler bleibt nur fuer nicht ausreichend abgedeckte Felder wie
Volltext, Hauptbild und Sprache sowie fuer gezielte Qualitaetsvalidierung sinnvoll.

`PAGE_PRECISEPUBTIMESTAMP` ist ein aussichtsreicher Kandidat fuer `publishedAt`, muss aber vor einer
Nutzung separat auf Abdeckung und Semantik untersucht werden. GDELT-Signal- und Dokumentzeitpunkte
duerfen weiterhin nicht pauschal als Publikationszeitpunkt verwendet werden.

## Reproduzierbare SQL-Pruefungen

```sql
SELECT array_length(string_to_array(raw_tsv, chr(9)), 1) AS column_count, count(*)
FROM gdelt_gkg_payloads
GROUP BY 1;

SELECT count(*) AS total,
       count(*) FILTER (
           WHERE nullif(substring(split_part(raw_tsv, chr(9), 27)
                                  FROM '<PAGE_TITLE>(.*?)</PAGE_TITLE>'), '') IS NOT NULL
       ) AS title_present
FROM gdelt_gkg_payloads;
```

Die Zahlen sind eine Momentaufnahme der lokalen Datenbank und keine Garantie fuer historische oder
spezielle GKG-Sammlungen. Parser und Datenmodell muessen fehlende Tags weiterhin tolerieren.
