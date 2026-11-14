# C3 Television OS

Eigene, schlanke TV-Oberfläche für den **Xiaomi TV F Pro 75** (Fire OS 8 / Android 11).
Läuft als normale App auf Fire OS: keine Firmware-Änderung, kein Root, jederzeit rückgängig.

## Funktionen

| Bereich | Was es kann |
| --- | --- |
| **Favoriten** | eigene Reihenfolge; Netflix, Prime Video, YouTube, Twitch, Disney+, RTL+ sind vorbelegt |
| **Alle Apps** | jede installierte App starten, zu Favoriten hinzufügen oder ausblenden |
| **Geräte** | PlayStation 5 und HDMI-Eingänge im Vollbild, PCs per Moonlight/Sunshine (C3 Desktop) mit Online-Status |
| **Home-Taste** | führt zu C3, zweimal drücken führt zu Amazon |
| **Privat** | Browser und eigene Seiten, nur sichtbar, solange im Updater eingeschaltet |
| **An C3 TV senden** | Links vom iPhone per Kurzbefehl am TV öffnen (nur bei „Privat an“) |
| **Systemleiste** | Einstellungen, Netzwerk, Bluetooth, Geräteinfo, Fire TV |

Auf dem Homescreen öffnet die **Menü-Taste ☰** die Diagnose.
**OK lang drücken** auf einer App öffnet ihre Optionen (Verschieben, Favorit, Ausblenden).
Ausgeblendete Apps holt die letzte Kachel unter „Alle Apps“ zurück.

## Installation

1. Am TV das ADB-Debugging einschalten. Auf dem PC adb installieren: `winget install Google.PlatformTools`
2. `C3-Updater.exe` aus dem [neuesten Release](https://github.com/C3yPTaX/C3-TelevisionOS/releases/latest) laden.
   Am besten auf den Desktop oder in „Dokumente“ legen, nicht unter „Programme“.
3. Im Updater die IP des TV eintragen und **TV aktualisieren** klicken.

Danach aktualisiert sich der Updater über **„Updater aktualisieren“** selbst.
Mit dem Haken **Entwicklerversion** nutzt er den Testkanal
[`c3-latest`](https://github.com/C3yPTaX/C3-TelevisionOS/releases/tag/c3-latest).

## Der C3 Updater (Windows)

- **Fernseher:** Geräte speichern, C3 installieren, aktualisieren und starten
- **Home-Taste:** Umleitung zu C3 an oder aus
- **C3 Desktop:** diesen PC mit seiner Sunshine-App als Kachel an den TV senden
- **Privat:** Seiten verwalten (auf dem PC verschlüsselt gespeichert), **Privat an**, **Privat aus**
  (löscht die Silk-Daten), iPhone für „An C3 TV senden“ einrichten
- **Sunshine:** erkennen und per winget installieren

## Rückweg

```
adb uninstall de.c3.tv
```

Damit ist alles entfernt. Fire OS und der Amazon-Homescreen bleiben dabei unverändert.

## Projekt

| Pfad | Inhalt |
| --- | --- |
| `app/` | TV-App (Kotlin, ohne Bibliotheken, Paket `de.c3.tv`) |
| `tools/updater/` | C3 Updater (C#, .NET 4, Windows) |
| `docs/02-device-findings.md` | Messwerte und Grenzen des Geräts, **vor Änderungen lesen** |
| `docs/04-design-proposal.md` | Designsystem |
| `docs/archiv/` | ursprüngliche Machbarkeitsanalyse und Prototyp-Plan |

Regeln für die Weiterentwicklung stehen in [AGENTS.md](AGENTS.md), Änderungen in [CHANGELOG.md](CHANGELOG.md).
Einzige Versionsquelle ist `VERSION`. Jede neue Version auf `main` wird automatisch als Release veröffentlicht.

**Hinweise:**
- Die APK ist mit einem Entwicklerschlüssel aus dem Repo signiert (`app/dev.keystore`).
  Vor einer öffentlichen Veröffentlichung muss er ersetzt werden.
- Die Schrift ist Inter (SIL Open Font License, `third_party/inter/OFL.txt`).
