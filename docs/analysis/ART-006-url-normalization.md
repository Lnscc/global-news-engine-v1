# ART-006: Auswertung der URL-Normalisierung

## Datengrundlage

Die Queries in `ART-006-url-normalization.sql` wurden am 11.07.2026 gegen den lokal
importierten GDELT-Bestand ausgefuehrt. Der Snapshot umfasst 11.554 Artikel und 59.339 Signale;
`first_seen_at` reicht von 08:15 bis 12:30 UTC. Die Ergebnisse sind deshalb eine belastbare
Momentaufnahme, aber noch keine Langzeitbeobachtung.

## Ergebnisse

### HTTP und HTTPS

Es gibt 17 Gruppen mit jeweils genau zwei Artikeln, deren kanonische URL sich nur im Scheme
unterscheidet. Betroffen sind `en.people.cn` (6), `www.educationtimes.com` (5), `www.jpost.com`
(5) und `www.prnewswire.com` (1). Das Muster spricht fuer Duplikate, belegt aber nicht, dass
beide Schemes dauerhaft auf dieselbe Ressource zeigen. Eine globale Zusammenfuehrung bleibt
damit zu riskant; eine Domain-Allowlist waere auf Basis eines einzelnen kurzen Snapshots
ebenfalls verfrueht.

### www und non-www

Es wurde keine exakte Variante gefunden, die sich nur durch `www.` unterscheidet. Daraus folgt
weder eine allgemeine Gleichwertigkeit noch ein Bedarf fuer eine neue Regel.

### Trailing Slash und Query

Die 59.339 Roh-URLs der drei verarbeiteten GDELT-Quellen enthalten 24.782 Pfade mit und 34.557 ohne
abschliessenden Slash. Der bestehende Normalizer entfernt bereits abschliessende Slashes bei
Nicht-Root-Pfaden; im Artikelbestand entstehen dadurch keine getrennten Varianten. Root-Pfade
bleiben korrekt erhalten.

30 Basis-URLs besitzen mehrere Query-Varianten, zusammen 120 zusaetzliche Artikel. Die groessten
Gruppen verwenden Parameter wie `id`, `p`, `nid`, `evid`, `postid`, `update` oder
`liveBlogItemId`, die unterschiedliche Artikel, Posts oder Liveblog-Eintraege identifizieren.
Ein globales Entfernen von Queries wuerde daher nachweislich falsche Merges erzeugen.
Experiment-/Referrer-Parameter wie `_f`, `cexp_id`, `cexp_var`, `source` und `fm` sehen teilweise
entfernbar aus, sind aber nicht domainuebergreifend semantisch eindeutig. Sie bleiben bis zu
einer groesseren, domainbezogenen Auswertung ausgeschlossen.

## Entscheidung

Es wird keine zusaetzliche Produktionsregel umgesetzt. Beibehalten werden die bestehenden
konservativen Regeln: Tracking-Parameter `utm_*`, `fbclid` und `gclid` entfernen, Query sortieren,
Nicht-Root-Trailing-Slashes entfernen sowie Scheme und Host unveraendert getrennt behandeln.

Fuer eine spaetere Freigabe von Scheme-, Host- oder Parameterregeln muessen mehrere
Importzeitraeume dasselbe Domain-Muster zeigen. Scheme- und Host-Regeln brauchen zusaetzlich
einen Redirect-/Content-Gleichheitsnachweis; Parameterregeln muessen pro Domain als
Navigation/Tracking statt als Content-Identifier klassifiziert sein.

## Rebuild-/Backfill-Strategie bei einer spaeteren Regelaenderung

Vor einer Regelaenderung wird die neue kanonische URL fuer alle Roh-URLs in einer separaten
Mapping-Tabelle berechnet und auf Hash-Kollisionen sowie unerwartet grosse Merge-Gruppen
geprueft. Danach werden in einer Transaktion neue Zielartikel angelegt, alle `article_signals`
auf den jeweiligen Zielartikel umgehaengt und `first_seen_at` als Minimum der Quellartikel
gesetzt. Erst nach Mengen-, FK- und Unique-Key-Pruefungen werden alte Artikel entfernt und die
neuen `canonical_url`-/`url_hash`-Werte aktiviert. Alternativ kann bei reproduzierbar vorhandenem
Fachdatenbestand die Artikel-Schicht vollstaendig geleert und mit dem geaenderten Normalizer neu
extrahiert werden. In beiden Faellen sind Backup, Dry Run und Rollback-Punkt Pflicht.
