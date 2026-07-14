# ART-017: GKG-Hauptbild am Artikel bereitstellen

Status: erledigt
Bereich: articles, gdelt

## Kontext

GKG 2.1 liefert Bildmetadaten in eigenen TSV-Feldern, die der aktuelle Staging-Parser noch nicht
auswertet. In der lokalen Stichprobe von 11.515 GKG-Rohzeilen war `V2.1SHARINGIMAGE` in 10.271
Zeilen vorhanden (89,2 Prozent). `V2.1RELATEDIMAGES` war in 1.722 und
`V2.1SOCIALIMAGEEMBEDS` in 683 Zeilen vorhanden.

`V2.1SHARINGIMAGE` ist der vom Publisher fuer Social Sharing bestimmte Bildkandidat und damit die
geeignetste initiale GDELT-Quelle fuer ein Artikelhauptbild. Manche Publisher verwenden dort
allerdings Logos oder andere generische Bilder; das Feld darf deshalb nicht ungeprueft als
qualitativ garantiert betrachtet werden.

## Ziel

Ein geeigneter GKG-Sharing-Image-Kandidat wird ohne externen Seitenabruf robust, idempotent und mit
transparenter Quelle am Artikel gespeichert und ueber die Article API ausgegeben.

## Umfang

```text
- Abdeckung, URL-Formate, Domains und erkennbare Logo-/Placeholder-Faelle reproduzierbar analysieren
- V2.1SHARINGIMAGE aus GKG-Spalte 19 parsen und im Staging persistieren
- nur syntaktisch gueltige absolute HTTP-/HTTPS-URLs akzeptieren
- Normalisierung und maximales Laengenlimit definieren
- main_image_url und main_image_source nullable am Artikel persistieren
- deterministische Konfliktregel fuer mehrere GKG-Signale desselben Artikels
- vorhandene Raw-/Staging-Daten kontrolliert und idempotent nachziehen
- ArticleSummary, ArticleDetail und REST-Responses erweitern
- Parser-, Staging-, Extractor-, Backfill-, Query-, Controller- und Contract-Tests
- Postman-Collection und Postman-Tests aktualisieren
- Quellenmatrix und Analyseergebnis dokumentieren
```

## Fachliche Regeln

```text
- mainImageSource ist initial GKG_SHARING_IMAGE
- fehlende oder verworfene Kandidaten ergeben mainImageUrl = null und mainImageSource = null
- ein spaeterer Kandidat ersetzt keinen bestehenden Wert ohne dokumentierte Qualitaetsregel
- Backfill und Neuimport erzeugen dasselbe Ergebnis
- die Anwendung laedt oder spiegelt die Bilddatei in diesem Ticket nicht
- eine Bild-URL ist externe, nicht dauerhaft garantierte Referenz
```

## Akzeptanzkriterien

```text
- Analyse nennt Stichprobengroesse, Abdeckung und dokumentierte Ausschlussregeln
- gueltige Sharing-Image-URLs werden dem normalisierten Artikel zugeordnet
- ungueltige oder nicht unterstuetzte URLs blockieren weder GKG-Staging noch Artikel-Extraktion
- Artikel ohne Bild bleiben abrufbar und liefern null
- mehrere GKG-Signale fuehren zu einem deterministischen Ergebnis
- Migration und Backfill sind idempotent
- Listen- und Detailendpunkt liefern mainImageUrl und mainImageSource nullable aus
- Pagination, Sortierung, Signale und bestehende Statuscodes bleiben unveraendert
- Postman-Collection ist aktualisiert und valides JSON
```

## Abgrenzung

`V2.1RELATEDIMAGES`, Social-Media-Embeds, Visual-GKG-Annotationen, Bild-Proxying, lokales Caching,
Copyright-/Lizenzbewertung und externe Webseitenabrufe sind nicht Teil dieses Tickets. Falls die
Qualitaetsanalyse zeigt, dass `V2.1SHARINGIMAGE` zu viele Logos oder Platzhalter enthaelt, wird vor
der Persistenz eine begruendete Folgeentscheidung dokumentiert.

