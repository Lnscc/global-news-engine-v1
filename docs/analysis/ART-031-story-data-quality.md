# ART-031: Datenqualitaet fuer Story-Clustering

## Ergebnis

Der vorhandene Titel ist als primaerer MVP-Inhalt geeignet, aber weder ein einzelner
Cosine-Schwellwert noch ein GDELT-Signal ist Story-Wahrheit. `publishedAt` beschreibt den
fachlichen Ereigniszeitraum oft besser, liegt jedoch nur fuer 39.409 von 80.080 Artikeln
(49,2120 %) vor und enthaelt reale Archivzeitpunkte. Die reproduzierbare Kandidaten-Baseline nutzt
deshalb das vollstaendige, ingestnahe `firstSeenAt` als Zeitanker; ART-033 legt den effektiven
Produktivzeitpunkt und eine moegliche Fallback-Regel fest. Als genau ein Modell fuer ART-032 wird nach dem
direkten Vergleich `text-embedding-3-small` empfohlen. Ausschlaggebend sind der identische
24-h-Top-50-Recall aller Modelle, die bessere Positiv-vs.-Hard-Negative-AUC von OpenAI sowie der
deutlich einfachere Betrieb bei fuer das erwartete Volumen vernachlaessigbaren API-Kosten. Der
Top-10-Nachteil gegenueber MiniLM entspricht in diesem kleinen Test zwei von 20 Suchrichtungen und
wird fuer die gewaehlte Top-50-Kandidatensuche nicht als entscheidend gewertet.

Die Empfehlung ist eine Kandidaten-Baseline, keine Zuordnungsregel: Bei `text-embedding-3-small`
ist der kleinste gemessene positive Cosine-Wert 0,540, waehrend ein harter Negativfall 0,560 und
ein mehrdeutiger Fall 0,876 erreicht. Die Verteilungen ueberlappen also weiterhin.

## Reproduzierbarkeit und Datenstand

Die Stichprobe enthaelt alle Artikel mit
`2026-07-12T20:15:00Z <= first_seen_at < 2026-07-17T20:00:00Z`. Das halboffene UTC-Intervall
enthaelt 80.080 Artikel aus 7.255 Domains. Fuer die Embedding-Messung werden deterministisch die
4.000 kleinsten `(url_hash, id)`-Werte unter den betitelten Artikeln sowie alle 25 gelabelten
Paarbeispiele ausgewaehlt; nach Deduplizierung sind das 4.035 Titel.

Der beim dokumentierten Lauf gelesene Datenstand lautet:

```text
articles=103509, maxArticleId=103551
gdelt_events=85237
gdelt_mentions=243839
gdelt_gkg=102808
maxParsedAt=2026-07-19T17:55:08.725764Z
```

Die vollstaendigen Rohzahlen, Perzentile, Paare und Laufzeitwerte stehen in
`docs/analysis/ART-031-results.json`. Die vorlaeufigen Labels stehen in
`docs/analysis/ART-031-pairs.csv`. Es wurden keine Artikelseiten oder sonstigen externen
Webseiten abgerufen. Einmalig wird der festgeschriebene lokale Modell-Snapshot aus dem
Modell-Registry bezogen. Fuer den autorisierten Vergleich wurden ausschliesslich die 4.035
normalisierten Titel an die OpenAI Embeddings API uebertragen; der API-Key wird weder geloggt noch
in einem Analyseartefakt gespeichert.

Reproduktion mit Python 3.12 und dem lokal erreichbaren PostgreSQL aus `compose.yaml`:

```powershell
python -m venv .art031
.\.art031\Scripts\python.exe -m pip install -r scripts\art031-requirements-lock.txt
.\.art031\Scripts\python.exe -c "from huggingface_hub import snapshot_download; snapshot_download(repo_id='sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2', revision='e8f8c211226b894fcb81acc59f3b34ba3efd5f42', local_dir='.art031/models/paraphrase-multilingual-MiniLM-L12-v2')"
$env:OPENAI_API_KEY = [System.Net.NetworkCredential]::new('', (Read-Host 'OpenAI API key' -AsSecureString)).Password
.\.art031\Scripts\python.exe scripts\run_art031_analysis.py --openai --openai-large
Remove-Item Env:OPENAI_API_KEY
```

