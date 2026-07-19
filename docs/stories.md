# Story-Definition und Clustering-Ziel

## Zweck

Dieses Dokument definiert die fachliche Einheit zwischen Artikeln und Topics. Es ist die
verbindliche Grundlage fuer Datenanalyse, Evaluationskorpus und den spaeteren Story-Clusterer.
Eine technische Aehnlichkeit ist nur ein Hinweis; die Sollzuordnung richtet sich immer nach dem
beschriebenen Geschehen.

```text
GDELT-Signale -> Artikel -> Stories -> Topics -> Themes
```

## Zentrale Definition

Eine **Story** ist die Menge der journalistischen Artikel, deren jeweiliger Hauptgegenstand
dasselbe konkrete, raeumlich und zeitlich abgrenzbare Geschehen ist. Die Artikel duerfen
unterschiedliche Perspektiven, Quellen, Detailstaende und Korrekturen enthalten. Sie gehoeren nur
dann zusammen, wenn ein Leser sie als Berichte oder Aktualisierungen desselben Geschehens und
nicht bloss desselben Sachgebiets verstehen wuerde.

Zwei Artikel gehoeren im MVP genau dann zur selben Story, wenn alle folgenden Aussagen gelten:

1. Ihr dominantes Geschehen ist identisch. Handelnde oder betroffene Entitaeten, wesentliche
   Handlung beziehungsweise Zustandsaenderung und konkreter Anlass stimmen im Kern ueberein.
2. Unterschiede beschreiben Details, Reaktionen oder Erkenntnisse ueber dieses Geschehen; sie
   fuehren kein eigenstaendig berichtenswertes neues Geschehen ein.
3. Widersprechende Fakten koennen als Korrektur oder ungeklaerte Quellenlage verstanden werden.
   Sie bezeichnen nicht zwei verschiedene Vorgaenge.
4. Die Zuordnung laesst sich mit beobachtbaren Merkmalen begruenden. Ein gemeinsames Theme, eine
   Person, Organisation, Domain oder `globalEventId` allein reicht nie aus.

Ort und Zeit sind wichtige Trennsignale, aber keine starren Identitaetsschluessel. Derselbe Vorgang
kann sich ueber mehrere Orte und Tage erstrecken. Umgekehrt koennen am selben Ort zur selben Zeit
mehrere getrennte Geschehen stattfinden.

## Abgrenzung der Begriffe

| Begriff | Fachliche Einheit | Beispiel | Beziehung |
|---|---|---|---|
| Story | Ein konkretes Geschehen und die dazu berichteten Aktualisierungen | Ein bestimmtes Erdbeben und korrigierte Angaben zu diesem Beben | gruppiert Artikel |
| Folgeereignis | Ein spaeteres, eigenstaendig berichtenswertes Geschehen, das kausal oder narrativ an eine Story anschliesst | Regierung beschliesst Wochen nach dem Beben einen Wiederaufbaufonds | eigene Story; kann spaeter mit der Ausgangs-Story in einem Topic verbunden werden |
| Topic | Ein laenger laufender Kontext aus mehreren verwandten Stories | Folgen und Wiederaufbau nach einer Erdbebenserie | gruppiert Stories; nicht Teil des Story-MVP |
| Theme | Eine breite, langfristige analytische Kategorie, die viele Topics und Stories schneiden kann | Naturkatastrophen, Klimaanpassung oder Katastrophenschutz | klassifiziert beziehungsweise aggregiert langfristig; nicht mit einem GKG-Theme gleichzusetzen |

Ein **Folgeereignis** beginnt, sobald eine neue Handlung, Entscheidung, Zustandsaenderung oder ein
neuer Vorfall auch ohne die bisherigen Aktualisierungen eine eigene Meldung tragen kann. Eine
blosse neue Quelle, Reaktion, Opferzahl, Korrektur oder Detailentdeckung zum urspruenglichen
Vorgang bleibt dagegen in derselben Story. Die Ursache-Wirkungs-Beziehung allein verschmilzt zwei
Geschehen nicht.

## Artikelmitgliedschaft im MVP

Ein Artikel gehoert im MVP zu **hoechstens einer Story** oder bleibt unzugeordnet. Entscheidend ist
sein dominantes Geschehen, nicht jede erwaehnte Nebenhandlung. Diese Einschraenkung macht
Zuordnung, Evaluation und Erklaerung eindeutig. Mehrfachmitgliedschaft wird erst nach einer
gemessenen Fehleranalyse neu bewertet.

