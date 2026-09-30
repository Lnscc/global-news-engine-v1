# ART-044: Erste Story-Clustering-Version pruefen und freigeben

Status: erledigt
Bereich: stories, operations

## Kontext

Die Story API zeigt ausschliesslich Stories der aktuellen `ACTIVE`-Clustering-Version. Derzeit
stehen alle drei vorhandenen Versionen auf `SHADOW`; deshalb liefert `GET /stories` eine leere
Liste, obwohl Shadow-Stories berechnet und publiziert wurden.

Die technische Promotion ist mit ART-039 vorhanden. Der ART-032-Korpus wurde jedoch bereits zur
Regelwahl verwendet und ist kein unabhaengiger Freigabenachweis. Die bekannte Stichprobe aus
ART-038 dokumentiert ausserdem Qualitaetsprobleme, ersetzt aber ebenfalls keinen neuen Holdout.

## Ziel

Ein neuer unabhaengiger Holdout liefert eine belastbare Freigabeentscheidung fuer die aktuelle
24-Stunden-Shadow-Version. Nur bei bestandenen Gates und dokumentierter fachlicher Zustimmung wird
die Version promoviert und damit ueber die Story API sichtbar.

## Umfang

- neuen, leakagefreien Holdout mit Herkunft, Version, Label-Verantwortung und
  Unabhaengigkeitserklaerung erstellen
- eindeutige positive und negative Artikelpaare mit fachlicher Begruendung labeln
- die bestehende 24-Stunden-Shadow-Version mit den Gates aus ART-039 pruefen
- Review, Metriken, Story-Diff und Freigabeentscheidung nachvollziehbar dokumentieren
- bei bestandenen Gates eine dokumentierte Promotion ausfuehren
- nach erfolgreicher Promotion die aktive Version und die Story API pruefen
- bei nicht bestandenen Gates keine Promotion ausfuehren und die Blocker dokumentieren

## Akzeptanzkriterien

- der Holdout erfuellt die Mindestgroesse und Metadatenanforderungen von
  `story-release-gates-v1`
- der Holdout ist vom ART-032-Korpus und von den bekannten ART-038-Qualitaetsfaellen getrennt und
  wurde nicht zur Wahl von Schwellenwerten oder Regeln verwendet
- jedes Label besitzt eine fachliche Begruendung; mehrdeutige Faelle werden nicht als eindeutige
  positive oder negative Beispiele erzwungen
- `StoryPromotionService.review` wird gegen den unveraenderten aktuellen Stand ausgefuehrt und
  Review, Metriken und Diff werden gespeichert
- eine Promotion erfolgt nur bei bestandenen Gates und mit dokumentiertem Pruefer,
  Dokumentverweis und Grund
- nach erfolgreicher Promotion existiert genau eine `ACTIVE`-Clustering-Version,
  `GET /stories` liefert vorhandene Stories und ein gelistetes `GET /stories/{id}` liefert `200`
- bei nicht bestandenen Gates bleiben alle Versionen unveraendert und die Story API bleibt
  korrekterweise leer
- der Holdout und die Freigabeentscheidung sind reproduzierbar dokumentiert

## Abgrenzung

Neue Clustering-Regeln, geaenderte Schwellenwerte, ein automatischer Shadow-Fallback der API,
ein Freigabe-REST-Endpunkt und eine Umgehung der bestehenden Gates sind nicht enthalten.

## Offene Fragen

- Wer uebernimmt fachliches Labeling und Freigabe?
- Welche unabhaengige Datenquelle beziehungsweise welcher Zeitraum wird fuer den Holdout verwendet?

## Implementierungskommentar (2026-09-30)

Linus bestaetigte den unabhaengigen Holdout mit 20 positiven und 20 negativen Paaren aus dem
Zeitraum 2026-09-01 bis 2026-09-02. Version 1 erreichte eine Precision von `1.00` und einen Recall
von `0.95` und bestand damit `story-release-gates-v1`. Review, Story-Diff, Hashes und Zustimmung
sind unter `docs/analysis/ART-044-release-decision.md` dokumentiert.

`story-mvp-title-embedding-24h-v1.0.0` wurde erfolgreich promoviert und ist die einzige
`ACTIVE`-Version. Die beiden anderen Versionen blieben `SHADOW`. Die lokale Datenbasis wurde auf
ausdruecklichen Wunsch auf die 74 Holdout-Artikel reduziert; Rohimporte wurden als
Wiederherstellungsquelle behalten. `GET /stories` lieferte 53 sichtbare Stories und ein Abruf einer
gelisteten Story lieferte `200`.