Der DSN ist standardmaessig `postgresql://gne:gne@localhost:5432/gne` und kann mit
`ART031_DSN` ersetzt werden. `.art031` ist ignoriert; dort liegt auch ein anhand von Modell,
Artikel-IDs und Input-Hashes validierter OpenAI-Cache. Das versionierte JSON ist die Ausgabe.

## Abdeckung

| Merkmal | Artikel | Anteil |
|---|---:|---:|
| nicht leerer Titel | 79.872 | 99,7403 % |
| `publishedAt` | 39.409 | 49,2120 % |
| `firstSeenAt` | 80.080 | 100,0000 % |
| EVENTS | 12.594 | 15,7268 % |
| MENTIONS | 29.644 | 37,0180 % |
| GKG | 80.038 | 99,9476 % |
| Themes | 71.171 | 88,8749 % |
| Personen | 57.767 | 72,1366 % |
| Organisationen | 59.666 | 74,5080 % |
| Orte | 61.918 | 77,3202 % |

Die Tageswerte sind fuer Titel und GKG stabil, nicht aber fuer den Importbetrieb: Am 15. und
16. Juli liegen im festgelegten Fenster null Artikel. An den Tagen mit Daten reicht die
EVENTS-Abdeckung von 12,5890 bis 18,0220 % und MENTIONS von 30,2022 bis 43,0017 %. Das Ergebnis
ist deshalb eine belastbare Analyse dieses lokalen Datenstands, aber keine Schaetzung eines
kontinuierlichen Welt-Nachrichtenstroms.

## Titelqualitaet

Von 79.872 Titeln sind nach deterministischer Normalisierung 56.467 verschieden. 28.027 Zeilen
(35,09 %) liegen in 4.622 Duplikatgruppen; nach Abzug je eines Originals bleiben 23.405
ueberzaehlige Duplikatzeilen (29,30 %). Das ist ueberwiegend Syndikation und zeigt, dass die
Kandidatensuche nicht nur Domain-Diversitaet, sondern viele semantisch identische Titel behandeln
muss.

Eine bewusst enge, versionierte Generik-Liste findet 300 Titel (0,3756 %), darunter 263-mal
`Targeted News Service` und 18-mal `Deadline`. 1.778 Titel (2,226 %) haben weniger als vier whitespace-getrennte Tokens.
Kurze Titel sind nicht automatisch generisch; beide Gruppen muessen in ART-032 getrennt gelabelt
werden. 208 Artikel (0,2597 %) haben keinen Titel und bleiben im MVP unzugeordnet.

Die Stichprobe enthaelt zudem bereits fehlerhafte Zeichencodierung, etwa `versÃ©` statt `versé`.
Die Eingabenormalisierung repariert das nicht heuristisch, weil eine solche Reparatur nicht
deterministisch sicher waere.

## Zeitqualitaet und Spaetankunft

Bei vorhandener Publikationszeit liegt `publishedAt` im Median 2,10 Stunden und am 95. Perzentil
12,63 Stunden vor `firstSeenAt`; das 99. Perzentil des absoluten Abstands ist 270,93 Stunden.
1.068 von 39.409 Werten (2,7100 %) liegen mehr als 24 Stunden und 422 (1,0708 %) mehr als sieben
Tage vor `firstSeenAt`. Der Maximalabstand von 427.604 Stunden gehoert zu einem tatsaechlichen
Archiv-Titel (`October 5, 1977 - The Equity`) und darf nicht pauschal als kaputter Zeitstempel
verworfen werden. 408 Werte (1,0353 %) liegen bis maximal 15 Minuten nach `firstSeenAt`, passend
zu den 15-Minuten-Quellfenstern.

Nach Quellzeit liegen bei 99 % der Artikel alle Signale im selben Fenster; maximal werden noch
zwei Stunden spaeter datierte Signale beobachtet. Nach realer Ingestion ist Spaetankunft deutlich:

