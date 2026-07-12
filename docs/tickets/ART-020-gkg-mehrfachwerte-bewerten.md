# ART-020: Weitere GKG-Mehrfachwerte bewerten

Status: offen
Bereich: architecture, gdelt
Prioritaet: zurueckgestellt

## Kontext

GKG liefert neben Themes auch Personen, Organisationen und strukturierte Orte als Mehrfachwerte.
Eigene Tabellen erzeugen zusaetzliche Importlogik und Joins. Solange keine konkreten Abfragen
darauf existieren, reicht der unveraenderte Rohwert am GKG-Record.

## Festgehaltene Entscheidung

```text
- persons_raw, organizations_raw und locations_raw bleiben zunaechst am GKG-Record
- Normalisierung erfolgt feldweise und nur fuer einen belegten Anwendungsfall
- Raw-Werte bleiben auch nach einer spaeteren Normalisierung als Provenienz erhalten
- es werden nicht vorsorglich drei weitere Detailtabellen eingefuehrt
```

## Ziel

Fuer Personen, Organisationen und Orte wird jeweils anhand konkreter Produktabfragen entschieden,
ob und in welcher Struktur eine Normalisierung erforderlich ist.

## Zu pruefende Fragen

```text
- Welche API-Suche, Filterung oder Aggregation benoetigt das Feld?
- Muss ein einzelner Wert indexiert werden?
- Welche Bestandteile liefert GDELT und welche davon haben stabile Semantik?
- Wie werden Reihenfolge, Duplikate, leere Werte und Parsingfehler behandelt?
- Brauchen Orte eine strukturierte Tabelle fuer Land, Koordinaten und Feature-ID?
- Rechtfertigt der Nutzen die zusaetzlichen Tabellen und Joins?
```

## Akzeptanzkriterien

```text
- fuer jedes Feld ist behalten oder normalisieren begruendet entschieden
- eine Normalisierung besitzt einen benannten Abfrage- oder Produktbedarf
- Zielstruktur und Parsingregeln sind vor einer Implementierung dokumentiert
- ohne belegten Bedarf bleibt das jeweilige Feld ausschliesslich als Raw-Wert erhalten
```

## Abgrenzung

Dieses Ticket fuehrt selbst keine Detailtabellen, API-Filter, Geocoding-Dienste oder semantische
Entitaetsaufloesung ein.
