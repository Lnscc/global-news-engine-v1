# ART-013: Artikelsuche und Filter

Status: erledigt
Bereich: articles

## Kontext

`GET /articles` liefert bisher ausschliesslich die neuesten Artikel mit Offset-Pagination. Fuer
gezielte Abfragen fehlen Suche und Filter, obwohl Domain, Titel, Beobachtungszeit und Signale
bereits vorhanden sind.

## Ziel

Die bestehende Artikelliste kann ueber optionale Query-Parameter durchsucht und gefiltert werden.
Ohne neue Parameter bleibt ihr bisheriges Verhalten unveraendert.

## Umfang

```text
- case-insensitive Titelsuche ueber q
- Filter nach domain
- Zeitraumfilter ueber firstSeenFrom und firstSeenTo
- Filter nach theme als exakter Eintrag in der semikolongetrennten Theme-Liste
- Filter nach signalType
- explizite Sortierrichtung ueber direction=asc|desc
- alle Filter sind optional und werden mit AND kombiniert
- Offset-/Limit-Pagination und stabiles Tie-Breaking ueber die Artikel-ID beibehalten
- Query-Service-, Controller- und Contract-Tests
- Postman-Collection und Postman-Tests aktualisieren
```

## API-Vertrag

```text
GET /articles?q={text}
             &domain={domain}
             &firstSeenFrom={ISO-8601 instant}
             &firstSeenTo={ISO-8601 instant}
             &theme={theme}
             &signalType={EVENTS|MENTIONS|GKG}
             &direction={asc|desc}
             &offset={offset}
             &limit={limit}
```

`firstSeenFrom` ist inklusiv, `firstSeenTo` ist exklusiv. `direction` bezieht sich auf
`firstSeenAt` und ist standardmaessig `desc`. Der Zeitraum beschreibt weiterhin den ersten
GDELT-Nachweis und nicht den noch separat zu modellierenden Publikationszeitpunkt.

## Akzeptanzkriterien

```text
- jeder Filter funktioniert einzeln und in Kombination
- q sucht ohne Beachtung der Gross-/Kleinschreibung nur in vorhandenen Titeln
- theme matched einen vollstaendigen Theme-Eintrag und keine Teilzeichenfolge
- signalType erzeugt durch mehrere passende Signale keine doppelten Artikel
- total zaehlt die gefilterte Gesamtmenge vor der Pagination
- Sortierung ist fuer gleiche firstSeenAt-Werte ueber id stabil
- unbekannte oder ungueltige Parameterwerte liefern 400 mit dem bestehenden Fehlervertrag
- ein Aufruf ohne neue Parameter verhaelt sich wie der bisherige GET /articles
- Pagination, Detailabruf und bestehende Statuscodes bleiben unveraendert
- Postman-Collection ist aktualisiert und valides JSON
```

## Abgrenzung

Publikationszeit, Sprache, Autoren, Publisher-Metadaten, Volltextsuche und Cursor-Pagination sind
nicht Teil dieses Tickets. `q` ist zunaechst eine Titelsuche und fuehrt keinen externen Abruf aus.

## Implementierungskommentar

`GET /articles` unterstuetzt nun die optionalen Parameter `q`, `domain`, `firstSeenFrom`,
`firstSeenTo`, `theme`, `signalType` und `direction`. Die Filter werden im Query-Service mit AND
kombiniert; Signal- und Theme-Filter verwenden korrelierte `EXISTS`-Abfragen, sodass ein Artikel
auch bei mehreren passenden Signalen nur einmal erscheint. Anzahl, Offset-/Limit-Pagination und
die stabile Sortierung nach `firstSeenAt` und `id` verwenden dieselben Filter. Ungueltige Werte
und unbekannte Parameter liefern den bestehenden `invalid_request`-Fehlervertrag. Controller-,
Query-Service- und Postman-Tests decken den erweiterten Vertrag ab.
