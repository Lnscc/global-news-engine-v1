# Globale Ereignis- und Analyseplattform - Architektur

Diese Datei ist der Einstiegspunkt fuer die Architekturplanung. Die Details sind in kleinere Dokumente aufgeteilt, damit die Planung uebersichtlich bleibt.

Weitere Planungsdokumente:

```text
- articles.md: Artikel-Schicht zwischen GDELT-Staging und Stories
```

## Zielbild

Die Plattform soll globale Ereignisse analysieren, clustern und visuell darstellen.
Im Mittelpunkt stehen nicht einzelne GDELT-Events, sondern die automatische Bildung von:

```text
Artikel -> Stories -> Topics -> Themes
```

Grundprinzip:

```text
GDELT ist Input.
Artikel sind Wissen.
Stories sind Bedeutung.
Topics sind Kontext.
Themes sind strategische Analyse.
```

## Wichtigste Architekturentscheidung

Nicht Event-first bauen.

```text
GDELT liefert Signale
-> Artikel liefern Inhalt
-> Stories liefern Bedeutung
-> Topics liefern Kontext
-> Themes liefern Langzeitanalyse
```

Events, Mentions und GKG bleiben wichtig, aber sie sind nicht das zentrale Produktobjekt. Das zentrale Produktobjekt ist die Story.