| Messung | Median | P90 | P95 | P99 | Maximum |
|---|---:|---:|---:|---:|---:|
| letztes minus erstes ingestiertes Signal | 0 h | 1,063 h | 20,770 h | 60,724 h | 62,723 h |
| Titel verfuegbar nach erstem Signal | 0 h | 1,063 h | 20,770 h | 60,724 h | 60,868 h |
| Publikationsmetadaten verfuegbar nach erstem Signal | 0 h | 1,319 h | 22,277 h | 60,724 h | 60,868 h |

1.909 von 80.080 Artikeln (2,3839 %) erhalten noch nach mehr als 24 Stunden ein Signal. Titel
kommen bei 1.892 von 79.872 betitelten Artikeln (2,3688 %) und Publikationsmetadaten bei 1.023 von
39.409 Artikeln mit `publishedAt` (2,5959 %) erst nach mehr als 24 Stunden. ART-033 braucht daher
Retry beziehungsweise Revisit mindestens nach 24 Stunden und einen spaeteren Reparaturlauf; ein
bei Erstsichtung fehlender Titel darf nicht als dauerhaft fehlend gelten.

## Strukturierte Signale

EVENTS und MENTIONS sind weder vollstaendig noch stabil genug fuer primaere Identitaet. Ueber beide
Signaltypen haben 22.485 Artikel (28,0782 %) mehr als eine `globalEventId`. Bei EVENTS haben 8.972
(11,2038 %) mehrere IDs und 7.170 (8,9535 %) mehrere `eventCode`-Werte. Am 99. Perzentil stehen je
Artikel 26 verschiedene kombinierte Event-IDs und 5 Event-Codes, die Maxima sind 189 und 26.

Entitaeten sind haeufiger und einzelne Werte oft trennscharf: Der Median der Artikelhaeufigkeit je
Person, Organisation und Ort ist eins. Sie sind trotzdem unvollstaendig und nicht normalisiert als
Story-Identitaet. Das haeufigste Theme erscheint in 62.261 Artikeln (77,75 %), der haeufigste Ort
in 15.253 (19,05 %), die haeufigste Organisation in 6.827 (8,53 %) und die haeufigste Person in
4.665 (5,83 %). Vor allem Themes sind daher breite Bestaetigungs- oder Diagnosemerkmale, keine
Trennschluessel.

## Artikelvolumen und Kandidatenmengen

Die importierten Fenster sind stark gebuendelt. Ueber alle 479 Viertelstunden des Samples ist der
Median null, P95 sind 1.560 und das Maximum 3.921 Artikel. Bei Stundenfenstern ist der Median
ebenfalls null, P95 sind 5.093 und das Maximum 12.268. Ein reines symmetrisches Zeitfenster erzeugt
fuer einen betitelten Artikel folgende Kandidatenmengen:

| Zeitfenster | Median | P90 | P99 | Maximum |
|---|---:|---:|---:|---:|
| +/- 12 h | 25.248 | 29.397 | 29.397 | 29.397 |
| +/- 24 h | 30.245 | 45.380 | 51.373 | 51.373 |
| +/- 72 h | 54.646 | 79.871 | 79.871 | 79.871 |

Zeit allein begrenzt die lokale Kandidatenmenge also nicht sinnvoll. Eine Vektorsuche mit festem
Top-k ist zwingend; die Werte sind ausserdem wegen der Importluecken vor einer Kapazitaetsplanung
mit einem kontinuierlichen Datenlauf zu wiederholen.

## Embedding-Messung

### Eingabe und Modell

`art031-title-nfkc-ws-v1` dekodiert HTML5-Entities, wendet Unicode NFKC an, ersetzt jede Folge von
Unicode-Whitespace durch genau ein U+0020, trimmt und behaelt Gross-/Kleinschreibung sowie
Interpunktion. Der Hash ist der kleingeschriebene Hex-Wert von SHA-256 ueber die UTF-8-Bytes des
normalisierten Titels. Beispiel:

```text
raw:     "  Caf&eacute;\n  update  "
input:   "Café update"
sha256:  839f7749f479b15cf6ff14fa937382119eba8448f6843b11309f80349d84fefb
```

