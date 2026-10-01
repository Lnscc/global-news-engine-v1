# Tickets

- Offen: `docs/tickets`
- Epics: `docs/tickets/epics`
- Zurueckgestellt: `docs/tickets/backlog`
- Erledigt oder verworfen: `docs/tickets/done`

Ein Epic buendelt mehrere eigenstaendig pruefbare Tickets. Einzelne Aenderungen brauchen kein
Epic. Alles bleibt in Markdown; optionale leere Abschnitte entfallen.

## Ticket

```text
# ART-NNN: Titel
Status: offen | in arbeit | erledigt | verworfen
Bereich: articles | gdelt | stories | operations | architecture
Epic: EPIC-NNN

## Kontext
## Ziel
## Umfang
## Akzeptanzkriterien
## Abgrenzung
## Offene Fragen
```

## Epic

```text
# EPIC-NNN: Titel
Status: offen | in arbeit | erledigt | verworfen
Bereich: articles | gdelt | stories | operations | architecture

## Kontext
## Ziel
## Umfang
## Erfolgskriterien
## Tickets
## Abgrenzung
## Offene Fragen
```

Ablauf: Ticket -> Impact-Analyse -> Implementierungsplan -> Umsetzungskommentar. Status und Ablage
nur auf Wunsch aendern. Backlog-Tickets bleiben `offen` und `zurueckgestellt`.
