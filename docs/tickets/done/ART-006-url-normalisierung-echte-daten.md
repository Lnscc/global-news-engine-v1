# ART-006: URL-Normalisierung mit echten Daten pruefen

Status: erledigt
Bereich: articles

## Kontext

Die erste URL-Normalisierung ist bewusst konservativ. `http` und `https` werden zum Beispiel
nicht zusammengefuehrt. Erste echte Daten zeigen, dass dadurch manche Artikel doppelt erscheinen.

## Ziel

Mit echten Importdaten pruefen, welche zusaetzlichen Normalisierungsregeln sinnvoll und sicher
sind.

## Umfang

```text
- http/https Duplikate analysieren
- www/non-www Varianten analysieren
- trailing slash und Query-Varianten analysieren
- domain-spezifische Merge-Regeln bewerten
```

## Akzeptanzkriterien

```text
- Analysequeries dokumentieren die haeufigsten Duplikat-Muster
- Entscheidung dokumentiert, welche Regeln umgesetzt werden
- Unsichere Regeln bleiben bewusst ausgeschlossen
- Falls Regeln geaendert werden: Rebuild-/Backfill-Strategie fuer articles beschrieben
```

## Implementierungskommentar (11.07.2026)

Die Analyse wurde mit 11.554 lokal importierten Artikeln und 59.339 Signalen durchgefuehrt.
Reproduzierbare PostgreSQL-Queries und die detaillierte Auswertung liegen unter
`docs/analysis/ART-006-url-normalization.sql` und
`docs/analysis/ART-006-url-normalization.md`.

Gefunden wurden 17 reine HTTP/HTTPS-Paare auf vier Domains, keine reinen www/non-www-Paare und
30 Basis-URLs mit mehreren Query-Varianten. Viele Query-Parameter sind Content-Identifier und
duerfen nicht entfernt werden. Wegen des kurzen Beobachtungszeitraums und fehlender
Redirect-/Content-Nachweise wird keine neue Normalisierungsregel umgesetzt; unsichere globale
und domain-spezifische Regeln bleiben bewusst ausgeschlossen. Fuer eine spaetere Aenderung ist
eine Rebuild-/Backfill-Strategie dokumentiert.