## Implementierungskommentar

Implementiert am 2026-07-14:

- Die reproduzierbare lokale Stichprobe umfasst 32.072 GKG-Rohzeilen. Feld 19 war in 28.116 Zeilen
  vorhanden (87,7 Prozent); 27.343 Kandidaten verwendeten HTTPS und 773 HTTP. Nach der
  URI-Validierung wurden 28.108 Kandidaten akzeptiert. Acht Kandidaten wurden wegen ungueltiger
  URI-Syntax verworfen, davon sieben mit einer eckigen Klammer und einer mit Whitespace.
- Die akzeptierten URLs waren 27 bis 594 Zeichen lang (Median 108). Die zehn haeufigsten Hosts
  begannen mit `i.iheart.com` (3.232), `image.chitra.live` (1.129), `townsquare.media` (812),
  `bloximages.chicago2.vip.townnews.com` (754) und `npr-brightspot.s3.amazonaws.com` (550).
  Zwoelf Rohkandidaten enthielten auffaellige Begriffe wie `logo`, `placeholder`, `default-image`
  oder `no-image`. Sie werden nicht heuristisch verworfen, weil ein Dateiname allein keine
  verlaessliche Qualitaetsentscheidung erlaubt; `mainImageSource` macht die begrenzte
  GKG-Qualitaet transparent.
- `GdeltGkgParser` liest `V2.1SHARINGIMAGE` aus der nullbasierten Spalte 18. Die Normalisierung
  trimmt aeusseren Whitespace und akzeptiert ausschliesslich syntaktisch gueltige absolute HTTP-
  oder HTTPS-URIs mit Authority und maximal 2.048 Zeichen. Ungueltige optionale Werte blockieren
  das Staging nicht.
- Migration V13 fuegt `sharing_image_url` im Staging sowie `main_image_url` und
  `main_image_source` am dauerhaften GKG-Record hinzu und zieht vorhandene Raw-/Staging-Daten mit
  demselben Parser deterministisch nach. Ein Constraint erlaubt nur das konsistente Paar aus URL
  und `GKG_SHARING_IMAGE` beziehungsweise zwei `null`-Werten.
- Entsprechend der Modellentscheidung aus ART-018 liegt das Bild am verursachenden GKG-Record und
  nicht als zweite Wahrheit in `articles`. ArticleSummary und ArticleDetail projizieren den
  fruehesten gueltigen Kandidaten nach `source_timestamp` und Record-ID. Spaetere Kandidaten
  ersetzen ihn nicht; Artikel ohne Bild liefern fuer URL und Quelle `null`.
- Parser-, Staging-, Migrations-, Extractor-, Query-, Controller- und Contract-Tests decken
  gueltige, fehlende, ungueltige, ueberlange und konkurrierende Kandidaten sowie den Backfill ab.
  Die Postman-Collection prueft die beiden nullable Felder und deren Quellenkopplung.

Die Stichprobe ist auf einer lokalen Datenbank nach V13 mit folgender Abfrage reproduzierbar:

```sql
WITH images AS (
    SELECT NULLIF(BTRIM(split_part(raw_tsv, chr(9), 19)), '') AS raw_url
    FROM gdelt_raw_gkg
)
SELECT COUNT(*) AS total,
       COUNT(raw_url) AS present,
       COUNT(*) FILTER (WHERE raw_url ~* '^https://') AS https,
       COUNT(*) FILTER (WHERE raw_url ~* '^http://') AS http,
       COUNT(*) FILTER (WHERE length(raw_url) > 2048) AS too_long,
       COUNT(*) FILTER (
           WHERE raw_url ~* '(logo|placeholder|default[-_]?image|no[-_]?image)'
       ) AS suspicious_name
FROM images;
```
