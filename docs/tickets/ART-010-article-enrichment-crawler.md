# ART-010: Article-Enrichment-Crawler implementieren

Status: offen
Bereich: articles

## Kontext

ART-008 stellt mit `article_enrichments` und den atomaren Zustandsuebergaengen die persistente
Grundlage fuer Article Enrichment bereit. Es fehlt noch der getrennte Worker, der die kanonische
Artikel-URL abruft, die Webseite auswertet und insbesondere Titel sowie weitere Metadaten
persistiert.

## Ziel

Ein separat planbarer Enrichment-Worker beansprucht faellige Artikel, ruft deren HTML-Seiten
sicher und begrenzt ab, extrahiert die vorgesehenen Enrichment-Felder und speichert Erfolg oder
einen eindeutig klassifizierten Fehler ueber das Enrichment-Repository. Die GDELT-Extraktion
bleibt davon unabhaengig.

## Umfang

```text
- Analyse der vorhandenen GDELT-Roh- und Staging-Felder je Enrichment-Zielfeld
- dokumentierte Quellenmatrix: GDELT, gecrawlte Zielseite oder abgeleiteter Wert
- definierte Prioritaets- und Konfliktregeln, wenn mehrere Quellen einen Wert liefern
- HTTP-Client-Adapter mit konfigurierbaren Verbindungs- und Lese-Timeouts
- Groessenlimit fuer Responses und begrenzte Redirect-Verfolgung
- Schutz vor SSRF: nur HTTP/HTTPS und keine Loopback-, privaten oder lokalen Zieladressen
- HTML-Parser fuer title, publishedAt, language, mainImageUrl und extractedText
- definierte Prioritaet geeigneter Metadaten, zum Beispiel Open Graph vor HTML-Fallbacks
- separater, konfigurierbarer Enrichment-Worker auf Basis von ArticleEnrichmentRepository
- automatische, batch-begrenzte Aufnahme neuer Artikel ohne Enrichment-Zeile als PENDING;
  ein manueller Enqueue-Schritt ist im regulaeren Betrieb nicht erforderlich
- Retry-Klassifizierung mit Backoff fuer temporaere Fehler
- permanente Fehler ohne nextAttemptAt fuer nicht verarbeitbare Antworten
- begrenzte und bereinigte technische Fehlermeldungen
- automatisierte Tests mit lokalem HTTP-Testserver und realistischen HTML-Fixtures
```

Nicht Teil dieses Tickets sind JavaScript-Rendering im Browser, Umgehung von Paywalls oder
Bot-Schutz, ein Vollbestands-Backfill sowie Aenderungen an der Article REST API.

## Abgrenzung GDELT und Crawler

Vor der Implementierung wird fuer jedes Zielfeld geprueft, ob und mit welcher Qualitaet es in den
tatsaechlich importierten GDELT-Datensaetzen vorhanden ist. Dabei zaehlen nicht nur theoretische
GDELT-Spezifikationen, sondern die im Projekt gespeicherten Raw-, Staging- und Article-Signal-
Felder. Das Ergebnis wird in `docs/articles.md` als Quellenmatrix dokumentiert.

Die Ausgangshypothese lautet:

```text
- canonicalUrl und domain: aus der bestehenden GDELT-basierten Artikelidentitaet
- title: primaer vom Crawler, sofern kein belastbares GDELT-Feld nachgewiesen wird
- publishedAt: GDELT-Zeitstempel nur als Beobachtungs-/Signalzeit; echter Publikationszeitpunkt
  primaer aus der Zielseite
- language: aus der Zielseite; GDELT-Sprachinformationen nur nach Qualitaetspruefung verwenden
- mainImageUrl: aus der Zielseite
- extractedText: ausschliesslich aus der Zielseite
- themes, persons, organizations, locations und tone: bleiben GDELT-Signale und werden nicht
  durch den Crawler in article_enrichments dupliziert
```

Falls die Analyse geeignete GDELT-Felder findet, muss festgelegt werden, ob sie als Fallback oder
als bevorzugte Quelle dienen. Ein Wert darf nicht stillschweigend zwischen Quellen wechseln;
Prioritaet, Normalisierung und Konfliktverhalten muessen je Feld dokumentiert und getestet sein.

