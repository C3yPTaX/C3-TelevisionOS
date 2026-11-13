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
- Keine Firmware-Änderung, kein Root, keine Deaktivierung kritischer Systempakete,
  jede Änderung am TV mit dokumentiertem Rückweg.
- Funktionierende Logik nicht ohne Auftrag verändern; Vorschläge erst nach Zustimmung umsetzen.
- Texte der App und der Dokumentation sind deutsch.