Sammelartikel, deren Abschnitte mehrere gleichrangige Geschehen behandeln, werden nicht automatisch
zugeordnet. Liveblogs werden nur zugeordnet, wenn ein klar dominantes Geschehen vorliegt; ein
Liveblog mit mehreren unabhaengigen Ereignissen bleibt unzugeordnet. Rueckblicke gehoeren zu einer
Story, wenn sie ueberwiegend ein konkretes Geschehen rekonstruieren. Chroniken, Jahresrueckblicke
und thematische Analysen mit mehreren gleichrangigen Geschehen bleiben unzugeordnet.

Eine Story darf im MVP aus nur einem Artikel bestehen. Ein Singleton ist ehrlicher als eine
erzwungene falsche Zuordnung und bleibt fuer spaet eintreffende Artikel erweiterbar. Produktansichten
duerfen Singletons ausblenden, ohne ihre fachliche Identitaet umzudeuten.

## Zeitbegriffe

| Zeitbegriff | Bedeutung fuer die Story-Bildung |
|---|---|
| Ereigniszeit | Zeitpunkt oder Intervall, in dem das beschriebene Geschehen stattfand; fachlich am staerksten, im aktuellen Artikelmodell aber nicht verlaesslich vorhanden |
| `publishedAt` | vom Artikel angegebener Publikationszeitpunkt; primaerer verfuegbarer Zeitanker, nicht automatisch die Ereigniszeit |
| `firstSeenAt` | fruehester Eingang eines GDELT-Signals zum Artikel; Fallback und Betriebszeit, nicht als Publikations- oder Ereigniszeit umzudeuten |
| Signalzeit | Zeit eines einzelnen EVENTS-, MENTIONS- oder GKG-Signals; beschreibt Beobachtung oder Quellrecord und kann verspaetet sein |
| Story-Zeitspanne | Minimum und Maximum der effektiven Zeitpunkte ihrer Mitglieder; technischer Suchraum, kein Beweis fuer fachliche Zusammengehoerigkeit |

Der effektive Artikelzeitpunkt und konkrete Kandidatenfenster werden in ART-033 festgelegt. ART-031
misst zuvor Abdeckung und Abweichungen. Es gibt in ART-030 deshalb bewusst keine universelle
Stunden- oder Tagesgrenze.

## Mindestinhalt einer Story

Das MVP muss fuer jede Story fachlich mindestens festhalten koennen:

```text
- eine stabile Story-Referenz innerhalb einer Clustering-Version
- mindestens einen Mitgliedsartikel und genau einen repraesentativen Artikel
- den effektiven fruehesten und spaetesten Mitgliedszeitpunkt
- die verwendete Clustering-Regel beziehungsweise -Version
- je automatischer Mitgliedschaft eine maschinenlesbare Zuordnungsbegruendung
```

Der repraesentative Artikel liefert im MVP die anzeigbaren Metadaten. Eine generierte Ueberschrift
oder Zusammenfassung ist kein Mindestinhalt. Das spaetere Persistenzschema und die Regeln fuer
stabile Identitaet, Merge und Split werden erst in ART-033 entschieden.

## Eingangssignale fuer das erste Clustering

Die Einordnung ist eine fachliche Hypothese und wird durch ART-031 an realen Daten geprueft.

### Notwendig

- stabile Artikelidentitaet und kanonische URL, damit dieselbe Quelle nicht mehrfach zaehlt
- nicht leerer, hinreichend ereignisbezogener Titel als primaerer Inhaltsanker
- ein versioniertes Embedding des normalisierten Titels als primaeres semantisches
  Kandidaten- und Aehnlichkeitssignal
- `publishedAt` oder ersatzweise `firstSeenAt` fuer begrenzte und reproduzierbare Kandidatensuche
- deterministisch normalisierte Titel- und Zeitwerte

Artikel ohne verwendbaren Titel bleiben im ersten MVP unzugeordnet; ihre Anzahl ist in ART-031 zu
messen. Fehlende Werte werden nicht aus Domain, TLD oder Theme erfunden.

### Optional und unterstuetzend

- Personen, Organisationen und typisierte Orte aus GKG
- `globalEventId`, Event-Code und die Verbindung ueber EVENTS und MENTIONS
- normalisierte GKG-Themes
- Domain als Diversitaets- und Diagnosemerkmal, nicht als semantisches Match
- Titelmehrfachbeobachtungen und spaet eintreffende Signale als Evidenz mit dokumentierter Herkunft

