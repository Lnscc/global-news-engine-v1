# Inhaltliche Story-Stichprobe, 2026-09-26

## Grundlage

Rein lesende Pruefung der aktuellen Mitgliedschaften der 24-h-SHADOW-Version,
Publish-Snapshot 163 vom 2026-09-25 18:24:49 UTC. Die ausgewaehlten Artikel stammen
ueberwiegend vom 2026-09-03. Keine Daten oder Clustering-Regeln wurden veraendert.

Auswahl: 35 grosse Story-Gruppen gesichtet, 45 Mitglieder mit den niedrigsten
Medoid-Scores sowie 25 hoch bewertete storyuebergreifende Paarentscheidungen gelesen.
Die letzten 25 Paare enthalten viele Syndikationsduplikate und sind keine 25
unabhaengigen Faelle. Anschliessend alle gespeicherten Titel von zwoelf ausgewaehlten
Stories gelesen: 296 Mitgliedschaften und 83 innerhalb ihrer Gruppen unterschiedliche
Titel. Dies ist eine gezielte Fehlersuche, keine Zufallsstichprobe oder Qualitaetsquote.

Beurteilt wurden die eingefrorenen normalisierten Titel, Zuordnungen, Zeitabstaende
und gespeicherten Scores. Volltexte und externe Meldungswahrheit wurden nicht geprueft.
Scores sind semantische Aehnlichkeiten, keine Wahrscheinlichkeiten fuer Korrektheit.

## Plausibel zusammengefasst

| Story-ID | Artikel | Beobachtung |
|---|---:|---|
| `388b5ebb-2743-37fe-8c0f-6d29cee6685e` | 36 | Alle vier Titelvarianten beschreiben das Ende der britischen McDonald's-Monopoly-Aktion; Unterschiede betreffen meist den Zeitungssuffix. |
| `f8783d59-84b5-3f49-83e8-2157bd9c07f6` | 90 | Zwei nahezu identische Titelvarianten zur staatlichen Veroeffentlichung von Firmen, die den Mindestlohn unterschreiten. Nach Titellage konsistent. |

## Klare Fehlzusammenfassungen nach Titellage

| Story-ID | Artikel | Gemeinsame Gruppe | Warum getrennt? | Score zum Medoid |
|---|---:|---|---|---:|
| `7f98f60e-0258-37c5-8dcd-86508b1301b6` | 2 | West Ham vs Derby County / West Brom vs Watford | Andere Mannschaften und andere Spiele; gleiches Titelmuster und Portal. | 0.703164 |
| `559868ed-7793-34e5-862b-a9441223a61e` | 2 | Controlled Burn in Brimbin Nature Reserve, Taree / Controlled Burns Set for Garby Reserve, Woolgoolga Headland | Geplante Feuer an unterschiedlichen benannten Orten. | 0.702481 |
| `7a2bb587-bd39-3299-b852-71444a6bf002` | 2 | Wonderful: 550 Mio. Dollar Series C / Lyte: 165 Mio. Dollar Series C | Andere Unternehmen und Finanzierungsrunden. | 0.703938 |

## Keine sinnvolle Ereignis-Story

`91dd5814-9f2b-369c-9489-8702d73b8801`: 39 Artikel mit 39 verschiedenen Titeln,
darunter `About`, `Advertise`, `Our team`, `Gear Reviews`, `Nutrition` und `Job Postings`,
jeweils mit dem Suffix FasterSkier. Auch `Speed Creates the Load, Not Intensity` liegt
in der Gruppe (Score zum Medoid `About – FasterSkier`: 0.708327).
Hier werden Navigations-/Service-Seiten und einzelne Inhalte unter einer Website-Marke
gruppiert. Die Gruppe repraesentiert kein einzelnes Ereignis. Das ist zunaechst ein
Input-/Titelqualitaetsproblem; die einzelnen Inhalte sollten nicht pauschal alle
als unbrauchbar eingestuft werden.

## Wahrscheinlich verpasste Zusammenfuehrungen

| Getrennte Story-IDs | Artikel | Titelbeispiel / Beurteilung | Hoechster gesichteter Paar-Score |
|---|---:|---|---:|
| `774c5c16-a2a6-3111-be99-6659416d00d6` / `013fff7d-6bfc-346b-b078-b432878cc4f0` | 79 + 10 | Schliessung deutscher Kulturzentren/Goethe-Institute in Russland im Streit mit Berlin. Nahezu identische Meldungen auf beiden Seiten; die kleinere Gruppe enthaelt auch deutsche Reaktionen. Mindestens die identischen Kernmeldungen gehoeren sehr wahrscheinlich zusammen. | 0.878457, 15 Minuten Abstand |
| `2a5b8ef6-7e91-3d2a-9c0b-160c08243ffb` / `249351ac-f10d-3843-8f56-ad537f105070` | 5 + 11 | Spaniens Premier: keine Belege fuer eine von Marokko organisierte Ceuta-Migration. Die zweite Gruppe enthaelt auch Aussagen zu fehlenden Vorwarnungen. Starker Kandidat fuer dieselbe Ereignis-Story; ein Liveblog ist gesondert zu beurteilen. | 0.877011, 35 Minuten Abstand |
| `cf4309b3-f26c-3ebe-8347-d4b63180abc2` / `7c13695a-b6f2-36a3-a1aa-e54914ad19e8` | 16 + 4 | WMO-Prognose zu einer Verstaerkung von El Nino und Auswirkungen bis 2027. Die Titel legen dieselbe Prognose nahe; fuer eine definitive Zusammenfuehrung Volltexte/Publikationsanlass vergleichen. | 0.851086, gesichtete Paare mit 0 bzw. 13.200 Sekunden Abstand |

## Einordnung

Die Daten zeigen sowohl falsche Zusammenfassungen als auch wahrscheinlich unnoetige
Trennungen. Ein hoher Score eines einzelnen Paars erzwingt im vereinbarten
Medoid-Radius-Verfahren keinen Gesamtmerge: Der Medoid der Vereinigung muss zu jedem
Mitglied passen. Die konkrete ablehnende Merge-Entscheidung wurde hier nicht neu berechnet.
Die Stichprobe belegt deshalb Qualitaetsprobleme der Ergebnisse, keinen Nachweis eines
Fehlers der atomaren Publikation aus ART-038.

Ein blosses Senken der Schwelle wuerde die bereits sichtbaren Fehlzusammenfassungen
wahrscheinlich verschaerfen; Anheben kann weitere Trennungen verursachen. Sinnvoller
naechster Schritt ist ein kleines gelabeltes Regressionsset aus diesen konkreten Faellen
und eine gesonderte Entscheidung ueber Titelbereinigung, Ausschluss von Navigationsseiten
und Ereignismerkmale wie Mannschaften, Unternehmen oder Orte. Letzteres erweitert den
bisherigen Titel-Embedding-/Zeit-Vertrag und sollte nicht still in ART-038 einfliessen.
