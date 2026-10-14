# ART-049: Story-Clustering-Versionen automatisiert pruefen und freigeben

Status: erledigt
Bereich: stories, operations

## Kontext

Die Freigabe einer neuen Story-Clustering-Version erfordert derzeit manuelle Datenbankabfragen,
temporaere Testklassen und eine lange interaktive Begleitung. Insbesondere das Nachziehen eines
Kandidaten auf den Wasserstand der aktiven Version, die Fortschrittskontrolle und der vollstaendige
Identitaetsvergleich sind langsam und fehleranfaellig. Der ART-048-Lauf hat ausserdem gezeigt, dass
ein veralteter Kandidaten-Snapshot erst nach einem teuren Review als nicht freigabefaehig erkannt
wird.

## Ziel

Ein reproduzierbarer, wiederaufnehmbarer Freigabelauf bereitet eine angegebene Story-Version vor,
prueft sie und erzeugt alle Entscheidungsdaten ohne manuelle SQL-Arbeit oder interaktive
KI-Begleitung. Eine Promotion bleibt eine ausdrueckliche, abgesicherte Aktion.

## Umfang

- einen dokumentierten operativen Einstieg fuer Vorbereitung, Review und optionale Promotion
  bereitstellen
- Kandidat und aktive Version vor teurer Verarbeitung auf kompatible Wasserstaende pruefen
- fehlende Inputs und Embeddings effizient sowie wiederaufnehmbar verarbeiten
- Fortschritt, Laufzeit und Blocker maschinenlesbar und fuer den Betrieb verstaendlich ausgeben
- unabhaengigen Holdout und bekannte Regressionssets getrennt auswerten
- Gates, Story-Diff, Evidenz-Hashes und Freigabeentscheidung reproduzierbar speichern
- wiederholte Aufrufe ohne doppelte Artefakte oder widerspruechliche Statuswechsel erlauben

## Akzeptanzkriterien

- ein dokumentierter Aufruf nimmt mindestens Kandidatenversion, Holdout-Datei und Review-Metadaten
  entgegen
- ein Vorabcheck meldet vor der aufwendigen Berechnung, wenn der Kandidat aelter als die aktive
  Version, unvollstaendig oder nicht reviewfaehig ist
- Vorbereitung und Review benoetigen weder temporaere Testklassen noch manuelle SQL-Abfragen
- Embedding-Arbeit wird gebuendelt, zeigt belastbaren Fortschritt und kann nach einem Fehler ohne
  Verlust bereits fertiger Artefakte fortgesetzt werden
- `story-release-gates-v1`, Story-Diff und getrennte Regressionsevidenz werden unveraendert
  ausgewertet und dauerhaft dokumentiert
- ohne ausdrueckliche Promotion und vollstaendige fachliche Zustimmung bleiben Versionsstatus
  unveraendert
- eine angeforderte Promotion wird nur bei bestandenen Gates ausgefuehrt und bestaetigt danach
  genau eine `ACTIVE`-Version sowie erfolgreiche Story-Liste und Story-Detail-Abfrage
- derselbe Lauf ist mit identischen Eingaben reproduzierbar und idempotent
- automatisierte Tests decken Vorabcheck, Wiederaufnahme, fehlgeschlagene Gates, fehlende Zustimmung
  und erfolgreiche Promotion ab

## Abgrenzung

Keine Aenderung an Clustering-Regeln, Release-Gates, Story-REST-Vertraegen oder automatische
Promotion ohne explizite Zustimmung. Ein allgemeines Workflow- oder Job-Framework ist nicht Teil
des Tickets.

## Abhaengigkeiten

Das Ticket verwendet den bestehenden Freigabevertrag aus ART-039 und soll den operativen Ablauf
von ART-048 vereinfachen. ART-048 bleibt bis zu einer belastbaren Entscheidung unveraendert offen.

## Offene Fragen

- Soll der operative Einstieg als lokaler CLI-Befehl oder als interner administrativer Job
  angeboten werden?
- Welche Fortschrittsdaten muessen dauerhaft gespeichert werden und welche genuegen als Logausgabe?

## Implementierungskommentar (2026-10-08)

Umgesetzt ist ein explizit aktivierter lokaler CLI-Lauf auf Basis der vorhandenen Embedding-,
Snapshot-, Gate- und Promotion-Services. Er prueft Kandidatenstatus, laufende Verarbeitung,
Wasserstaende sowie fehlende, veraltete und unvollstaendige Inputs vor dem Review. Anschliessend
verarbeitet er nur die Kandidatenversion in begrenzten, wiederaufnehmbaren Batches und publiziert
idempotent auf dem Wasserstand der aktiven Version.

Der Lauf speichert Holdout-Gates, Story-Diff, Basis- und Evidenz-Hashes, Review-Metadaten und die
separat ausgewerteten ART-045-Regressionen als JSON-Datei. Fortschritt und Blocker werden als
maschinenlesbare JSON-Logzeilen ausgegeben. Ohne ausdruecklichen Promotionsschalter und
vollstaendige Zustimmung oder bei gescheiterten Gates bleiben die Status unveraendert. Nach einer
Promotion werden genau eine aktive Version sowie Story-Liste und Story-Detail geprueft.

Automatisierte Tests decken den fruehen Vorabcheck, Wiederaufnahme, fehlgeschlagene Gates,
fehlende Zustimmung und erfolgreiche Promotion ab. Der Aufruf und seine optionalen Pfade sind in
`docs/operations.md` dokumentiert. Das Ticket wurde gemaess Vorgabe nicht verschoben und sein
Status nicht geaendert.

Validierung am 2026-10-08: `mvnw verify` bestand mit 93 Unit-Tests (ein optionaler Korpustest
uebersprungen) und allen 44 PostgreSQL-Integrationstests.

### Korrektur am 2026-10-14

Die Wiederaufnahme nach einer Promotion wurde abgesichert: Eine atomar geschriebene
`PROMOTION_PENDING`-Evidenz erlaubt die Reparatur der finalen Evidenzdatei, ohne die Promotion zu
wiederholen. Ein Request-Hash verhindert die Wiederverwendung fuer abweichende Holdout-,
Regressions-, Review-, Zustimmungs- oder Batch-Eingaben; `RETIRED`-Versionen werden nicht als
aktuell aktive Freigabe bestaetigt. JSON-Fortschritt und Blocker enthalten jetzt die Laufzeit.

Validierung am 2026-10-14: `mvnw verify` bestand mit 96 Unit-Tests (ein optionaler Korpustest
uebersprungen) und allen 44 PostgreSQL-Integrationstests.
