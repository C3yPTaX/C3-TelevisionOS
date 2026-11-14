# Hinweise für die Weiterentwicklung

Gilt für Menschen und KI-Assistenten. Überblick: [README.md](README.md), Messwerte und
Grenzen des Geräts: [docs/02-device-findings.md](docs/02-device-findings.md).

## Versionen und Releases

- Einzige Versionsquelle ist `VERSION` (x.y.z). Die App liest sie beim Bauen
  (`versionCode = x*10000 + y*100 + z`), der Updater bekommt sie ins Programm.
- Bei jeder Änderung, die nach `main` geht, `VERSION` erhöhen und `CHANGELOG.md` mit einem
  Abschnitt `## x.y.z` ergänzen.
- Jeder neue Stand von `main` mit neuer Versionsnummer wird automatisch als Release
  `vX.Y.Z` veröffentlicht (ZIP + APK + Updater). Eine vorhandene Version wird nie
  überschrieben.
- Jeder Push ersetzt zusätzlich das Vorab-Release `c3-latest` (Entwicklerversion).
- Pull Requests bauen und prüfen nur das ZIP, sie veröffentlichen nichts.

## Regeln

- Arbeit in eigenen Branches, `main` nur über Pull Request.
- Funktionierende Logik nicht ohne Auftrag verändern; Vorschläge erst nach Zustimmung umsetzen.
- Texte der App und der Dokumentation sind deutsch.

## Umgang mit Systemeingriffen auf dem Testgerät

Der TV ist ein ausdrücklich freigegebenes Experimentier- und Testgerät im Eigentum des
Betreibers. Die früheren pauschalen Verbote (kein Root, keine Firmware-Änderung, keine
Systemmodifikation) gelten nicht mehr. An ihre Stelle treten diese Grundsätze:

- Experimentelle Systemeingriffe auf ausdrücklich freigegebenen Testgeräten sind zulässig.
- Risiken und Auswirkungen werden vor einer Durchführung technisch bewertet.
- Reversible Verfahren werden bevorzugt, sind aber keine zwingende Voraussetzung.
- Irreversible Eingriffe benötigen die ausdrückliche Freigabe des Betreibers.
- Vorhandene Wiederherstellungsmöglichkeiten werden dokumentiert.
- Keine riskanten Änderungen ohne vorherige Untersuchung der tatsächlichen Gerätehardware.

Konkret für KI-Assistenten: zuerst lesende Diagnose und Recherche, Befunde nach *verifiziert /
Vermutung / ungetestet* trennen, Risiko und Rückweg benennen – und bei irreversiblen Schritten
(Firmware, Bootloader, kritische Systemdateien) vor der Ausführung die Freigabe einholen.
