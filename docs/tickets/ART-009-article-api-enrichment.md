# ART-009: Article API um Enrichment erweitern

Status: offen
Bereich: articles

## Kontext

ART-008 fuehrt persistierte Enrichment-Daten in `article_enrichments` ein. Die bestehenden
Endpoints `GET /articles` und `GET /articles/{id}` liefern bislang nur die technische
Artikelidentitaet und bei der Detailabfrage die GDELT-Signale. Vorhandene angereicherte Inhalte
waeren damit fuer API-Clients nicht sichtbar.

## Ziel

Artikelabfragen liefern neben den bisherigen Feldern alle fuer den Artikel vorhandenen
Enrichment-Daten. Noch nicht angereicherte Artikel bleiben abrufbar und enthalten fuer diese
Felder `null` beziehungsweise einen nicht erfolgreichen Enrichment-Status.

## Umfang

```text
- ArticleQueryService liest article_enrichments per LEFT JOIN
- ArticleSummary und ArticleDetail werden um Enrichment-Daten erweitert
- GET /articles liefert vorhandene Enrichment-Daten je Listeneintrag
- GET /articles/{id} liefert vorhandene Enrichment-Daten zusammen mit allen bisherigen Signalen
- API-Vertrag umfasst title, publishedAt, language, mainImageUrl, extractedText,
  enrichmentStatus, attemptCount, lastAttemptAt, nextAttemptAt, errorCode, errorMessage
  und enrichedAt
- Controller-, Query-Service- und Contract-Tests fuer Artikel mit und ohne Enrichment
- Postman-Collection und Postman-Tests werden an den neuen Response-Vertrag angepasst
```

## Akzeptanzkriterien

```text
- GET /articles enthaelt alle persistierten Enrichment-Felder, wenn sie vorhanden sind
- GET /articles/{id} enthaelt alle persistierten Enrichment-Felder und weiterhin alle Signale
- Artikel ohne article_enrichments-Zeile werden durch den LEFT JOIN nicht ausgefiltert
- nullable Felder und Enrichment-Status sind im Response-Vertrag eindeutig definiert
- Pagination, Sortierung und bestehende Statuscodes bleiben unveraendert
- Tests decken vollstaendiges, partielles, fehlgeschlagenes und fehlendes Enrichment ab
- docs/postman/Article-API.postman_collection.json ist aktualisiert, enthaelt passende
  Contract-Tests und bleibt valides JSON
```

## Abhaengigkeit

Dieses Ticket wird nach ART-008 umgesetzt, da es dessen Tabelle und Zustandsmodell voraussetzt.
