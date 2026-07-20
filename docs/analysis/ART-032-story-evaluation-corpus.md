# ART-032: Gelabeltes Story-Evaluationskorpus

## Ergebnis

Das versionierte Korpus `ART-032-corpus.json` enthaelt 46 reale, zwischen dem 12. und 20. Juli
2026 durch die lokale GDELT-Pipeline importierte Artikelreferenzen. Es speichert keine
Artikelvolltexte. Die 108 Paare teilen sich in 33 `SAME_STORY`, 70 `DIFFERENT_STORY` und fuenf
`UNCERTAIN` auf. 27 Negativpaare sind gezielt schwere Folgeereignisse. Zehn Referenz-Stories
enthalten jeweils drei Artikel verschiedener Domains.

Die 45 Kalibrierungspaare und 63 Evaluationspaare verwenden disjunkte Artikel und Stories. Zwei
bzw. drei `UNCERTAIN`-Paare bleiben ausserhalb der Kernmetriken. Die Trennung ist im Validator
erzwungen.

## Reale Daten und Sampling

Der reproduzierbare Sampler liest alle betitelten Artikel im halboffenen Intervall
`2026-07-12T20:15:00Z <= firstSeenAt < 2026-07-20T18:45:00Z`. Beim dokumentierten Lauf waren das
118.003 Artikel. Die Kandidatenpopulation besteht aus den 4.000 kleinsten `(urlHash, id)`-Werten,
gezielten Titelduplikat- und Kurztitel-Strata sowie allen Korpusartikeln; nach Deduplizierung sind
es 4.106 Titel. Das entspricht ART-031s Startregel `24 h + Top-50` und uebergewichtet zugleich
Syndikation, generische Titel und Konfliktfaelle. Der Sampler vergibt niemals Referenzlabels.

Embedding- und lexikalische Baseline erzeugen getrennte Top-50-Listen innerhalb von 24 Stunden.
Embedding-Ranggleichheit wird mit aufsteigendem `urlHash`, lexikalische Ranggleichheit ebenfalls
mit aufsteigendem `urlHash` aufgeloest. Beide verwenden exakt dieselben Korpusartikel und Labels.

## Label- und Dateivertrag

- `SAME_STORY`: gleicher konkreter Vorgang nach den Regeln in `docs/stories.md`.
- `DIFFERENT_STORY`: unterschiedlicher konkreter Vorgang, auch bei kausaler oder thematischer Naehe.
- `UNCERTAIN`: die gespeicherte Evidenz erlaubt keine belastbare Entscheidung; das Paar wird aus
  den Kernmetriken ausgeschlossen.
- `ref` ist aus dem stabilen kanonischen URL-Hash abgeleitet. Lokale Datenbank-IDs sind nicht im
  Korpus enthalten.
- `titleInputHash` ist SHA-256 ueber den mit Unicode NFKC und Whitespace-Kollaps normalisierten
  Titel. Modellkennung, Modellversion und Normalisierungsregel stehen im Korpus und Ergebnis.
- Jede eindeutige Entscheidung hat eine Begruendung; schwierige Entscheidungen enthalten
  zusaetzliche gepruefte Evidenz und Quell-URLs.

Das formale Format steht in `ART-032-corpus.schema.json`; der strengere semantische Validator
`scripts/art032_evaluation.py` prueft zusaetzlich Referenzen, Titel-Hashes, Mindestmengen,
Duplikate und Split-Leakage.

## Besonders schwere Entscheidungen

Drei reale Ereignisketten liefern je neun schwere Negativpaare:

1. Der landesweite Cyclospora-Ausbruch und der spaetere freiwillige Rueckruf von Taylor-Farms-
   Salat sind kausal verbunden, aber Krankheitgeschehen und Rueckrufentscheidung sind getrennte
   konkrete Ereignisse. Die FDA-Ausbruchs- und Rueckrufseiten wurden geprueft.
2. Die Ankuendigung einer 20-Prozent-Gebuehr fuer Hormus-Passagen am 13. Juli und ihr Rueckzug am
   14. Juli sind zwei entgegengesetzte Entscheidungen. Le Monde dokumentiert die Ankuendigung,
   Axios den Rueckzug.
3. Lindsey Grahams Tod und die spaetere Vereidigung seiner Schwester Darline sind kausal
   verbunden, haben aber unterschiedliche dominante Handlungen. Todesmeldung und AP-Bericht zur
   Vereidigung wurden getrennt geprueft.

Diese Kontrolle ist messbar relevant: Der eingefrorene Embedding-Schwellwert fuehrt alle neun
Hormus-Entscheidungspaare faelschlich zusammen.