Diese Signale duerfen Kandidaten bestaetigen oder trennen, aber keines darf allein eine
Story-Zuordnung erzwingen. GDELT-Events sind Quellsignale und nicht mit Stories identisch.

### Spaeter und nur bei nachgewiesenem Bedarf

- extrahierter Volltext und produktive Sprachklassifikation
- Uebersetzung oder weitere sprachuebergreifende Normalisierung ausserhalb des Embedding-Modells
- weitere gelernte semantische Modelle jenseits des festgelegten Titel-Embedding-Modells
- LLM-basierte Extraktion, Zuordnung, Titel oder Zusammenfassung
- externe Knowledge-Graph- oder Crawler-Daten

### Rolle der Titel-Embeddings

Das MVP bildet Embeddings ausschliesslich aus dem deterministisch normalisierten Artikeltitel.
Entitaeten, GDELT-Themes, Domain und generierte Texte werden nicht verdeckt in den Eingabetext
gemischt, sondern bleiben getrennt nachvollziehbare Signale. ART-031 waehlt anhand realer Daten
genau ein fuer die vorhandenen Sprachen geeignetes Modell aus und dokumentiert Modellkennung,
Version, Dimension, Eingabetext und Eingabe-Hash.

Embedding-Aehnlichkeit dient dazu, innerhalb eines fachlich begrenzten Zeitfensters Kandidaten zu
finden und zu ordnen. Eine automatische Verbindung erfordert zusaetzlich die in ART-032
kalibrierte Entscheidungsregel und darf nicht allein aus hoher Cosine Similarity folgen. Fehlende
oder unsichere Evidenz fuehrt konservativ zu einem Singleton oder einem unzugeordneten Artikel.
Volltext-Embeddings und generative LLM-Aufrufe bleiben ausserhalb des MVP.

## Mindestbegruendung einer automatischen Zuordnung

Eine Zuordnung muss ohne erneuten Modellaufruf reproduzierbar erklaeren koennen:

```text
- welche Clustering-Version und normalisierten Eingangswerte verwendet wurden
- welche Embedding-Modellversion, welcher Eingabe-Hash und welche Cosine Similarity verwendet wurden
- welche bestehenden Mitglieder beziehungsweise welcher Repraesentant verglichen wurden
- welche positiven Merkmale die Zuordnung stuetzen, zum Beispiel Titelereignis, Entitaet und Ort
- welche Trennmerkmale geprueft wurden, zum Beispiel abweichende Handlung, Ort oder Zeit
- welcher deterministische Schwellwert oder welche Regel die Entscheidung ausloeste
```

Eine Begruendung wie `gleiches Theme` oder `Aehnlichkeit 0,87` ohne Merkmalsherkunft und
Entscheidungsregel genuegt nicht. Identische Eingaben und dieselbe Version muessen dieselbe
Zuordnung ergeben.

## Grenzfaelle und Sollentscheidungen

Die Beispiele sind Labelregeln fuer ART-032. `UNCERTAIN` bezeichnet einen Fall, den die gegebenen
Metadaten nicht sicher entscheiden lassen; er darf in der Evaluation nicht still als
`DIFFERENT_STORY` gelten.

