# ART-032: Gelabeltes Story-Evaluationskorpus erstellen

Status: offen
Bereich: stories, architecture

## Kontext

Ohne eine unabhaengige Sollzuordnung kann die Qualitaet eines Story-Clusterers nicht objektiv
bewertet werden. Rein anschauliche Beispielcluster reichen nicht aus, weil insbesondere harte
Negativfaelle wie dieselben Personen bei unterschiedlichen Ereignissen leicht uebersehen werden.

Das Korpus soll klein genug fuer eine sorgfaeltige manuelle Pruefung, aber breit genug fuer einen
vergleichbaren Baseline-Test sein. Es speichert keinen Artikelvolltext.

## Ziel

Ein versioniertes und nachvollziehbar gelabeltes Referenzkorpus ermoeglicht, verschiedene
Clustering-Ansaetze reproduzierbar gegen dieselben positiven, negativen und mehrdeutigen Faelle zu
bewerten.

## Umfang

```text
- maschinenlesbares Format und Schema fuer Artikelreferenzen und Labels definieren
- stabile Referenzen statt lokaler, datenbankabhaengiger IDs verwenden
- Artikelpaare als SAME_STORY, DIFFERENT_STORY oder UNCERTAIN labeln
- zusammengehoerige Mehrartikelgruppen mit stabilen Referenz-Story-IDs erfassen
- Begruendung und relevante Grenzfallkategorie je manueller Entscheidung dokumentieren
- mehrere Domains, Tage, Signalabdeckungen und Ereignisarten einbeziehen
- harte Negativfaelle gezielt aufnehmen
- harte Negativfaelle mit hoher Embedding-Aehnlichkeit und positive Paare mit niedriger
  Embedding-Aehnlichkeit gezielt aufnehmen
- Regeln fuer Korrekturen und Versionierung des Korpus dokumentieren
- einfache Auswertung fuer Pairwise Precision, Recall und F1 vorbereiten
- eine lexikalische Titel-Zeit-Baseline mit der in ART-031 empfohlenen Embedding-Zeit-Baseline
  unter denselben Labels vergleichen
- Kalibrierungs- und Evaluationsdaten trennen und den Similarity-Schwellwert vor der finalen
  Evaluation einfrieren
```

## Mindestumfang

```text
- mindestens 100 eindeutig gelabelte Artikelpaare
- davon mindestens 25 SAME_STORY-Paare
- davon mindestens 25 harte DIFFERENT_STORY-Paare
- mindestens zehn Referenz-Stories mit jeweils mindestens drei Artikeln
- UNCERTAIN-Faelle werden separat gefuehrt und nicht stillschweigend als negativ gewertet
```

## Datenschutz und Reproduzierbarkeit

```text
- keine Artikelvolltexte in das Repository kopieren
- nur fuer die Bewertung erforderliche Metadaten und Quellreferenzen speichern
- Auswahlzeitraum und Herkunft der Daten dokumentieren
- lokale Datenbank-IDs duerfen nicht die einzige Artikelidentitaet sein
- nicht mehr erreichbare URLs duerfen die bereits dokumentierte Sollentscheidung nicht unerklaerbar machen
- Embedding-Modell, Modellversion, Titel-Normalisierung und Eingabe-Hash werden mit der Auswertung
  versioniert; Embedding-Vektoren selbst muessen nicht im Repository liegen
```

## Akzeptanzkriterien

```text
- das Korpus erfuellt den dokumentierten Mindestumfang
- jedes eindeutige Label besitzt eine kurze nachvollziehbare Begruendung
- die Grenzfaelle aus ART-030 sind vertreten oder ihr Fehlen ist begruendet
- das Sampling beruecksichtigt die Messwerte und Risiken aus ART-031
- Labelschema, Dateiformat und Versionierungsregel sind dokumentiert
- ein automatisierter Auswertungspfad kann Vorhersagen gegen die Referenzlabels vergleichen
- Pairwise Precision, Recall und F1 sind eindeutig definiert
- die Auswertung weist zusaetzlich Kandidaten-Recall, Fehl-Merges und Singleton-Anteil aus
- Kalibrierungs- und Evaluationsmenge sind ohne Paar- oder Story-Leakage getrennt; auf der
  Evaluationsmenge werden weder Similarity-Schwellwert noch Zeitfenster nachjustiert
- lexikalische und Embedding-Baseline verwenden dieselben Evaluationsartikel und dokumentieren
  Modellversion, Eingabe-Hash, Zeitfenster, Top-k und Entscheidungsregel
- das Embedding-MVP besitzt vor Implementierungsbeginn einen eingefrorenen Schwellwert oder eine
  eingefrorene deterministische Entscheidungsregel mit dokumentiertem Qualitaetsergebnis
- UNCERTAIN-Faelle beeinflussen die Kernmetriken nicht ohne explizite Entscheidung
- es erfolgen keine Aenderungen am produktiven Datenbankschema oder REST-Vertrag
```

## Abhaengigkeiten

Das Ticket baut auf der Story-Definition aus ART-030 und dem Sampling- und Datenqualitaetsergebnis
aus ART-031 auf.

## Abgrenzung

Produktives Clustering, Story-Persistenz, ein allgemeines Annotationstool und die automatische
Erzeugung von Ground Truth sind nicht Teil dieses Tickets.
