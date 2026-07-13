# ART-016: Artikelliste um Signalzusammenfassung erweitern

Status: offen
Bereich: articles
Prioritaet: zurueckgestellt

## Kontext

`GET /articles` liefert pro Artikel derzeit nur Identitaet, Beobachtungszeit und Titelmetadaten.
Die vorhandenen GDELT-Signale sind erst im Detailendpunkt sichtbar. Dadurch kann ein Client in der
Liste weder Signalstaerke noch zeitliche Aktivitaet oder zentrale Themes einschaetzen.

## Ziel

`ArticleSummary` erhaelt eine kompakte, begrenzte Zusammenfassung der vorhandenen Signale, ohne die
vollstaendige Signalliste zu duplizieren oder pro Artikel weitere Datenbankabfragen auszufuehren.

## Umfang

```text
- signalCount als Anzahl aller Signale
- signalTypes als stabile, duplikatfreie Liste vorhandener Typen
- firstSignalAt und latestSignalAt
- wichtigste Themes als begrenzte, duplikatfreie Liste mit dokumentierter Rangfolge
- averageTone nur ueber vorhandene numerische tone_value-Werte und nullable ohne Messwerte
- ArticleSummary, Query-Service und REST-Response erweitern
- Aggregation fuer eine ganze Seite ohne N+1-Queries umsetzen
- Verhalten fuer Artikel ohne Signale definieren und testen
- Query-, Controller-, Performance-/Query-Shape- und Contract-Tests
- Postman-Collection und Postman-Tests aktualisieren
```

## Fachliche Regeln

```text
- die Zusammenfassung ersetzt nicht die Signale im Detailendpunkt
- Listen sind in Groesse und Reihenfolge deterministisch
- Themes werden als vollstaendige semikolongetrennte Eintraege ausgewertet
- averageTone mischt nur numerisch vergleichbare tone_value-Werte und bleibt sonst null
- Aggregationen aendern weder Artikelanzahl noch Pagination oder Sortierung
```

## Akzeptanzkriterien

```text
- Summary-Werte stimmen fuer gemischte EVENTS-, MENTIONS- und GKG-Signale
- mehrere gleiche Themes und Signaltypen werden korrekt zusammengefasst
- Artikel ohne Signale bleiben abrufbar und liefern neutrale beziehungsweise nullable Werte
- die Listenabfrage verursacht keine Query pro Artikel
- total, Offset-/Limit-Pagination und stabile Sortierung bleiben unveraendert
- Detailsignale und bestehende Statuscodes bleiben unveraendert
- Postman-Collection ist aktualisiert und valides JSON
```

## Abgrenzung

Die Felder sind beobachtbare Artikelmetriken und keine Relevanzbewertung. Story-Clustering,
quellenuebergreifende Deduplizierung, personalisiertes Ranking und LLM-Zusammenfassungen gehoeren
nicht in dieses Ticket.
