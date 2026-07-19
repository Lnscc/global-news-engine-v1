# ART-029: Redundante GKG-Rohspalten aus dem Fachmodell entfernen

Status: erledigt
Bereich: gdelt, architecture

## Kontext

ART-020 hat `themes_raw`, `persons_raw`, `organizations_raw`, `locations_raw` und `tone_raw`
nach der Normalisierung bewusst aus dem damaligen GKG-Produktmodell entfernt. Die unveraenderte
GDELT-Quellzeile lag weiterhin in der Raw- beziehungsweise spaeteren Payload-Tabelle.

Bei der Konsolidierung von Staging- und Produktmodell durch ART-025 wurden die fuenf Felder mit
Flyway V18 erneut als dauerhafte Spalten in `gdelt_gkg` aufgenommen. Der aktuelle Parser schreibt
deshalb sowohl die urspruenglichen GDELT-Feldwerte als auch deren normalisierte Darstellungen:

```text
themes_raw          -> themes
persons_raw         -> persons
organizations_raw   -> organizations
locations_raw       -> locations
tone_raw            -> tone_value und weitere tone_*-Komponenten
```

Die Anwendung liest die `*_raw`-Spalten nach dem Anlegen einer GKG-Fachzeile nicht mehr. Suche,
Article API und Debug-Projektionen verwenden die normalisierten Felder. Die dauerhafte
Duplizierung widerspricht zudem dem Zielmodell, nach dem unveraenderte Quelldaten ausschliesslich
in `gdelt_gkg_payloads.raw_tsv` und erfolgreich geparste sowie normalisierte Werte in `gdelt_gkg`
liegen.

Nach Ablauf der Payload-Retention ist eine erneute Normalisierung aus der Quellzeile nicht mehr
moeglich. Das ist eine bewusste Folge der bestehenden Retention-Entscheidung und kein Grund, eine
unvollstaendige zweite Rohdatenkopie im Fachmodell dauerhaft vorzuhalten.

## Ziel

`gdelt_gkg` enthaelt nur noch die normalisierten fachlichen Darstellungen der fuenf betroffenen
GDELT-Felder. Neue Imports schreiben keine separaten `*_raw`-Werte mehr. Bestehende normalisierte
Werte und alle Artikelbeziehungen bleiben unveraendert erhalten.

## Zielmodell

Aus `gdelt_gkg` werden entfernt:

```text
- themes_raw
- persons_raw
- organizations_raw
- locations_raw
- tone_raw
```

Erhalten bleiben insbesondere:

```text
- themes TEXT[] NOT NULL
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

Die vollstaendige Quellzeile bleibt bis zum Ablauf der konfigurierten Retention in
`gdelt_gkg_payloads.raw_tsv` verfuegbar.

## Umfang

```text
- neue vorwaertsgerichtete Flyway-Migration zum Entfernen der fuenf Spalten anlegen
- bestehende historische Migration V18 unveraendert lassen
- GKG-Parsing und Persistierung so anpassen, dass neue Fachzeilen nur normalisierte Werte schreiben
- Migrations- und Transformer-Tests an das bereinigte Schema anpassen
- pruefen, dass bestehende normalisierte Werte, Metadaten und article_id-Beziehungen erhalten bleiben
- Dokumentation des Payload- und GKG-Fachmodells korrigieren
- feststellen und dokumentieren, dass der Article-REST-Contract unveraendert bleibt
```

## Akzeptanzkriterien

```text
- gdelt_gkg enthaelt nach allen Flyway-Migrationen keine der fuenf *_raw-Spalten
- Neuimporte erzeugen weiterhin vollstaendige normalisierte GKG-Fachzeilen
- Themes, Personen, Organisationen, Orte und alle Tone-Komponenten bleiben wertgleich erhalten
- vorhandene article_id-Beziehungen und GKG-Metadaten bleiben unveraendert
- Payload-Retention und Retry-Verhalten bleiben unveraendert
- V18 bleibt als historische Migration unveraendert und bestehende Datenbanken migrieren vorwaerts
- eine neue Datenbank erreicht ueber die vollstaendige Migrationskette dasselbe bereinigte Zielschema
- Article API, Suche, Filter, Pagination, Sortierung und Statuscodes bleiben unveraendert
- relevante Unit-, Migrations- und PostgreSQL-Integrationstests sind erfolgreich
- die Dokumentation beschreibt raw_tsv als einzige temporaere Rohdatenquelle
```

## Abhaengigkeiten

Das Ticket korrigiert eine durch ART-025 entstandene Regression gegen das in ART-019 und ART-020
etablierte normalisierte GKG-Modell. Es baut ausserdem auf der Payload-Retention aus ART-022 auf.

## Abgrenzung

Eine verlaengerte Retention, externe Rohdatenarchivierung, erneute Normalisierung bereits
geloeschter Payloads, Aenderungen der Normalisierungsregeln und Aenderungen am Article-REST-Contract
sind nicht Teil dieses Tickets.

## Implementierungskommentar

Migration V21 entfernt `themes_raw`, `persons_raw`, `organizations_raw`, `locations_raw` und
`tone_raw` vorwaertsgerichtet aus `gdelt_gkg`; die historische Migration V18 bleibt unveraendert.
Der aktive GKG-Transformer persistiert nur noch die normalisierten Arrays, das normalisierte
Orts-JSON und die einzelnen Tone-Komponenten. Migrationstests pruefen den Erhalt bestehender
normalisierter Werte und Artikelbeziehungen sowie das bereinigte Zielschema. Der PostgreSQL-
Pipeline-Test prueft Neuimport und Normalisierung direkt gegen das Schema ohne Rohspalten.
Dokumentation und Betriebshinweise beschreiben `gdelt_gkg_payloads.raw_tsv` als einzige temporaere
Rohdatenquelle. Der Article-REST-Contract und die Payload-Retention bleiben unveraendert.
