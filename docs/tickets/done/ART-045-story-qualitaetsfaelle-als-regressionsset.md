# ART-045: Story-Qualitaetsfaelle als Regressionsset sichern

Status: erledigt
Bereich: stories

## Kontext

Die qualitative Stichprobe aus ART-038 zeigt konkrete Fehl-Merges, wahrscheinlich verpasste
Zusammenfuehrungen und eine Gruppe aus Navigations- beziehungsweise Service-Seiten. Diese Faelle
sind dokumentiert, aber noch nicht als versionierte Regressionsevidenz abgesichert.

Die bekannten Faelle duerfen nicht Bestandteil des unabhaengigen Release-Holdouts aus ART-044
werden, weil sie bereits zur Fehleranalyse und zur Auswahl moeglicher Verbesserungen verwendet
wurden.

## Ziel

Ein kleines versioniertes Regressionsset macht die bekannten Qualitaetsprobleme reproduzierbar
messbar und verhindert unbemerkte Verschlechterungen bei spaeteren Aenderungen.

## Umfang

- die in `docs/analysis/ART-038-story-quality-spotcheck.md` genannten eindeutigen Fehl-Merges und
  wahrscheinlich verpassten Zusammenfuehrungen mit stabilen Referenzen erfassen
- Navigations- und Service-Seiten als eigene Input-Qualitaetskategorie erfassen
- erwartete fachliche Beziehung, Begruendung und Evidenzherkunft je Fall dokumentieren
- eine reproduzierbare automatisierte Auswertung mit versionierter Baseline bereitstellen
- Abweichungen zwischen Ziel-Label und aktuellem Clustering sichtbar machen

## Akzeptanzkriterien

- alle eindeutigen Fehl-Merges aus der ART-038-Stichprobe sind mit `DIFFERENT_STORY` und
  Begruendung enthalten
- die dokumentierten Kandidaten fuer verpasste Zusammenfuehrungen sind nach fachlicher Pruefung
  eindeutig gelabelt oder explizit als nicht entscheidbar ausgeschlossen
- Navigations- und Service-Seiten werden getrennt von normalen Ereignisartikeln ausgewiesen
- Referenzen, Titelgrundlage, Herkunft und Korpusversion sind nachvollziehbar gespeichert
- die automatisierte Auswertung ist deterministisch und meldet die bekannten Abweichungen des
  aktuellen Verfahrens
- eine eingefrorene Baseline verhindert, dass spaetere Aenderungen die gemessene Qualitaet
  unbemerkt verschlechtern
- das Regressionsset ist technisch und dokumentarisch klar vom unabhaengigen Release-Holdout aus
  ART-044 getrennt und wird nicht als Freigabenachweis verwendet

## Abgrenzung

Die Behebung aller erfassten Fehler, neue Ereignismerkmale, Volltextanalyse, Schwellenwertwechsel
und die produktive Promotion einer Clustering-Version sind nicht enthalten.

## Offene Fragen

- Welche der bisher nur als wahrscheinlich bezeichneten Zusammenfuehrungen lassen sich anhand
  der vorhandenen Evidenz eindeutig labeln?

## Implementierungskommentar (2026-10-01)

Das versionierte Set `docs/analysis/ART-045-regression-cases-v1.json` sichert die drei eindeutigen
Fehl-Merges als `DIFFERENT_STORY`, zwei fachlich bestaetigte verpasste Zusammenfuehrungen als
`SAME_STORY` und den El-Nino-Fall mangels Volltextpruefung explizit ausgeschlossen als `UNCERTAIN`.
Sechs Navigations-/Service-Seiten und ein normaler FasterSkier-Inhalt bilden eine getrennte
Input-Qualitaetskategorie.

`scripts/art045_regression.py` validiert Referenzen, Herkunft, Labels und Kategorien und vergleicht
die deterministische Auswertung mit `docs/analysis/ART-045-baseline-v1.json`. Die Baseline weist
die elf bekannten Abweichungen der aktuellen Version sichtbar aus und scheitert bei ungesehenen
Aenderungen. Die Dokumentation grenzt das Set ausdruecklich vom unabhaengigen ART-044-Holdout und
von Freigabenachweisen ab.