| Nr. | Artikelpaar oder Artikelform | Sollentscheidung | Begruendung |
|---:|---|---|---|
| 1 | Erstmeldung ueber ein Erdbeben bei Izmir; spaetere Meldung korrigiert die Magnitude desselben Bebens | `SAME_STORY` | Korrektur eines Merkmals desselben konkreten Vorgangs |
| 2 | Erdbeben bei Izmir am Montag; neues Beben in derselben Region zwei Wochen spaeter | `DIFFERENT_STORY` | neuer physischer Vorgang trotz Ort und Thema |
| 3 | Wahlergebnis wird gemeldet; Verlierer erkennt dasselbe Ergebnis am Folgetag an | `SAME_STORY` | unmittelbare Reaktion und Aktualisierung zum dominanten Wahlgeschehen |
| 4 | Wahlergebnis; neue Regierung verabschiedet Monate spaeter ihren ersten Haushalt | `DIFFERENT_STORY` | eigenstaendige Entscheidung, nur kausal nachgelagert |
| 5 | Unternehmen kuendigt eine bestimmte Uebernahme an; Kartellbehoerde untersagt genau diese Uebernahme spaeter | `DIFFERENT_STORY` | das Verbot ist eine neue eigenstaendige Entscheidung und Folgeereignis |
| 6 | Bericht ueber einen konkreten Zugunfall; Update erhoeht nach Bergung die Zahl der Verletzten | `SAME_STORY` | neue Erkenntnis ueber denselben Unfall |
| 7 | Zwei Proteste derselben Bewegung am selben Tag in Berlin und Paris, jeweils separat organisiert | `DIFFERENT_STORY` | verwandtes Topic, aber getrennte konkrete Geschehen |
| 8 | Agenturmeldung und lokale Zeitung berichten denselben Protest mit abweichender Teilnehmerzahl | `SAME_STORY` | Quellenwiderspruch aendert die Ereignisidentitaet nicht |
| 9 | Dieselbe Person tritt als Ministerin zurueck; eine Woche spaeter wird sie wegen eines anderen Vorgangs angeklagt | `DIFFERENT_STORY` | gemeinsame Person, aber neue Handlung und neuer Anlass |
| 10 | Sturm trifft mehrere Orte entlang derselben Zugbahn; Artikel berichten Landfall und unmittelbar folgende Auswirkungen | `SAME_STORY` | ein fortlaufendes, raeumlich ausgedehntes Geschehen |
| 11 | Ein Liveblog behandelt ausschliesslich denselben laufenden Gipfel; Einzelmeldung berichtet einen Beschluss dieses Gipfels | `SAME_STORY`, falls der Gipfel das dominante Geschehen beider Artikel ist | Artikelformat trennt nicht; Hauptgegenstand entscheidet |
| 12 | Nachrichten-Liveblog mischt Wahl, Sport und Wetter; Einzelmeldung berichtet nur die Wahl | Liveblog `UNASSIGNED` | mehrere gleichrangige Geschehen erlauben keine eindeutige Einzelmitgliedschaft |
| 13 | Rueckblick rekonstruiert ausschliesslich einen bestimmten Anschlag; damalige Erstmeldung berichtet denselben Anschlag | `SAME_STORY` | zeitlicher Abstand und Rueckblickformat allein erzeugen kein Folgeereignis |
| 14 | Jahresrueckblick nennt zehn Anschlaege; Erstmeldung betrifft einen davon | Rueckblick `UNASSIGNED` | kein dominantes Einzelgeschehen |
| 15 | Titel nennen dieselbe Organisation und denselben Ort am selben Tag, aber einmal eine Werksschliessung und einmal einen Streik | `DIFFERENT_STORY` | abweichende dominante Handlung trotz starker Oberflaechenaehnlichkeit |
| 16 | Zwei knappe Titel melden eine Explosion in derselben Grossstadt, ohne Ortsteil, Zeitpunkt oder Ursache | `UNCERTAIN` | vorhandene Metadaten belegen weder Identitaet noch Verschiedenheit ausreichend |
| 17 | Gleicher `globalEventId`, aber Titel beschreiben nachweislich zwei verschiedene Vorfaelle | `DIFFERENT_STORY` | GDELT-Zuordnung ist Signal, keine Story-Wahrheit |
| 18 | Unterschiedliche `globalEventId`-Werte, Titel und Entitaeten beschreiben nachweislich denselben Unfall | `SAME_STORY` | technische Event-IDs duerfen denselben Vorgang aufspalten |

Bei mehrdeutigen Produktionsfaellen gilt konservativ: keine Verbindung erzwingen. `UNCERTAIN` ist
ein Evaluationslabel; im produktiven MVP resultiert es in getrennten Singletons oder einem
unzugeordneten Artikel, nicht in einer zufaelligen Zuordnung.

## Nicht-funktionale Ziele

- **Nachvollziehbarkeit:** Jede Mitgliedschaft verweist auf Eingabewerte, Regelversion und
  Entscheidungsevidenz.
- **Reproduzierbarkeit:** Eine festgehaltene Eingabemenge liefert mit derselben Version dieselbe
  Partition, unabhaengig von Laufzeit und Verarbeitungsreihenfolge. Modellkennung,
  Modellversion, Titel-Normalisierung und Eingabe-Hash sind Teil dieser Version.
- **Konservatives Verhalten:** Unsichere Kandidaten werden nicht zur Verbesserung scheinbarer
  Abdeckung zusammengefuehrt.
