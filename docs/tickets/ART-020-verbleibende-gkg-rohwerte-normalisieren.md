# ART-020: Verbleibende GKG-Rohwerte normalisieren

Status: offen
Bereich: gdelt, articles

## Kontext

Nach ART-019 liegen Themes als normalisiertes Array direkt am GKG-Record. Personen,
Organisationen, strukturierte Orte und die vollstaendige Tone-Messung werden dagegen noch als
`persons_raw`, `organizations_raw`, `locations_raw` und `tone_raw` im Produktmodell gespeichert
und als Strings ueber die Article API ausgegeben.

Die GDELT-Quelle bleibt bereits in Raw und Staging erhalten. Eine zweite Rohkopie im dauerhaften
GKG-Record ist daher nicht erforderlich. Eine Stichprobe vom 2026-07-13 enthielt unter 7.712
Staging-Records 6.034 Records mit Personen, 5.736 mit Organisationen und 6.076 mit Orten.

## Ziel

Alle verbleibenden GKG-Rohwerte werden einmalig in ihre fachliche Zielstruktur normalisiert. Neue
Imports schreiben ausschliesslich die normalisierte Form nach `gdelt_gkg_records`; bestehende
Records werden kontrolliert nachgezogen. Die Article API liefert Arrays beziehungsweise typisierte
Tone-Felder statt Trennzeichen-Strings.

## Zielmodell

```text
gdelt_gkg_records
- persons TEXT[] NOT NULL
- organizations TEXT[] NOT NULL
- locations JSONB NOT NULL
- tone_value DOUBLE PRECISION
- tone_positive_score DOUBLE PRECISION
- tone_negative_score DOUBLE PRECISION
- tone_polarity DOUBLE PRECISION
- tone_activity_reference_density DOUBLE PRECISION
- tone_self_group_reference_density DOUBLE PRECISION
- tone_word_count INTEGER
```

`locations` ist eine geordnete JSON-Liste typisierter Objekte:

```json
{
  "type": 4,
  "name": "Exeter, Devon, United Kingdom",
  "countryCode": "UK",
  "adm1Code": "UKD4",
  "latitude": 50.7,
  "longitude": -3.53333,
  "featureId": "-2595805"
}
```

## Normalisierungsregeln

```text
- persons und organizations an Semikolon trennen
- Werte trimmen, leere Werte entfernen und exakte Duplikate beseitigen
- Reihenfolge des jeweils ersten Vorkommens erhalten
- locations an Semikolon und jeden Eintrag in genau sieben #-Felder zerlegen:
  type, name, countryCode, adm1Code, latitude, longitude, featureId
- leere Personen-, Organisations- und Ortswerte als leere Listen speichern, nicht als null
- latitude und longitude als nullable Zahlen innerhalb eines ansonsten gueltigen Orts behandeln
- fehlerhafte Einzelorte tolerant verwerfen und messbar machen; der GKG-Import darf nicht scheitern
- tone_raw in die sieben dokumentierten GKG-Tone-Komponenten zerlegen
- leere oder ungueltige Tone-Komponenten nullable behandeln; tone_word_count muss ganzzahlig sein
- Normalisierung bei Backfill und Neuimport muss bytegleich dieselben API-Werte erzeugen
```

## Umfang

```text
- reale Formate, Abdeckung und fehlerhafte Varianten reproduzierbar analysieren
- Parser und GKG-Extraktion fuer alle Zielstrukturen erweitern
- bestehende GKG-Records idempotent backfillen
- persons_raw, organizations_raw, locations_raw und tone_raw nach validiertem Backfill entfernen
- ArticleSignal und REST-Response auf Arrays, Ortsobjekte und typisierte Tone-Werte umstellen
- Debug-Views an das neue Modell anpassen
- Parser-, Migrations-, Backfill-, Extractor-, Query-, Controller- und Contract-Tests
- zufaellige Stichprobe und vollstaendigen Vergleich gegen Staging dokumentieren
- Postman-Collection und Postman-Tests aktualisieren und JSON validieren
```

## Akzeptanzkriterien

```text
- Personen und Organisationen werden geordnet, leerwertfrei und duplikatfrei ausgegeben
- Orte besitzen sieben eindeutig benannte Bestandteile und numerische Koordinaten
- ungueltige Einzelwerte blockieren weder Staging noch GKG-Extraktion
- Tone-Komponenten stimmen mit der GDELT-Felddefinition und realen Stichproben ueberein
- leere Mehrfachwerte erscheinen in der API als []
- das Produktmodell enthaelt keine Spalten persons_raw, organizations_raw, locations_raw oder tone_raw
- Backfill und Neuimport erzeugen identische normalisierte Werte
- der Gesamtvergleich aller vorhandenen Records gegen Staging weist keine unerklaerten Abweichungen auf
- Reihenfolge, Pagination, Artikelanzahl und bestehende Statuscodes bleiben unveraendert
- Postman-Collection ist aktualisiert und valides JSON
```

## Abhaengigkeit

Das Ticket baut auf dem in ART-019 etablierten Array- und API-Modell auf.

## Abgrenzung

Namensaufloesung, Personen- oder Organisations-IDs, Geocoding, Ortszusammenfuehrung,
Uebersetzungen und semantische Entitaetsbeziehungen sind nicht Teil dieses Tickets.
