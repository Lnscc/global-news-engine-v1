# Vision und Prinzipien

## Projektvision

Die Plattform soll globale Ereignisse analysieren, clustern und visuell darstellen.
Im Mittelpunkt stehen nicht einzelne GDELT-Events, sondern die automatische Bildung von:

```text
Artikel -> Stories -> Topics -> Themes
```

Das Ziel ist eine skalierbare Intelligence- und Analyseplattform mit:

- Echtzeit-Ereignissen
- Story-Erkennung
- thematischen Zusammenfassungen
- geopolitischer Analyse
- Globe-Visualisierung
- langfristiger Trendanalyse

## Kernprinzip

GDELT ist eine Signalquelle. Das Produktmodell der Plattform beginnt bei Artikeln und Stories.

```text
GDELT ist Input.
Artikel sind Wissen.
Stories sind Bedeutung.
Topics sind Kontext.
Themes sind strategische Analyse.
```

## Hierarchie

```text
Article
-> gehört zu Stories

Story
-> besteht aus Artikeln
-> wird durch GDELT-Signale gestützt

Topic
-> besteht aus Stories

Theme
-> besteht aus Topics
```

## Architekturentscheidung

Nicht Event-first bauen.

```text
GDELT liefert Signale
-> Artikel liefern Inhalt
-> Stories liefern Bedeutung
-> Topics liefern Kontext
-> Themes liefern Langzeitanalyse
```

Events, Mentions und GKG bleiben wichtig, aber sie sind nicht das zentrale Produktobjekt. Das zentrale Produktobjekt ist die Story.