- **Evaluierbarkeit:** Regeln erzeugen paarweise Entscheidungen, die gegen `SAME_STORY`,
  `DIFFERENT_STORY` und `UNCERTAIN` aus ART-032 geprueft werden koennen.
- **Quellenneutralitaet:** Gleiche Domain ist weder Voraussetzung noch hinreichender Grund; mehrere
  Publisher koennen dieselbe Story abdecken.

## Explizite Nicht-Ziele des MVP

Das erste Story-MVP erstellt keine Topics oder strategischen Themes, keine GKG-Theme-Hierarchie,
keine generativen Titel oder Zusammenfassungen und keine inhaltliche Wahrheitsbewertung. Es nutzt
keinen Artikelvolltext, keine Volltext-Embeddings, keine externen Webseiten und kein generatives
LLM. Das festgelegte Titel-Embedding-Modell ist die einzige gelernte semantische Komponente.
Es loest weder Empfehlungen noch Ranking, Sentimentanalyse, Kausalgraphen oder
Mehrfachmitgliedschaften. Persistenz, REST API, UI, Batch-Verarbeitung sowie Merge-, Split- und
Lebenszyklusregeln liegen ausserhalb von ART-030.

## Konsequenzen fuer die Folgetickets

### ART-031: messbare Datenfragen

- Welcher Anteil der Artikel besitzt einen nicht leeren, nicht generischen Titel und einen
  effektiven Zeitpunkt?
- Wie stark unterscheiden sich `publishedAt`, `firstSeenAt` und Signalzeiten, insbesondere am
  50., 90., 95. und 99. Perzentil?
- Welche Zeitfenster begrenzen Kandidaten, ohne positive Paare der Grenzfallkategorien 1, 3, 6,
  10 und 13 uebermaessig zu verlieren?
- Wie oft widersprechen `globalEventId`, Event-Code, Personen, Organisationen, Orte und Themes
  einer manuell erkennbaren Story-Identitaet?
- Wie viele Sammelartikel, generische Titel und nicht entscheidbare Kandidaten treten in einer
  reproduzierbaren Stichprobe auf?
- Wie trennen sich die Cosine-Similarity-Verteilungen fuer erkennbare positive Paare, harte
  Negativpaare und mehrdeutige Faelle?
- Welchen Kandidaten-Recall, welche Kandidatenmengengroesse, Laufzeit und Kosten ergeben
  Kombinationen aus Zeitfenster und Top-k fuer ein fest versioniertes Titel-Embedding-Modell?

### ART-032: Evaluationskorpus

- Die Grenzfallkategorien Korrektur, Folgeereignis, gleicher Akteur, gleicher Ort, verteiltes
  Geschehen, Sammelartikel, Liveblog, Rueckblick und widersprechende GDELT-IDs werden explizit
  gelabelt.
- Eindeutige Paare erhalten `SAME_STORY` oder `DIFFERENT_STORY`; fehlende Evidenz erhaelt
  `UNCERTAIN` und geht nicht in die Kernmetriken ein.
- Die Ein-Story-Mitgliedschaft wird als paarweise konsistente Partition evaluiert. Zusaetzlich zu
  Precision, Recall und F1 werden erzwungene Fehl-Merges und der Singleton-Anteil ausgewiesen.
- Eine lexikalische Titel-Zeit-Baseline wird mit einer Embedding-Zeit-Baseline und einer Variante
  mit zusaetzlichen strukturierten GDELT-Signalen verglichen.
- Similarity-Schwellwert, Zeitfenster und Top-k werden auf einer getrennten Kalibrierungsmenge
  bestimmt und vor der Evaluation ohne Story-Leakage eingefroren.

### ART-033: Lebenszyklusentscheidungen

ART-033 konkretisiert effektiven Artikelzeitpunkt, Kandidatenfenster, Spaetankunft, stabile
Story-Identitaet, Embedding-Idempotenz, Modell- und Input-Versionierung, deterministische
Vektorkandidatensuche, Regelversionierung sowie Merge und Split. Dabei muss die fachliche Invariante
`hoechstens eine Story je Artikel und Clustering-Version` erhalten bleiben. Eine spaetere
Mehrfachmitgliedschaft ist nur mit einem neuen fachlichen Vertrag und einer Evaluation gegen die
hier beschriebenen Sammel- und Liveblogfaelle zulaessig.
