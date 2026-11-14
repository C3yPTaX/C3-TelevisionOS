# C3-TelevisionOS – Vorschlag Prototyp P0 (zur Freigabe)

> Status: **Vorschlag** – Umsetzung erst nach Freigabe.
> Grundlage: `../02-device-findings.md`

## Ziel

Kleinstes installierbares APK, das auf dem echten TV beweist:
1. C3 Home startet schnell und lässt sich mit der Fernbedienung flüssig bedienen.
2. Apps und Einstellungen werden zuverlässig aus C3 gestartet.
3. Offene Plattformfragen (HDMI per `TvView`, PS5-Status für Drittapps) werden im Gerät beantwortet.

## Umfang

| Bereich | Inhalt P0 |
| --- | --- |
| **Apps-Reihe** | Netflix, Prime Video, YouTube, Twitch, Disney+, RTL+ – offizielle Banner zur Laufzeit; nicht installierte Apps ausgegraut mit Hinweis |
| **Geräte-Reihe** | PlayStation 5 (Status über `TvInputManager`, falls lesbar; Hinweis „Mit Controller einschalten“) · Gaming-PC (startet Moonlight, falls installiert) |
| **System-Reihe** | Einstellungen · Netzwerk · Bluetooth (falls Start erlaubt) · Geräteinfo · „Fire TV Home“ (Rückweg) |
| **Diagnose-Seite** (versteckt, z. B. Menü-Taste) | Liste aller TV-Inputs, die C3 sieht · `TvView`-Test HDMI 2 · freier RAM · C3-Speicherverbrauch |
| **Nicht in P0** | HOME-Umleitung, Wake-on-LAN, Control Center, Animationsfeinschliff, Einstellungen in C3 |

## Technische Eckdaten

- Kotlin, klassische Views, eine Activity, `minSdk 30`, nur `androidx.core`
- Paketname: `de.c3.tv` (änderbar – vor dem ersten Release festlegen, später nicht mehr)
- Berechtigungen: keine außer ggf. `INTERNET` (erst ab WoL nötig, in P0 nicht)
- `<queries>` nur für die sechs Apps + Moonlight
- Layout für 960×540 dp (UI läuft in 1080p)
- GitHub Actions baut ein Debug-APK als Download-Artefakt

## Akzeptanzkriterien

| Kriterium | Ziel | Messung |
| --- | --- | --- |
| Kaltstart C3 | < 500 ms | `adb shell am start -S -W -n de.c3.tv/.MainActivity` |
| Speicher im Vordergrund | ≤ 40 MB PSS | `adb shell dumpsys meminfo de.c3.tv` |
| Navigation | jede Kachel per D-Pad erreichbar, Fokus klar sichtbar, kein Ruckeln | manuell |
| Starts | jede installierte App startet; fehlende Apps zeigen Hinweis statt Absturz | manuell |
| Stabilität | 0 Abstürze in einer Woche Alltagsnutzung | `adb logcat` / Nutzerfeedback |

## Installation & Rückweg

```
adb install -r c3-tv-p0.apk          # installieren / aktualisieren
adb shell am start -n de.c3.tv/.MainActivity
adb uninstall de.c3.tv               # vollständig entfernen
```

Keine Systemeinstellungen werden verändert.