## Extraktionsregeln

```text
- title: og:title, danach twitter:title, danach HTML-title
- publishedAt: article:published_time, danach geeignete strukturierte Metadaten
- language: HTML-lang, danach geeignete Metadaten
- mainImageUrl: og:image, danach twitter:image; relative URLs werden gegen die finale URL aufgeloest
- extractedText: sichtbarer Hauptinhalt ohne script, style, navigation und andere Seitenelemente
- fehlende einzelne Felder verhindern SUCCEEDED nicht, wenn die Seite insgesamt verwertbar ist
```

## Fehlerklassifizierung

```text
- temporaer: Timeouts, Verbindungsfehler, HTTP 429 und geeignete 5xx-Antworten
- permanent: unzulaessige Zieladresse, nicht unterstuetzter Content-Type, geeignete 4xx-Antworten
  und nicht verwertbares HTML
- temporaere Fehler erhalten nextAttemptAt mit begrenztem Backoff
- permanente Fehler erhalten kein nextAttemptAt
```

## Akzeptanzkriterien

```text
- ein PENDING- oder retry-faelliges Enrichment wird atomar beansprucht und genau einmal verarbeitet
- neue Artikel ohne `article_enrichments`-Zeile werden automatisch und idempotent in begrenzten
  Batches als PENDING aufgenommen
- eine dokumentierte Quellenmatrix weist fuer jedes Enrichment-Feld aus, was GDELT liefern kann,
  was der Crawler liefert und welche Quelle bei Konflikten gewinnt
- GDELT-Signalzeitpunkte werden nicht ungeprueft als Publikationszeitpunkt verwendet
- fuer verwertbares HTML werden mindestens title und alle weiteren vorhandenen Felder persistiert
- Metadaten-Prioritaeten und relative Bild-URLs sind durch Tests eindeutig belegt
- Erfolg setzt SUCCEEDED und entfernt vorherige Retry- und Fehlerdaten
- temporaere Fehler setzen FAILED mit nextAttemptAt; permanente Fehler setzen FAILED ohne Retry
- Timeouts, Response-Groesse, Redirects und erlaubte Zieladressen sind konfigurierbar beziehungsweise
  sicher begrenzt
- der Worker verarbeitet eine fehlerhafte Seite isoliert weiter, ohne den gesamten Batch abzubrechen
- ArticleExtractorService bleibt frei von HTTP-, Parsing- und Enrichment-Verantwortung
- Tests decken Erfolg, partielle Metadaten, Redirects, Timeouts, Groessenlimit, HTTP-Fehler,
  Retry und SSRF-Schutz ab
```

## Abhaengigkeit

Dieses Ticket setzt ART-008 voraus. ART-009 kann unabhaengig davon implementiert werden, liefert
aber erst nach erfolgreichem Crawling tatsaechlich angereicherte Inhalte aus.

## Implementierungskommentar

Implementiert wurde ein separat konfigurierbarer Enrichment-Job, der faellige Zeilen atomar ueber
`ArticleEnrichmentRepository` beansprucht und Seitenfehler innerhalb eines Batches isoliert. Der
HTTP-Adapter begrenzt Verbindungs-/Lesezeit, Response-Groesse und Redirects, validiert jedes Ziel
gegen SSRF und klassifiziert Netzwerk-, HTTP- und Inhaltsfehler als temporaer oder permanent.
Der Parser persistiert priorisierte Metadaten und bereinigten Haupttext; temporaere Fehler erhalten
begrenztes exponentielles Backoff. Die GDELT-Quellen und Konfliktregeln sind in `docs/articles.md`
dokumentiert. Lokale HTTP-Server-Tests decken Extraktion, partielle Metadaten, Redirects, Timeout,
Groessenlimit, Content-Type, HTTP-Fehler und Adressschutz ab.

Der Worker nimmt vor jedem Claim automatisch und idempotent bis zur konfigurierten Batch-Groesse
neue Artikel ohne `article_enrichments`-Zeile als PENDING auf. Damit ist im regulaeren Betrieb kein
manueller Enqueue-Schritt erforderlich. Das Enqueueing bleibt Teil des getrennten Enrichment-Jobs
und `ArticleExtractorService` unveraendert frei von Enrichment-Verantwortung.
