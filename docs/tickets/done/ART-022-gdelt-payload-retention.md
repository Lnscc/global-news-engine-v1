# ART-022: GDELT-Payload-Retention

Status: erledigt
Bereich: gdelt, operations

## Kontext

ART-023 bis ART-025 trennen temporaere GDELT-Rohdaten von den dauerhaften, erfolgreich
verarbeiteten Fachzeilen. ART-026 schliesst das gemeinsame Modell ab. Die unveraenderten
Quellzeilen liegen danach in:

```text
- gdelt_event_payloads
- gdelt_mention_payloads
- gdelt_gkg_payloads
```

Nach erfolgreichem Parsing und Normalisieren werden sie fuer den regulaeren Betrieb nicht mehr
benoetigt. Ohne eine explizite Retention wuerden die speicherintensiven `raw_tsv`-Werte dennoch
dauerhaft weiterwachsen.

Fehlerhafte oder noch nicht verarbeitete Payloads muessen fuer Diagnose und erneute Verarbeitung
erhalten bleiben. Die Fehlerhistorie in `gdelt_processing_errors` ist dauerhaft und nicht Teil der
Payload-Loeschung.

## Ziel

Erfolgreich in die jeweilige Fachtabelle ueberfuehrte Payloads werden nach einer konfigurierbaren
Aufbewahrungsdauer automatisch geloescht.

Die stabile Fachzeile und ihre ID bleiben dabei unveraendert erhalten:

```text
gdelt_gkg_payloads.id = 4711  -- wird nach Ablauf der Retention geloescht
gdelt_gkg.id          = 4711  -- bleibt dauerhaft bestehen
```

## Loeschregeln

```text
- eine Payload ist nur loeschbar, wenn eine Fachtabellenzeile mit derselben ID existiert
- die Aufbewahrungsfrist beginnt mit parsed_at der Fachtabellenzeile
- Payloads ohne passende Fachtabellenzeile werden niemals automatisch geloescht
- offene oder historische Eintraege in gdelt_processing_errors werden nicht geloescht
- ein vorhandener historischer Fehler verhindert die Loeschung nicht, wenn die Zeile spaeter erfolgreich verarbeitet wurde
- wiederholte Cleanup-Laeufe sind idempotent
- jeder Lauf verarbeitet eine begrenzte Anzahl Zeilen, um lange Sperren zu vermeiden
```

## Umfang

```text
- konfigurierbare Aufbewahrungsdauer fuer erfolgreich verarbeitete Payloads einfuehren
- Cleanup-Job fuer Event-, Mention- und GKG-Payloads implementieren
- Loeschbarkeit ausschliesslich ueber die Existenz derselben ID in der passenden Fachtabelle bestimmen
- Cleanup in begrenzten Batches und mit deterministischer Reihenfolge ausfuehren
- Anzahl geloeschter Payloads je Datensatztyp protokollieren
- Health- oder Betriebsmetrik fuer loeschbare und zurueckgehaltene Payloads bereitstellen
- Tests fuer Frist, Batch-Grenzen, Idempotenz und Schutz unverarbeiteter Payloads ergaenzen
- Betriebs- und Architektur-Dokumentation um die Retention erweitern
```

## Akzeptanzkriterien

```text
- erfolgreich verarbeitete Payloads werden erst nach Ablauf der konfigurierten Frist geloescht
- Payloads ohne passende Zeile in gdelt_events, gdelt_mentions oder gdelt_gkg bleiben erhalten
- die zugehoerige Fachzeile und ihre ID bleiben nach dem Cleanup unveraendert erhalten
- gdelt_processing_errors bleibt vollstaendig und dauerhaft erhalten
- ein spaeter behobener Fehler verhindert die Payload-Loeschung nach erfolgreicher Verarbeitung nicht
- erneute Cleanup-Laeufe loeschen keine fachlichen Daten und liefern konsistente Ergebnisse
- Batch-Grenzen verhindern eine unbeschraenkte Loeschtransaktion
- PostgreSQL-Integrationstests pruefen Retention und Referenzintegritaet
- Dokumentation beschreibt Konfiguration, Loeschregeln und Wiederherstellungsgrenzen
```

## Abhaengigkeit

Das Ticket baut auf ART-021 sowie den Payload- und Fachtabellen aus ART-023 bis ART-025 auf.
ART-026 muss das gemeinsame Modell und seine Betriebsmetriken zuvor abgeschlossen haben.

## Abgrenzung

Die Archivierung von Payloads in Object Storage, Backups ausserhalb der Datenbank, eine Retention
fuer `gdelt_processing_errors` sowie die Loeschung von Fachzeilen oder `gdelt_import_files` sind
nicht Teil dieses Tickets.

## Umsetzungskommentar

Implementiert am 2026-07-17:

- Ein konfigurierbarer Scheduler loescht EVENTS-, MENTIONS- und GKG-Payloads erst nach Ablauf der
  Aufbewahrungsfrist ab `parsed_at`. Der Standard betraegt sieben Tage (`PT168H`).
- Die Loeschbarkeit wird ausschliesslich ueber die Fachzeile mit derselben ID bestimmt. Payloads
  ohne Fachzeile, alle Fachzeilen, `gdelt_import_files` und die vollstaendige Historie in
  `gdelt_processing_errors` bleiben unveraendert erhalten.
- Jeder Datensatztyp wird in einer eigenen Transaktion in stabiler Reihenfolge nach `parsed_at`
  und Payload-ID verarbeitet. Batch-Groesse und maximale Batch-Anzahl pro Scheduler-Lauf sind
  konfigurierbar; wiederholte Laeufe sind idempotent.
- Der Job protokolliert die geloeschten Zeilen je Datensatztyp. Eine Retention-Health-Abfrage und
  eine direkt nutzbare PostgreSQL-Betriebsabfrage weisen loeschbare und zurueckgehaltene Payloads
  fuer dieselbe Frist aus.
- Betriebs-, Architektur-, Article- und GDELT-Modell-Dokumentation beschreiben Konfiguration,
  Loeschregeln und die Wiederherstellungsgrenze nach Entfernung von `raw_tsv`.
- Jobtests und ein PostgreSQL-Integrationstest pruefen Frist, Batch-Grenze, Idempotenz, Schutz
  unverarbeiteter Payloads sowie den Erhalt von Fachzeilen und Fehlerhistorie.
- Die bestehende Article API und ihr REST-Vertrag wurden nicht geaendert. Daher ist keine
  Anpassung der Postman-Collection erforderlich.
- Verifiziert mit `.\mvnw.cmd verify`: 56 Unit-/Kontexttests und 6 PostgreSQL-
  Integrationstests, jeweils ohne Fehler oder uebersprungene Tests. Die Postman-Collection wurde
  zusaetzlich als valides JSON geprueft.
