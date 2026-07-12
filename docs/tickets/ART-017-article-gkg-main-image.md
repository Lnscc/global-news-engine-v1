# ART-017: GKG-Hauptbild am Artikel bereitstellen

Status: offen
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
