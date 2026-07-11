# ART-006: URL-Normalisierung mit echten Daten pruefen

Status: offen
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
