# EPIC-002: Themes-MVP

Status: offen
Bereich: stories, architecture

## Kontext

Topics buendeln laenger laufende Zusammenhaenge. Fuer eine uebergeordnete Analyse fehlen noch
strategische Themes, die mehrere Topics und Stories langfristig klassifizieren koennen. Diese
Produkt-Themes sind nicht mit den von GDELT gelieferten Themes gleichzusetzen.

## Ziel

Das Themes-MVP ordnet freigegebene Topics nachvollziehbar zu stabilen, fachlich definierten
Kategorien und stellt den aktuellen Stand per REST API bereit.

## Umfang

- Theme-Begriff, Taxonomie und Abgrenzung zu GDELT-Themes festlegen
- Erstellung, Aenderung und Lebenszyklus von Themes definieren
- Zugehoerigkeit von Topics zu Themes samt Begruendung modellieren
- Qualitaetspruefung, Versionierung und Freigabe automatischer Zuordnungen festlegen
- Theme-Liste und Theme-Detail mit zugehoerigen Topics per REST API bereitstellen
- Architektur, Betrieb und API-Vertrag dokumentieren

## Erfolgskriterien

- strategische Themes besitzen eine verbindliche fachliche Definition und stabile Identitaet
- GDELT-Themes dienen hoechstens als Eingangssignal und werden nicht ungeprueft uebernommen
- automatische Zuordnungen sind reproduzierbar, nachvollziehbar und fachlich evaluierbar
- die erlaubte Mehrfachzuordnung von Topics ist ausdruecklich entschieden
- Aenderungen an Taxonomie oder Regeln werden versioniert und kontrolliert freigegeben
- der aktuelle freigegebene Theme-Stand ist stabil paginiert und per Public ID abrufbar
- REST-Vertrag und relevante Statuscodes sind in Postman getestet

## Tickets

Noch keine.

## Abhaengigkeiten

Das Epic beginnt nach dem Topics-MVP und verwendet dessen freigegebenes Topic-Modell.

## Abgrenzung

Trendberechnung, Alerts, Benutzeroberflaeche, Volltextanalyse und generative Zusammenfassungen
sind nicht Teil dieses Epics.

## Offene Fragen

- Werden Themes kuratiert, automatisch entdeckt oder in einem hybriden Verfahren gepflegt?
- Wer verantwortet Namen, Beschreibung und Aenderungen der Taxonomie?
- Darf ein Topic mehreren Themes angehoeren?
- Werden Stories nur ueber ihre Topics oder auch direkt einem Theme zugeordnet?
- Wie werden neue, zusammengelegte oder nicht mehr verwendete Themes behandelt?
