# ART-045: Story-Qualitaetsfaelle als Regressionsset sichern

Status: offen
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

