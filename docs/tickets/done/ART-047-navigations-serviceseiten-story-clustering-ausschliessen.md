# ART-047: Navigations- und Serviceseiten vom Story-Clustering ausschliessen

Status: erledigt
Bereich: stories

## Kontext

Die Stichprobe aus ART-038 zeigt eine vermeintliche Story aus Navigations-, Service- und
redaktionellen Seiten derselben Website. Gemeinsame Seitentitelbestandteile fuehrten unter anderem
`About`, `Advertise`, `Meet the Team`, `Privacy`, `Resources` und `Support` zusammen, obwohl diese
Seiten keine Ereignisartikel sind. ART-045 sichert sechs solche Seiten sowie einen weiterhin
einzuschliessenden redaktionellen Gegenfall als Regressionsevidenz.

## Ziel

Eindeutig erkennbare Navigations- und Serviceseiten werden deterministisch und nachvollziehbar von
der Story-Verarbeitung ausgeschlossen. Normale redaktionelle Artikel derselben Website bleiben
unveraendert fuer das Clustering geeignet.

## Umfang

- konservative, versionierte Regeln fuer eindeutig erkennbare Navigations- und Serviceseiten
  definieren
- die Entscheidung anhand kanonischer URL und vorhandenem Seitentitel vor dem Embedding treffen
- ausgeschlossene Seiten mit einem eindeutigen Grund als nicht fuer Story-Embeddings erforderlich
  erfassen
- ausgeschlossene Seiten nicht in Story-Snapshots, Paarentscheidungen oder Mitgliedschaften
  aufnehmen
- bestehende Story-Mitgliedschaften ausgeschlossener Seiten beim naechsten Lauf der neuen
  Clustering-Version nicht fortfuehren
- die Input-Qualitaetsfaelle aus ART-045 als Regressionstest verwenden

## Fachliche Regeln

- nur eindeutig erkannte Navigations- und Serviceseiten werden ausgeschlossen; im Zweifel bleibt
  ein Artikel eingeschlossen
- Regeln verwenden exakte normalisierte Pfade oder gleichwertig enge Merkmale, keine unscharfe
  Teilstringsuche in beliebigen Artikel-URLs
- die Entscheidung ist fuer gleiche Eingabedaten und dieselbe Regelversion identisch
- ein Ausschluss loescht weder den Artikel noch seine GDELT-Daten und veraendert die Article API
  nicht
- die neue Regel wird nicht still in einer bereits freigegebenen Clustering-Version geaendert
- ein ML- beziehungsweise LLM-Klassifikator ist fuer den initialen Ausschluss nicht erforderlich

## Akzeptanzkriterien

- die sechs `NAVIGATION_SERVICE`-Faelle aus `docs/analysis/ART-045-regression-cases-v1.json`
  werden mit nachvollziehbarem Ausschlussgrund erkannt
- fuer ausgeschlossene Seiten wird kein Story-Embedding angefordert
- ausgeschlossene Seiten erhalten in der neuen Clustering-Version keine Story-Mitgliedschaft
- der ART-045-Gegenfall `content-speed-load` bleibt eingeschlossen und fuer das Clustering geeignet
- ein Navigationsbegriff innerhalb eines normalen Artikelpfads fuehrt nicht allein zum Ausschluss
- Ausschlussentscheidung, Regelversion und Grund sind in der Story-Verarbeitung nachvollziehbar
- wiederholte Verarbeitung unveraenderter Eingaben erzeugt dasselbe Ergebnis und keine zusaetzlichen
  Embedding-Aufrufe
- automatisierte Tests decken Erkennung, Nicht-Erkennung, Embedding-Ausschluss und
  Story-Mitgliedschaft ab
- das ART-045-Regressionsset bleibt vom unabhaengigen Release-Holdout getrennt und wird nicht als
  Freigabenachweis verwendet

## Abgrenzung

Keine allgemeine Webseitenklassifikation, kein externer Seitenabruf, keine Volltextanalyse und kein
ML- oder LLM-Klassifikator. Fehlerhafte Story-Zusammenfuehrungen normaler Ereignisartikel,
Titelbereinigung und neue Ereignismerkmale sind nicht Teil dieses Tickets. REST-Vertraege werden
nicht geaendert.

## Abhaengigkeiten

Das Ticket verwendet die in ART-045 versionierten Input-Qualitaetsfaelle. Eine geaenderte
Clustering-Regel wird entsprechend dem bestehenden Versions- und Freigabevertrag als neue Version
geprueft und aktiviert.

## Offene Fragen

Keine.

## Implementierungskommentar (2026-10-01)

Die neue Shadow-Version `story-mvp-title-embedding-24h-v1.1.0` verwendet die versionierte Regel
`navigation-service-exact-path-title-v1`. Sie schliesst die sechs durch ART-045 belegten
Navigations- und Serviceseiten nur bei passender Kombination aus exaktem kanonischem Pfad und
normalisiertem Titel mit Grund `NAVIGATION_SERVICE` aus. Die Disposition wird am Artikel-Input
gespeichert; ausgeschlossene Inputs erzeugen weder Embedding-Aufrufe noch Snapshot-Eintraege oder
Story-Mitgliedschaften. Der redaktionelle Gegenfall und Navigationsbegriffe in laengeren
Artikelpfaden bleiben eingeschlossen. Unit- und PostgreSQL-Integrationstests decken Regel,
Embedding-Ausschluss und Snapshot-/Mitgliedschaftsausschluss ab; das ART-045-Regressionsset bleibt
vom Release-Holdout getrennt. Die Promotion behandelt aktuelle `EXCLUDE`-Inputs der Zielversion
als beabsichtigte Entfernung, waehrend tatsaechlich fehlende Inputs weiterhin die Promotion
blockieren; ein Promotionstest sichert diesen Versionswechsel ab.
