# ART-031: Story-relevante Artikeldaten analysieren

Status: offen
Bereich: stories, articles, gdelt

## Kontext

Die Artikel-Schicht projiziert Titel, Publikationszeit und Bild aus GKG und stellt EVENTS-,
MENTIONS- und GKG-Signale bereit. Fuer Story-Clustering ist noch nicht gemessen, wie vollstaendig,
stabil und unterscheidungskraeftig diese Merkmale in real importierten Daten sind.

Ein Algorithmus darf nicht auf Feldern geplant werden, die nur selten vorliegen, zu spaet eintreffen
oder bei unterschiedlichen Ereignissen regelmaessig gleich aussehen.

## Ziel

Eine reproduzierbare Analyse bewertet die vorhandenen Artikeldaten als Eingang fuer ein erstes
Story-Clustering und benennt belastbare Features, Qualitaetsrisiken und konkreten Datenbedarf.

## Umfang

```text
- eine reproduzierbare Stichprobe aus real importierten Daten festlegen
- Titelabdeckung, Leerwerte, Duplikate und generische Titel messen
- publishedAt- und firstSeenAt-Abdeckung sowie zeitliche Abweichungen untersuchen
- Signalabdeckung je Artikel fuer EVENTS, MENTIONS und GKG messen
- Anzahl und Stabilitaet von globalEventId und eventCode je Artikel untersuchen
- Themes, Personen, Organisationen und Orte auf Abdeckung und Trennschaerfe pruefen
- spaet eintreffende Signale und nachtraeglich verfuegbare GKG-Metadaten messen
- Artikelvolumen pro Zeitfenster als Grundlage fuer Kandidatensuche bestimmen
- typische positive Kandidaten und harte Negativfaelle dokumentieren
- Analyseabfragen oder ein kleines wiederholbar ausfuehrbares Analyseskript bereitstellen
```

## Analysegrundsaetze

```text
- keine Story-Wahrheit allein aus globalEventId, Theme oder Domain ableiten
- keine fehlenden Werte aus TLD, Publisher oder anderen Heuristiken erfinden
- Ergebnisse nach Zeitraum und relevanten Quelltypen aufschluesseln
- Rohzahlen und Prozentwerte gemeinsam dokumentieren
- verwendeten Datenstand und Stichprobenverfahren nachvollziehbar festhalten
- keine externen Webseiten abrufen
```

## Akzeptanzkriterien

```text
- die Analyse ist mit dokumentierten Schritten auf einem lokalen Datenbestand wiederholbar
- Stichprobe, Zeitraum, Groesse und Auswahlverfahren sind dokumentiert
- Titel, Zeitstempel und alle vorhandenen strukturierten Signalsorten besitzen Abdeckungswerte
- typische Spaetankunft und zeitliche Streuung sind quantifiziert
- Kandidatenmengen fuer mehrere sinnvolle Zeitfenster sind abgeschaetzt
- mindestens je zehn erkennbare positive Kandidaten und harte Negativfaelle sind beschrieben
- Features sind begruendet als primaer, unterstuetzend oder fuer das MVP ungeeignet eingeordnet
- Datenluecken werden einem konkreten Folgeschritt zugeordnet, ohne ART-010 automatisch zu aktivieren
- die Ergebnisse liefern ein begruendetes Sampling fuer ART-032
- es erfolgen keine Aenderungen am produktiven Datenbankschema oder REST-Vertrag
```

## Abhaengigkeiten

Das Ticket baut auf ART-030 auf. Die dauerhaften GDELT-Fachmodelle und direkten
`article_id`-Zuordnungen aus ART-023 bis ART-027 sind bereits vorhanden.

## Abgrenzung

Persistentes Story-Modell, produktiver Clusterer, Embeddings, Sprachklassifikation, Volltext und
externe Seitenabrufe sind nicht Teil dieses Tickets.
