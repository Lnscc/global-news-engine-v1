# ART-008: Article Enrichment implementieren

Status: erledigt
Bereich: articles

## Kontext

ART-007 hat das Zielmodell fuer angereicherte Artikel festgelegt. Enrichment-Daten werden in
einer separaten 1:1-Tabelle gespeichert, damit die stabile GDELT-basierte Artikelidentitaet und
der asynchrone Crawl-Lebenszyklus getrennt bleiben.

## Ziel

Das in `docs/articles.md` beschriebene Enrichment-Modell als persistente, retry-faehige Grundlage
fuer einen spaeteren Crawler implementieren, ohne Crawling in die GDELT-Extraktion einzubauen.

## Umfang

```text
- additive Flyway-Migration fuer article_enrichments
- Felder fuer Titel, Publikationszeitpunkt, Sprache, Hauptbild und extrahierten Text
- Statusmodell PENDING | PROCESSING | SUCCEEDED | FAILED
- Attempt-, Retry- und Fehlerfelder
- atomare Beanspruchung faelliger Enrichments fuer parallele Worker
- Repository/Service fuer Zustandsuebergaenge und persistierte Ergebnisse
- Tests fuer Persistenz, Zustandsuebergaenge, Retry und parallele Beanspruchung
```

Nicht Teil dieses Tickets sind der konkrete HTTP-Crawler/Parser, ein Vollbestands-Backfill und
eine Erweiterung der Article REST API.

## Akzeptanzkriterien

```text
- article_enrichments ist als 1:1-Beziehung zu articles migriert
- Statuswerte und notwendige Zustandsinvarianten werden von Datenbank oder Service abgesichert
- ein Worker kann Pending- und retry-faellige Eintraege atomar beanspruchen
- Erfolg speichert Enrichment-Daten und entfernt vorherige Retry-/Fehlerdaten
- temporaere und permanente Fehler koennen eindeutig persistiert werden
- ArticleExtractorService bleibt unveraendert frei von Crawling- und Enrichment-Verantwortung
- automatisierte Tests decken Migration und wesentliche Zustandsuebergaenge ab
```

## Implementierungskommentar

Implementiert wurde eine additive `article_enrichments`-Migration mit 1:1-Fremdschluessel,
Status- und Zustands-Constraints sowie Faelligkeitsindex. Ein separates Enrichment-Repository
unterstuetzt idempotentes Enqueueing, atomare Batch-Beanspruchung mit `FOR UPDATE SKIP LOCKED`,
erfolgreiche Ergebnis-Persistenz und temporaere beziehungsweise permanente Fehler. Der
`ArticleExtractorService` und die REST API bleiben unveraendert.
