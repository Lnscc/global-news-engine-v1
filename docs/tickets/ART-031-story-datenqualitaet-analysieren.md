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
Story-Clustering mit Titel-Embeddings und benennt belastbare Features, Qualitaetsrisiken, ein
geeignetes Embedding-Modell und konkreten Datenbedarf.

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
- einen deterministischen Titel-Eingabetext und dessen Hash fuer Embeddings definieren
- mindestens ein fuer die vorhandenen Sprachen geeignetes Embedding-Modell auf der Stichprobe
  reproduzierbar untersuchen; Modellkennung, Version, Dimension und Ausfuehrungsumgebung festhalten
- Cosine-Similarity-Verteilungen fuer erkennbare positive Paare, harte Negativpaare und
  mehrdeutige Faelle getrennt ausweisen
- Recall der positiven Kandidaten und Kandidatenmengengroesse fuer sinnvolle Kombinationen aus
  Zeitfenster und Top-k-Nachbarn messen
- Laufzeit, Batch-Groesse, Fehlerrate und geschaetzte Kosten der Embedding-Erzeugung dokumentieren
- Analyseabfragen oder ein kleines wiederholbar ausfuehrbares Analyseskript bereitstellen
```

## Analysegrundsaetze

```text
- keine Story-Wahrheit allein aus globalEventId, Theme oder Domain ableiten
- keine Story-Wahrheit allein aus Embedding-Aehnlichkeit oder einem ungeprueften Schwellwert ableiten
- keine fehlenden Werte aus TLD, Publisher oder anderen Heuristiken erfinden
- Embeddings nur aus dem normalisierten Titel bilden; keine Entitaeten, GDELT-Themes oder
  generierten Texte verdeckt in den Eingabetext mischen
- Modell und Eingabetext so protokollieren, dass jede gemessene Aehnlichkeit reproduzierbar ist
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
- Titel-Eingabetext, Normalisierung, Hashbildung, Embedding-Modell, Modellversion und Dimension
  sind eindeutig dokumentiert
- typische Spaetankunft und zeitliche Streuung sind quantifiziert
- Kandidatenmengen fuer mehrere sinnvolle Zeitfenster sind abgeschaetzt
- mindestens je zehn erkennbare positive Kandidaten und harte Negativfaelle sind beschrieben
- fuer diese Kandidaten sind Similarity-Verteilungen sowie Recall-at-k fuer mindestens zwei
  sinnvolle Zeitfenster ausgewiesen
- Verhalten bei unterschiedlichen Sprachen, sehr kurzen, generischen und nahezu identischen
  Titeln ist anhand konkreter Beispiele bewertet
- Features sind begruendet als primaer, unterstuetzend oder fuer das MVP ungeeignet eingeordnet
- genau ein Embedding-Modell wird fuer ART-032 empfohlen oder die gemessene Blockade mit einem
  quantitativen Mindestkriterium fuer eine erneute Auswahl belegt
- Durchsatz, Fehler und Kosten sind so quantifiziert, dass ART-033 Batch- und Retry-Regeln festlegen kann
- Datenluecken werden einem konkreten Folgeschritt zugeordnet, ohne ART-010 automatisch zu aktivieren
- die Ergebnisse liefern ein begruendetes Sampling fuer ART-032
- es erfolgen keine Aenderungen am produktiven Datenbankschema oder REST-Vertrag
```

## Abhaengigkeiten

Das Ticket baut auf ART-030 auf. Die dauerhaften GDELT-Fachmodelle und direkten
`article_id`-Zuordnungen aus ART-023 bis ART-027 sind bereits vorhanden.

## Abgrenzung

Persistentes Story-Modell, produktiver Clusterer, produktive Embedding-Persistenz oder
Vektorsuche, Sprachklassifikation, Volltext und externe Seitenabrufe sind nicht Teil dieses
Tickets. Experimentell erzeugte Titel-Embeddings und lokale Analyseartefakte sind ausdruecklich
Teil der Analyse.
