# ART-014: Publikationszeit sauber modellieren

Status: erledigt
Bereich: articles, gdelt

## Kontext

`articles.first_seen_at` bezeichnet den fruehesten GDELT-Nachweis eines Artikels und bleibt als
Beobachtungszeitpunkt erhalten. Er ist kein verlaesslicher Publikationszeitpunkt. In GKG-Feld 27
kommt optional `PAGE_PRECISEPUBTIMESTAMP` vor und ist der derzeit beste Kandidat fuer ein eigenes
`publishedAt`.

## Ziel

Der Publikationszeitpunkt wird anhand realer GKG-Daten fachlich validiert, separat vom
Beobachtungszeitpunkt gespeichert und mit transparenter Quelle ueber die Article API ausgegeben.

## Umfang

```text
- Abdeckung, Format, Zeitzonenannahme und Plausibilitaet von PAGE_PRECISEPUBTIMESTAMP messen
- Werte mit document_date, source_timestamp und first_seen_at vergleichen
- ungueltige und offensichtlich unplausible Werte tolerant behandeln
- published_at und published_at_source nullable am Artikel persistieren
- PAGE_PRECISEPUBTIMESTAMP robust und idempotent aus GKG-Feld 27 extrahieren
- Konfliktregel fuer mehrere GKG-Signale desselben Artikels definieren
- vorhandene Raw-/Staging-Daten kontrolliert nachziehen
- ArticleSummary, ArticleDetail und REST-Responses erweitern
- Parser-, Staging-, Extractor-, Query-, Controller- und Contract-Tests
- Postman-Collection und Postman-Tests aktualisieren
- Quellenmatrix und Analyseergebnis dokumentieren
```

## Fachliche Regeln

```text
- firstSeenAt bleibt unveraendert der erste GDELT-Nachweis
- Signal-, Mention-, Dokument- und Importzeiten werden nicht pauschal als publishedAt verwendet
- publishedAtSource benennt die tatsaechlich verwendete Quelle, initial GKG_PAGE_PRECISE_PUB_TIMESTAMP
- fehlende oder verworfene Werte ergeben publishedAt = null und publishedAtSource = null
- Backfill und Neuimport muessen dasselbe Ergebnis erzeugen
```

## Akzeptanzkriterien

```text
- Analyse nennt Stichprobengroesse, Abdeckung, Formatvarianten und Plausibilitaetsgrenzen
- gueltige PAGE_PRECISEPUBTIMESTAMP-Werte werden als Instant gespeichert und ausgegeben
- ungueltige Werte verhindern weder GKG-Staging noch Artikel-Extraktion
- Artikel ohne Publikationszeit bleiben abrufbar und liefern null
- mehrere GKG-Signale fuehren zu einem deterministischen Ergebnis
- firstSeenAt und bestehende Sortierung bleiben zunaechst unveraendert
- Migration und Backfill sind idempotent
- bestehende Pagination, Signale und Statuscodes bleiben unveraendert
- Postman-Collection ist aktualisiert und valides JSON
```

## Abgrenzung

Die Standardsortierung von `GET /articles` wird in diesem Ticket noch nicht auf `publishedAt`
umgestellt. Eine solche Aenderung benoetigt nach der Abdeckungsanalyse eine eigene API-Entscheidung.

## Implementierungskommentar

Implementiert am 2026-07-13:

- Die reale Stichprobe umfasst 17.232 GKG-Staging-Zeilen. 9.148 Zeilen (53,09 Prozent) enthalten
  `PAGE_PRECISEPUBTIMESTAMP`; alle vorhandenen Werte verwenden das 14-stellige
  `YYYYMMDDHHMMSS`-Format. Analyse und reproduzierbare SQL-Abfrage liegen unter `docs/analysis`.
- Der Parser interpretiert kalendarisch gueltige Werte gemaess GDELT als UTC. Fehlende, defekte
  oder mehr als 15 Minuten nach `document_date` liegende Werte werden tolerant als `null`
  behandelt. In der Stichprobe wurden 8.860 Werte akzeptiert und 288 klare Zukunftswerte verworfen.
- Migration V12 ergaenzt Staging und `gdelt_gkg_records` und zieht bestehende Raw-Daten mit
  demselben Parser nach, den auch Neuimporte verwenden.
- Entsprechend der Modellentscheidung aus ART-018 bleibt der Kandidat am verursachenden GKG-Record,
  statt als zweite Wahrheit nach `articles` kopiert zu werden. Summary und Detail projizieren den
  Wert des fruehesten gueltigen GKG-Records nach `source_timestamp` und `id` als `publishedAt` mit
  `publishedAtSource = GKG_PAGE_PRECISE_PUB_TIMESTAMP`.
- Parser-, Staging-, Migrations-, Extractor-, Query-, Controller- und Contract-Tests decken
  nullable Werte, ungueltige Kalenderdaten, Zukunftswerte, Backfill, Neuimport und konkurrierende
  GKG-Records ab. Postman-Vertrag und Quellenmatrix wurden aktualisiert.
- `firstSeenAt`, Pagination, Standardsortierung, Detailsignale und Statuscodes bleiben unveraendert.
