# ART-030: Story-Begriff und Clustering-Ziel definieren

Status: erledigt
Bereich: stories, architecture

## Kontext

Die Plattform besitzt eine stabile Artikel-Schicht mit GDELT-Signalen, aber noch kein fachlich
definiertes Story-Objekt. Das Architekturziel nennt Stories als zentrales Produktobjekt zwischen
Artikeln und Topics. Ohne eine klare Abgrenzung wuerde ein Clustering jedoch technische
Aehnlichkeit mit fachlicher Zusammengehoerigkeit verwechseln.

Insbesondere muss unterschieden werden zwischen demselben konkreten Geschehen, einem Folgeereignis
und einem langfristigen Topic. Diese Entscheidung soll vor Datenmodell, Algorithmus und REST API
getroffen werden.

## Ziel

Eine verbindliche, anhand realer Beispiele pruefbare Definition beschreibt, wann Artikel zu
derselben Story gehoeren und welche Eigenschaften das erste Story-MVP besitzt.

## Umfang

```text
- Story, Folgeereignis, Topic und Theme voneinander abgrenzen
- positive, negative und mehrdeutige Zuordnungsbeispiele dokumentieren
- fachliche Einheit einer Story festlegen: konkretes Geschehen statt allgemeines Thema
- Artikelmitgliedschaft fuer das MVP festlegen, insbesondere genau eine oder mehrere Stories
- relevante Zeitbegriffe und deren fachliche Bedeutung benennen
- Mindestinhalt einer Story definieren
- nicht-funktionale Ziele fuer Nachvollziehbarkeit und reproduzierbare Zuordnung festlegen
- Rolle von Titel-Embeddings als primaeres, aber nicht allein entscheidendes MVP-Signal festlegen
- MVP-Abgrenzung gegen Volltext, generative LLM-Funktionen, Topics und Themes dokumentieren
- Konsequenzen fuer Analyse, Evaluationskorpus und Lebenszyklusentscheidungen ableiten
```

## Zu klaerende Entscheidungen

```text
- Wann sind zwei Meldungen Aktualisierungen derselben Story?
- Wann beginnt ein eigenstaendiges Folgeereignis?
- Welche Rolle spielen Zeit, Ort, Personen, Organisationen, GDELT-Events und Themes?
- Wie werden Sammelartikel, Liveblogs und Rueckblicke behandelt?
- Darf ein Artikel im MVP mehreren Stories angehoeren?
- Welche Mindestbegruendung muss eine automatische Zuordnung liefern koennen?
- Welche Rolle spielen Embedding-Aehnlichkeit, Zeitfenster und strukturierte Trennsignale zusammen?
```

## Akzeptanzkriterien

```text
- eine zentrale Story-Definition ist dokumentiert und widerspricht dem Architekturziel nicht
- Story, Folgeereignis, Topic und Theme sind trennscharf beschrieben
- mindestens zehn reale oder realistische Grenzfaelle besitzen eine begruendete Sollentscheidung
- Artikelmitgliedschaft und Mindestinhalt einer Story sind fuer das MVP entschieden
- benoetigte Eingangssignale sind nach notwendig, optional und spaeter eingeordnet
- Titel-Embeddings sind als versioniertes Kandidaten- und Aehnlichkeitssignal eingeordnet, ohne
  fachliche Story-Identitaet allein aus einem Similarity-Wert abzuleiten
- explizite Nicht-Ziele verhindern, dass das erste Story-MVP zum Topic- oder LLM-Projekt anwaechst
- offene Unsicherheiten sind benannt und als messbare Fragen fuer ART-031 oder ART-032 formuliert
- es erfolgen keine Aenderungen an Datenbank oder REST API
```

## Abhaengigkeiten

Das Ticket baut auf der vorhandenen Artikel-Schicht und der Zielarchitektur
`Artikel -> Stories -> Topics -> Themes` auf. Es ist Voraussetzung fuer ART-031, ART-032 und
ART-033.

## Abgrenzung

Story-Persistenz, Clustering-Algorithmus, Batch-Verarbeitung, API, UI und produktive
Zusammenfassungen sind nicht Teil dieses Tickets.

## Implementierungskommentar

Die fachliche Story-Definition und das Clustering-Ziel des MVP sind in `docs/stories.md`
dokumentiert und vom Architektur-Einstiegspunkt verlinkt. Festgelegt wurden insbesondere ein
konkretes Geschehen als Story-Einheit, die Abgrenzung zu Folgeereignis, Topic und Theme, hoechstens
eine Story je Artikel, zulaessige Singletons, der Umgang mit Sammelartikeln, Liveblogs und
Rueckblicken sowie die Mindestdaten und Mindestbegruendung einer Zuordnung. Achtzehn begruendete
Grenzfaelle und messbare Anschlussfragen konkretisieren die Arbeit fuer ART-031 bis ART-033.
Titel-Embeddings sind als primaeres MVP-Signal fuer Kandidatensuche und Aehnlichkeit aufgenommen.
Sie werden mit einem Zeitfenster und vorhandenen strukturierten Signalen kombiniert und duerfen
eine Zuordnung nicht allein aufgrund eines Similarity-Werts erzwingen. Volltext und generative
LLM-Funktionen bleiben ausserhalb des MVP.

Es wurden bewusst weder produktives Datenbankschema noch REST API, Cluster-Implementierung oder
Story-Persistenz veraendert.