Lokal gemessen wurde genau das 384-dimensionale Modell
`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, Revision
`e8f8c211226b894fcb81acc59f3b34ba3efd5f42`, mit Sentence Transformers 5.1.2, Python 3.12.13,
CPU auf Windows 11 und normalisierten Ausgabevektoren. Als kontrollierte Alternativen wurden
`text-embedding-3-small` mit 1.536 und `text-embedding-3-large` mit 3.072 Dimensionen ueber die
OpenAI API auf exakt denselben Eingaben gemessen. Die API lieferte jeweils dieselbe Modellkennung
wie angefordert; eine unveraenderliche Snapshot-Revision stellen diese Modellkennungen nicht
bereit. Alle Vektorsaetze wurden vor dem Cosine-Scoring defensiv L2-normalisiert.

### Paare und Similarity-Verteilungen

Die 10 positiven Paare, 10 harten Negativpaare und 5 mehrdeutigen Paare sind vorlaeufige,
title-only Labels fuer diese Analyse. Sie ersetzen nicht den unabhaengigen Korpus aus ART-032.
Die folgende erste Verteilungstabelle zeigt die lokal gemessenen MiniLM-Werte; der direkte
Modellvergleich folgt darunter.

| Klasse | n | Minimum | Median | P90 | Maximum |
|---|---:|---:|---:|---:|---:|
| positiv | 10 | 0,543 | 0,793 | 0,954 | 1,000 |
| harter Negativfall | 10 | 0,070 | 0,289 | 0,452 | 0,647 |
| mehrdeutig | 5 | 0,161 | 0,449 | 0,795 | 0,820 |

Die positiven Beispiele umfassen Paraphrasen zum Bangkok-Pubbrand, zur Widdecombe-Ermittlung,
zum Cairngorms-Brand, zum Burberry-Quartal, zur Hormuz-Gebuehr, zu ICE-Fahrzeugkontrollen und zum
Yellowstone-Bisonangriff, ein exaktes Syndikationsduplikat sowie Englisch-Franzoesisch
(Carroll-Zahlung, 0,543) und Englisch-Italienisch (Balogun/FIFA, 0,604). Die harten Negativfaelle
teilen gezielt Person, Region, Organisation, Handlung oder Ereignistyp. Besonders wichtig ist das
Paar zweier staatlicher Cyclospora-Meldungen mit 0,647: Es liegt ueber zwei positiven
sprachuebergreifenden Paaren. Alle Titel, Begruendungen und Einzelwerte stehen in den Paar- und
Ergebnisdateien.

Konkretes Verhalten der Sonderfaelle:

- Das exakte Syndikationsduplikat P09 erreicht 1,000 und ein nahezu identisches
  Widdecombe-Paar P02 0,949. Das Modell erkennt diese Oberflaechenaehnlichkeit erwartungsgemaess.
- Der generische Titel `Deadline` kommt bei mindestens 18 verschiedenen Artikeln vor. Identische
  Eingaben haben zwangslaeufig Cosine 1,000, ohne damit dasselbe Geschehen zu belegen. Generische
  Titel muessen vor der Kandidatensuche ausgeschlossen werden.
- Reale sehr kurze Werte wie `Health`, `CKIA` oder `NPR News` tragen zu wenig Ereignisinhalt und
  bleiben unzugeordnet. Dagegen ist `辉发河畔两昼夜` trotz nur eines whitespace-getrennten Tokens
  nicht automatisch kurz oder generisch. Eine reine Wortzahl darf CJK-Titel nicht verwerfen.
- Das Englisch-Franzoesisch-Paar P03 (0,543) und das Englisch-Italienisch-Paar P05 (0,604) werden
  schwächer als ein englischer harter Negativfall N09 (0,647) abgebildet. Mehrsprachigkeit ist
  grundsaetzlich vorhanden, erfordert aber bewusstes Sampling und Zusatzsignale.

### Recall und Laufzeit

Alle hier ausgewiesenen Zeitfenster, Kandidatenmengen und Recall-Werte verwenden `firstSeenAt`.
Damit stimmen die Messung und die empfohlene ART-032-Kandidaten-Baseline ueberein. `publishedAt`
wird wegen seiner 49,2120-%-Abdeckung und realer Archivzeitpunkte in ART-031 als Diagnose- und
Zusatzsignal bewertet, nicht stillschweigend als gemischter Zeitanker eingesetzt.

Jedes positive Paar wird in beide Richtungen abgefragt, also 20 Queries. Innerhalb des
4.035-Titel-Samples ergibt sich:

| Zeitfenster / Top-k | Treffer | Recall |
|---|---:|---:|
| 12 h / 10 | 16/20 | 0,80 |
| 12 h / 50 | 18/20 | 0,90 |
| 24 h / 10 | 18/20 | 0,90 |
| 24 h / 50 | 20/20 | 1,00 |
| 72 h / 10 | 18/20 | 0,90 |
| 72 h / 50 | 20/20 | 1,00 |

`24 h + top-50` ist damit das begruendete Start-Sampling fuer ART-032. `72 h + top-50` bringt auf
diesen Positivfaellen keinen Recall-Gewinn, vergroessert aber den zeitlichen Rohraum stark.

Batch-Groesse 64 verarbeitete 4.035 Titel in 8,759 Sekunden, entsprechend 460,7 Titel/s. Es gab
0 Fehler (0,0000 %). Linear hochgerechnet benoetigen alle 79.872 betitelten Sample-Artikel rund
173,4 Sekunden und eine Million Titel rund 36,2 Minuten auf derselben CPU. Der externe API-Preis
ist exakt 0,00 USD, weil das Modell lokal laeuft; Strom- und Hardwarekosten wurden nicht als
Scheingenauigkeit erfunden. Fuer ART-033 sind Batch 64, idempotente Wiederaufnahme pro Input-Hash
und ein Retry nur fuer technische Batchfehler sinnvolle Ausgangswerte.

### Kontrollvergleich mit OpenAI

Beide OpenAI-Modelle wurden auf demselben 4.035-Titel-Korpus und denselben 25 Paarlabels
ausgefuehrt. Je Modell waren das 16 Requests mit Batch-Groesse 256 und 70.698 Input-Tokens. Es gab
keine terminalen Fehler; der Client war fuer maximal zwei automatische Retries konfiguriert.
`small` kostete bei 0,02 USD je Million Input-Tokens 0,00141396 USD, `large` bei 0,13 USD
0,00919074 USD.

| Messwert | MiniLM | OpenAI `small` | OpenAI `large` |
|---|---:|---:|---:|
| Dimensionen | 384 | 1.536 | 3.072 |
| Positiv-vs.-Hard-Negative AUC | 0,97 | 0,99 | 0,99 |
| Minimum positiv minus Maximum hart-negativ | -0,104 | -0,019 | -0,021 |
| 12 h / Top-10 Recall | 0,80 | 0,75 | 0,80 |
| 24 h / Top-10 Recall | 0,90 | 0,80 | 0,85 |
| 72 h / Top-10 Recall | 0,90 | 0,80 | 0,85 |
| 12 h / Top-50 Recall | 0,90 | 0,90 | 0,90 |
| 24 h / Top-50 Recall | 1,00 | 1,00 | 1,00 |
| 72 h / Top-50 Recall | 1,00 | 1,00 | 1,00 |
| gemessene Laufzeit | 8,759 s | 35,653 s | 54,286 s |
| externe Kosten dieses Laufs | 0 USD | 0,00141396 USD | 0,00919074 USD |

`large` verbessert gegenueber `small` den Top-10-Recall und das Englisch-Franzoesisch-Paar P03
von 0,593 auf 0,656; das Englisch-Italienisch-Paar P05 bleibt mit 0,599 knapp unter MiniLM
(0,604). Beide OpenAI-Modelle trennen die kleinen, klar gelabelten Positiv- und Negativmengen
besser, erreichen aber weniger positive Treffer unter den ersten zehn Kandidaten als MiniLM. Bei
Top-50 sind alle drei Modelle ab 24 Stunden vollstaendig.

Fuer die produktseitig priorisierte Einfachheit wird `text-embedding-3-small` als genau ein Modell
fuer ART-032 festgelegt. Bei 24 h und Top-50 erreichen alle drei Modelle 20/20 Treffer; OpenAI
erreicht zugleich die bessere Positiv-vs.-Hard-Negative-AUC von 0,99. MiniLMs 24-h-Top-10-Vorteil
von 18/20 gegenueber 16/20 ist auf diesem kleinen Labelsatz nicht stark genug, um lokalen
Modellbetrieb zu rechtfertigen. Der gemessene Small-Lauf kostete 0,00141396 USD fuer 4.035 Titel;
beim erwarteten Volumen von 1.000 neuen Artikeln je 15 Minuten sind auf Basis der gemessenen
17,52 Tokens je Titel rund 1,01 USD pro 30 Tage zu erwarten. ART-032 validiert diese Festlegung auf
1.000 unabhaengig gelabelten Paaren, ohne erneut eine parallele Modellauswahl aufzubauen.

## Feature-Einordnung

| Einordnung | Features | Begruendung |
|---|---|---|
| primaer | normalisierter Titel, versioniertes Titel-Embedding | 99,7403 % Abdeckung; beste inhaltliche Trennung, aber kein alleiniger Merge-Beweis |
| primaer | `firstSeenAt` fuer das Kandidatenfenster | 100 % Abdeckung, stabiler Ingestzeitpunkt und exakt die in Recall und Kandidatenmengen gemessene Zeitbasis |
| unterstuetzend | `publishedAt` | fachlich wertvoll fuer Ereigniszeit und Archivdiagnose, aber nur 49,2120 % Abdeckung; die produktive Fallback-Regel entscheidet ART-033 |
| unterstuetzend | Personen, Organisationen, Orte | oft selten und trennscharf, aber nur 72-77 % Abdeckung und ohne Story-gerechte Alias-Normalisierung |
| unterstuetzend | `globalEventId`, `eventCode`, EVENTS, MENTIONS | geringe Abdeckung und hohe Mehrfachheit; gut fuer Begruendung und Konflikt-Sampling |
| unterstuetzend | GKG-Themes | hohe Abdeckung, aber einzelne Themes sind extrem breit |
| Diagnose | Domain, Duplikatgruppe, Ingestionszeit | wichtig fuer Diversitaet, Syndikation und Spaetankunft, nicht fuer semantische Identitaet |
| MVP-ungeeignet | einzelner Similarity-Schwellwert | positive, negative und mehrdeutige Verteilungen ueberlappen |
| MVP-ungeeignet | `publishedAt`, Domain, Theme oder Event-ID allein | jeweils unvollstaendig, breit oder fachlich kein Story-Schluessel |
| MVP-ungeeignet | erratene Sprache oder Mojibake-Reparatur | Sprache ist nicht persistiert; heuristische Reparatur wuerde Werte erfinden |

## Konsequenzen und Datenbedarf

ART-032 soll mit `24 h + top-50` beginnen und 1.000 Paare gezielt labeln: 400 Paare aus dem
Similarity-Ueberlappungsbereich 0,50-0,85, 200 sprachuebergreifende oder kodierungsauffaellige
Paare, 200 Konfliktfaelle zwischen Embedding und strukturierten Signalen, 100 kurze/generische
Titel und 100 klare Positiv-/Negativkontrollen. `UNCERTAIN` bleibt ein eigenes Label.

Konkrete Datenluecken und Folgeschritte:

- Ein Operations-Lauf soll mindestens sieben lueckenlose Tage importieren und danach Volumen,
  Spaetankunft und Kandidatenmengen erneut messen. Das ist Pipeline-/Sampling-Arbeit, kein
  automatischer Anlass fuer ART-010.
- ART-032 annotiert Sprache manuell im Korpus; eine produktive Sprachspalte gehoert weiterhin in
  den Umfang von ART-015 und wird hier nicht vorweggenommen.
- Die 208 titellosen Artikel bleiben im MVP unzugeordnet. Erst eine separate Fehleranalyse darf
  entscheiden, ob Crawler-Daten aus ART-010 benoetigt werden.
- Mojibake-Faelle werden als eigener Korpus-Stratum und spaeter als Ingestionsqualitaetsproblem
  verfolgt; die Embedding-Normalisierung veraendert sie nicht verdeckt.
- ART-033 muss spaete Titel/Metadaten durch Revisit und idempotentes Re-Embedding abdecken und darf
  Archivartikel nicht allein nach `firstSeenAt` in aktuelle Stories mischen.

Es wurden weder produktives Datenbankschema noch Datenwerte oder REST-Vertrag veraendert.
