# EPIC-001: Topics-MVP

Status: offen
Bereich: stories, architecture

## Kontext

Stories gruppieren Artikel ueber dasselbe konkrete Geschehen. Laenger laufende Zusammenhaenge
wie Wiederaufbau, Wahlkampf oder die Entwicklung eines Konflikts bestehen dagegen aus mehreren
eigenstaendigen Stories. Die Zielarchitektur nennt dafuer Topics als naechste Aggregationsstufe.

## Ziel

Das Topics-MVP gruppiert fachlich zusammenhaengende Stories reproduzierbar zu laenger laufenden
Kontexten und stellt den aktuellen freigegebenen Stand nachvollziehbar per REST API bereit.

## Umfang

- Topic-Begriff, Zugehoerigkeitsregeln und Grenzfaelle festlegen
- geeignete Story-Signale und einen unabhaengigen Evaluationsbestand bestimmen
- versionierte, reproduzierbare Topic-Zuordnungen mit Begruendung erzeugen
- Topic-Identitaet, Lebenszyklus und Aktualisierung bei neuen Stories festlegen
- Ergebnisse kontrolliert veroeffentlichen und freigeben
- Topic-Liste und Topic-Detail mit zugehoerigen Stories per REST API bereitstellen
- Architektur, Betrieb und API-Vertrag dokumentieren

## Erfolgskriterien

- mehrere eigenstaendige Stories desselben laenger laufenden Zusammenhangs koennen ein Topic bilden
- nur oberflaechlich verwandte Stories werden nicht allein wegen gemeinsamer Entitaeten, Orte oder
  GDELT-Signale zusammengefasst
- gleiche Eingaben und dieselbe Regelversion erzeugen dasselbe Ergebnis
- jede automatische Zuordnung besitzt eine nachvollziehbare Begruendung
- Qualitaetsgrenzen und fachliche Freigabe verhindern eine ungepruefte Aktivierung
- der aktuelle freigegebene Topic-Stand ist stabil paginiert und per Public ID abrufbar
- REST-Vertrag und relevante Statuscodes sind in Postman getestet

## Tickets

Noch keine.

## Abhaengigkeiten

Das Epic baut auf einer freigegebenen Story-Version und der stabilen Story REST API auf. ART-048
soll vor Beginn der Topic-Implementierung abgeschlossen sein.

## Abgrenzung

Strategische Themes, Trends, Benutzeroberflaeche, Volltextanalyse und generative
Zusammenfassungen sind nicht Teil dieses Epics.

## Offene Fragen

- Darf eine Story mehreren Topics angehoeren?
- Welche zeitliche Reichweite und welcher Lebenszyklus gelten fuer ein Topic?
- Welche Rolle spielen Entitaeten, Orte, Event-Codes, GDELT-Themes und semantische Aehnlichkeit?
- Wie bleiben Topic-Identitaeten bei einer neuen Gruppierungsversion stabil?
- Wie erhalten Topics ohne generative Texte einen verstaendlichen Titel?
