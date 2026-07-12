# Tickets

Geplante Arbeiten liegen als einzelne Markdown-Dateien in diesem Verzeichnis.

## Struktur

- Offene Tickets: `docs/tickets`
- Zurueckgestellte Tickets: `docs/tickets/backlog`
- Abgeschlossene Tickets (`erledigt` oder `verworfen`): `docs/tickets/done`

## Konvention

```text
Status: offen | in arbeit | erledigt | verworfen
Bereich: articles | gdelt | stories | operations | architecture
```

Tickets im Backlog behalten den Status `offen`, tragen aber eine explizite Prioritaet
`zurueckgestellt`. Sie werden erst wieder in `docs/tickets` verschoben, wenn ihre Umsetzung
konkret geplant ist.