Zusaetzlich sind drei durch ART-031 vorgepruefte positive Paare mit geringer oder mittlerer
Embedding-Aehnlichkeit vertreten: zwei sprachuebergreifende Meldungen zur Carroll-Zahlung und zur
Balogun/Trump-FIFA-Kontroverse sowie zwei unterschiedlich formulierte Meldungen zur ICE-
Fahrzeugkontrollanweisung. Das englisch-italienische Balogun-Paar erzwingt auf der
Kalibrierungsmenge bewusst eine weniger konservative Schwelle.

### Abdeckung der ART-030-Grenzfaelle

Vertreten sind Updates, Folgeereignisse, gleiche Akteure, gleiche Regionen, widersprechende
Entscheidungen, Syndikation, sprachuebergreifende Berichte und ein mehrdeutiger Rueckblick. Nicht
als eindeutige Labels vertreten sind Sammelartikel, Mehrthemen-Liveblogs, raeumlich verteilte
Sturmereignisse sowie nachweislich widersprechende GDELT-IDs. Im gepinnten Zeitraum liessen sich
diese Kategorien aus den gespeicherten Titeln und Metadaten nicht mit ausreichender Evidenz
belegen. Sie wurden deshalb nicht konstruiert oder automatisch gelabelt und bleiben vorgemerkte
Strata fuer eine spaetere Minor-Version des Korpus.

## Eingefrorene Baselines und Ergebnis

Nur auf der Kalibrierungsmenge wurden folgende Regeln festgelegt und danach nicht mehr veraendert:

```text
lexikalisch: Top-50 innerhalb 24 h; SAME_STORY bei Token-Jaccard >= 0,090909
Embedding:   Top-50 innerhalb 24 h; SAME_STORY bei Cosine >= 0,224867
Modell:      text-embedding-3-small
Version:     openai:text-embedding-3-small@2026-07-20
Input:       art031-title-nfkc-ws-v1
```

| Evaluationsmetrik | Lexikalisch | Embedding |
|---|---:|---:|
| Pairwise Precision | 0,5135 | 0,6786 |
| Pairwise Recall | 1,0000 | 1,0000 |
| Pairwise F1 | 0,6786 | 0,8085 |
| Kandidaten-Recall | 1,0000 | 1,0000 |
| Fehl-Merges | 18 | 9 |
| Singleton-Anteil | 0,0000 | 0,0000 |
| Ausgeschlossene `UNCERTAIN`-Paare | 3 | 3 |

Die sprachuebergreifenden positiven Paare senken die kalibrierte lexikalische Schwelle stark; die
lexikalische Baseline erzeugt dadurch 18 Fehl-Merges. Die Embedding-Baseline ist besser, trennt
aber die geprueften Folgeereignisse ebenfalls nicht sicher. Ein Embedding-MVP darf daher nicht nur
diese Schwelle als Merge-Beweis verwenden; vor Produktimplementierung ist eine deterministische
Zusatzregel fuer Handlungswechsel bzw. widersprechende Entscheidungen erforderlich.

## Metrikdefinitionen

`SAME_STORY` ist die positive Klasse. Pairwise Precision ist `TP / (TP + FP)`, Recall ist
`TP / (TP + FN)` und F1 ihr harmonisches Mittel. Kandidaten-Recall ist der Anteil der positiven
Referenzpaare, die die jeweilige 24-h-Top-50-Suche erreicht. Fehl-Merges sind explizit negative
Paare, deren Endpunkte durch vorhergesagte `SAME_STORY`-Kanten in derselben Komponente landen.
Der Singleton-Anteil ist der Anteil vorhergesagter Komponenten mit genau einem Evaluationsartikel.

## Reproduktion und Versionierung

```powershell
$env:PYTHONPATH = ".art032/site-packages;scripts"
python scripts/sample_art032_candidates.py
python scripts/build_art032_corpus.py
python scripts/art032_evaluation.py docs/analysis/ART-032-corpus.json
python scripts/run_art032_baselines.py --calibrate --output .art032/calibration-results.json
python scripts/run_art032_baselines.py --embedding-threshold 0.224867 --lexical-threshold 0.090909
```

Korrekturen an Metadaten oder Begruendungen erhoehen die Patch-Version. Neue Labels oder
geanderte Entscheidungen erhoehen die Minor-Version. Inkompatible Schema- oder Labelaenderungen
erhoehen Schema- und Major-Version. Eine Korrektur ersetzt keine alte Begruendung stillschweigend:
Sie wird im Commit und in diesem Dokument erklaert, anschliessend werden Kalibrierung und
Evaluation mit einer neuen Korpusversion wiederholt. Die Dateien unter `.art032` sind lokale,
reproduzierbare Caches und werden nicht versioniert.
