# ART-048: Story-Clustering-Version 1.1 pruefen und freigeben

Status: offen
Bereich: stories, operations

## Kontext

`story-mvp-title-embedding-24h-v1.0.0` ist die aktuelle `ACTIVE`-Version. ART-047 hat mit
`story-mvp-title-embedding-24h-v1.1.0` eine neue `SHADOW`-Version angelegt, die eindeutig erkannte
Navigations- und Serviceseiten vor der Story-Verarbeitung ausschliesst.

Eine neue Version wird nicht automatisch aktiv. Der bestehende Freigabevertrag verlangt einen
unabhaengigen Holdout, bestandene Gates und eine dokumentierte fachliche Freigabe.

## Ziel

Eine nachvollziehbare Freigabeentscheidung klaert, ob Version 1.1 die aktive Story-Version
ersetzen darf. Nur bei bestandenen Gates und ausdruecklicher Zustimmung wird sie promoviert.

## Umfang

- einen neuen unabhaengigen Holdout nach dem bestehenden Freigabevertrag erstellen
- Version 1.1 unveraendert mit `story-release-gates-v1` pruefen
- Metriken, Story-Diff und bekannte Regressionen fachlich bewerten
- Holdout und ART-045-Regressionsfaelle als getrennte Evidenz behandeln
- Freigabeentscheidung mit Pruefer, Dokumentverweis und Begruendung festhalten
- bei Freigabe Version 1.1 promovieren und die aktive Version sowie Story API pruefen
- bei Ablehnung Versionen unveraendert lassen und Blocker dokumentieren

## Akzeptanzkriterien

- der Holdout enthaelt mindestens 20 positive und 20 negative begruendete Paare sowie Herkunft,
  Version, Label-Verantwortung und Unabhaengigkeitserklaerung
- der Holdout wurde nicht fuer die Regeln von Version 1.1 verwendet und ist vom ART-032-Korpus
  sowie den ART-045-Regressionsfaellen getrennt
- Review, Metriken und Story-Diff sind fuer den unveraenderten Kandidaten dokumentiert
- die festen Gates von Precision mindestens `0.95` und Recall mindestens `0.90` sind ausgewertet
- eine Promotion erfolgt nur bei bestandenen Gates und dokumentierter fachlicher Zustimmung
- nach erfolgreicher Promotion ist Version 1.1 die einzige `ACTIVE`-Version; `GET /stories` und
  `GET /stories/{id}` liefern vorhandene Stories erfolgreich
- bei nicht bestandenen Gates oder fehlender Zustimmung bleibt Version 1.1 `SHADOW`
- die Entscheidung und ihre Evidenz sind reproduzierbar dokumentiert

## Abgrenzung

Keine neuen Clustering-Regeln, Schwellenwerte, Eingangsmerkmale, REST-Endpunkte oder automatische
Promotion. Das ART-045-Regressionsset ersetzt keinen unabhaengigen Freigabe-Holdout.

## Offene Fragen

- Welche Datenquelle und welcher Zeitraum bilden den unabhaengigen Holdout?
- Wer uebernimmt fachliches Labeling, Review und Freigabe?
