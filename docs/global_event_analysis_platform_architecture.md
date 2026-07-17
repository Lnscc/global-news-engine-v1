# Globale Ereignis- und Analyseplattform - Architektur

Diese Datei ist der Einstiegspunkt fuer die Architekturplanung. Die Details sind in kleinere Dokumente aufgeteilt, damit die Planung uebersichtlich bleibt.

Weitere Planungsdokumente:

```text
- articles.md: Artikel-Schicht zwischen GDELT-Fachmodell und Stories
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

## Aktuelle GDELT-Persistenz

EVENTS, MENTIONS und GKG trennen die unveraenderte, temporaere Quellzeile in
`gdelt_event_payloads`, `gdelt_mention_payloads` und `gdelt_gkg_payloads` von den dauerhaften,
erfolgreich geparsten Fachzeilen in `gdelt_events`, `gdelt_mentions` und `gdelt_gkg`. Payload und
Fachzeile verwenden dieselbe stabile ID. Parsing-Fehler werden unabhaengig davon dauerhaft in
`gdelt_processing_errors` historisiert.

Der gemeinsame produktive Pfad lautet fuer alle drei Datensatztypen:

```text
Download -> Payload-Import -> Parsing/Normalisierung -> Fachzeile -> Article-Extraktion
```

`gdelt_pipeline_health_view` fasst Payload-Bestand, Payloads ohne Fachzeile, offene Processing-Fehler
und Fachzeilen je Datensatztyp zusammen. Der Retention-Job loescht Payloads nach einer
konfigurierbaren Frist nur dann, wenn die Fachzeile mit derselben ID existiert. Die Frist beginnt
bei `parsed_at`; Auswahl und Loeschung erfolgen je Datensatztyp in begrenzten, deterministischen
Batches. Fachzeilen, Payloads ohne Fachzeile und `gdelt_processing_errors` werden nicht geloescht.

```text
Payload ohne Fachzeile -> dauerhaft fuer Retry und Diagnose behalten
Payload mit Fachzeile  -> bis parsed_at + Retention behalten -> Payload loeschen
Fachzeile              -> dauerhaft behalten
Processing-Fehler      -> dauerhaft behalten
```
