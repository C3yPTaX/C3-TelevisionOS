# C3-TelevisionOS – Messergebnisse vom Gerät (Phase 1)

> Quelle: Ausgabe der lesenden ADB-Abfragen vom 2026-11-13 (Fire OS 8.1.8.5).
> Persönliche Angaben (Gerätename, Netzwerk) sind hier bewusst nicht übernommen.

## 1. System

| Merkmal | Messwert | Bewertung |
| --- | --- | --- |
| API-Level / Android | **30 / 11** | ✅ bestätigt → `minSdk 30` |
| Fire OS | 8.1.8.5, Build `RS8185.3885N` | ✅ |
| Modell / Codename | `AFTBR92D74` / `ginzaeu` | ✅ |
| SoC | `ro.hardware=mt5896` (Platform `merak`), 4× Cortex-A55 (`0xd05`), `/proc/cpuinfo` meldet *CPU architecture: 7* → **vermutlich 32-Bit-Kernel/Userspace** | 🟡 per `getprop ro.product.cpu.abilist` bestätigen; relevant nur für nativen Code (nicht geplant) |
| RAM gesamt | 1 822 912 kB (~1,74 GiB nutzbar) | ✅ |
| RAM verfügbar (Leerlauf, Amazon-Home) | **~440–500 MB** (`MemAvailable` 504 MB) | ⚠️ knapp |
| Größter Verbraucher | `com.amazon.tv.launcher` **~129 MB PSS** | Referenzwert für C3 Home |
| Heap pro App | `heapgrowthlimit=192m`, `heapsize=384m` | ✅ |
| Speicher frei | 21 GB von 23 GB | ✅ |
| `low_ram` | nicht gesetzt | ✅ |

## 2. Anzeige

| Merkmal | Messwert | Konsequenz |
| --- | --- | --- |
| Panel | 3840×2160 @ **60 Hz** (einziger Modus) | Animationen auf 60 fps |
| UI-Auflösung | **Override 1920×1080, Dichte 320** → 960×540 dp | Layout für 960×540 dp entwerfen; Bilder in 1080p, nie 4K |
| HDR | HDR10, HLG, HDR10+ (Typen 2/3/4) | Dolby Vision nur als Decoder-Profil gelistet, nicht als Display-HDR-Typ |
| ALLM / Game-Content-Type für interne Apps | **nicht unterstützt** | Für Moonlight manuell Bildmodus „Spiel“ prüfen |

## 3. Videodecoder (für Moonlight)

| Codec | Hardware-Decoder | 4K60 | `low-latency`-Feature |
| --- | --- | --- | --- |
| H.264 | `c2.mtk.avc.decoder` | ja (lt. performance-point) | ✅ |
| HEVC | `c2.mtk.hevc.decoder` | ✅ (gemessen 122 fps @ 4K, 295 fps @ 1080p) | ✅ |
| AV1 | `c2.mtk.av1.decoder` | ✅ | ✅ |
| VP9 | `c2.mtk.vp9.decoder` | ✅ | ✅ |

**Bewertung:** Deutlich besser als erwartet. Die MediaTek-Decoder melden das Android-Feature `low-latency`, das Moonlight automatisch nutzt. Risiko R3 (Decoder-Latenz) sinkt von *mittel* auf *niedrig–mittel*; endgültig nur per Moonlight-Statistik messbar.

## 4. Launcher / HOME

| Befund | Wert |
| --- | --- |
| Standard-HOME | `com.amazon.tv.launcher/.ui.HomeActivity_vNext` (Priorität 950) |
| Weitere HOME-Kandidaten | `com.amazon.firehomestarter`, `com.amazon.servicemenu`, `com.amazon.tv.settings.v2/.system.FallbackHome` – **nicht anfassen** |
| Barrierefreiheit | keine Dienste aktiv; `ACTION_ACCESSIBILITY_SETTINGS` zeigt auf `CTSDummyIntentHandler` |

**Konsequenz:** Amazon hat die Barrierefreiheits-Einstellungsseite auf diesem Gerät durch einen Platzhalter ersetzt. Ein Umleitungsdienst ließe sich **nicht über die TV-Oberfläche** aktivieren, sondern nur per ADB (reversibel):

```
# Aktivieren (nur wenn wir P1 umsetzen)
adb shell settings put secure enabled_accessibility_services de.c3.tv/de.c3.tv.redirect.HomeRedirectService
adb shell settings put secure accessibility_enabled 1
# Rückweg (aktueller Wert ist "null" → wieder leeren)
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
```

Status der HOME-Umleitung bleibt 🟡, ist aber technisch weiter möglich.

**Umsetzung (0.2.0-p1):** `de.c3.tv/de.c3.tv.redirect.HomeRedirectService` – nur `typeWindowStateChanged` von `com.amazon.tv.launcher`, kein Fensterinhalt. Umleitung, wenn eine Launcher-Activity mit `HomeActivity` im Namen erscheint; 2× HOME innerhalb von 2 s = Amazon-Home bleibt (60 s Ruhe). Ein-/Ausschalten über den C3 Updater (bewahrt andere Einträge in `enabled_accessibility_services`). Debug: `adb logcat -s C3HomeRedirect`.

**0.3.1-p2 (am Gerät bestätigt):** Doppel-Home-Fenster 1,2 s; Ausstieg endet, sobald eine andere App/C3 vorne ist; C3 öffnet sich nach dem Einschalten (Dienst verbindet sich nach Amazon-Home). Bekannte Grenze: HOME *auf* Amazon-Home erzeugt kein Fensterereignis → Rückweg über C3 in Amazons Favoritenleiste.

**0.9.1 (Nutzerbericht):** Die Fire-TV-Einstellungen (`android.settings.SETTINGS` →
`com.amazon.tv.launcher/.ui.SettingsActivity`) laufen in Amazons Launcher und erscheinen mit
einer `HomeActivity`-Fensterklasse → die Umleitung holte sofort C3 zurück. Lösung: C3 meldet
das Öffnen vorher (`requestSettings`), die Umleitung pausiert, solange Launcher oder
`com.amazon.tv.settings.v2` vorne sind. Home beendet die Pause; dafür fordert der Dienst
**nur während der Pause** Tastenereignisse an (`canRequestFilterKeyEvents`, nie verbraucht).
🟡 Am Gerät zu bestätigen: ob Fire OS die Home-Taste an den Dienst liefert. Falls nicht, führt
Home aus den Einstellungen zu Amazon-Home; ein weiterer Start von C3 beendet die Pause.

## 4a. Amazon entrümpeln (0.10.0, ungetestet)

Updater-Karte „Amazon entrümpeln“: `pm disable-user --user 0` für eine kuratierte Liste
(`tools/updater/Debloat.cs`), Rückweg `pm enable` über „Rückgängig“ oder Werkseinstellungen.
Quellen: Community-Listen für Fire-TV-Sticks/Cube (JLCareglio-Gist, dumbtv.org, XDA Cube 3,
cleanyourtv). Bewusst **nicht** abgeschaltet: Launcher (Home-Umleitung), Einstellungen,
Appstore, Tastatur, HDMI/Eingänge, Fernbedienung, WebView, OTA, Uhrzeit, Prime Video, Netflix,
Silk sowie `com.amazon.imp` (zerstörte laut Quelle Home).

**Ergebnis am Gerät (2026-11-14, Fire OS 8.1.8.5):** 22 von 28 abgeschaltet ✅.
Von Amazon geschützt (`SecurityException: Cannot disable a protected package`):
`com.amazon.device.crashmanager`, `com.amazon.ftvads.deeplinking`. Nicht vorhanden:
`com.amazon.shoptv.firetv.client`, `com.amazon.device.sale.service`,
`com.amazon.imdb.tv.android.app`, `com.amazon.communication.discovery`.
Arbeitsspeicher frei 553 → 549 MB direkt danach: **kein messbarer Gewinn** – die Pakete liefen
kaum dauerhaft. Nutzen: weniger Hintergrund-/Netzverkehr, ACR aus. Für spürbar mehr Tempo
müssen die echten Verbraucher bekannt sein (ab 0.10.1 im Protokoll).

**Größte Verbraucher (PSS, nach dem Entrümpeln, C3 im Vordergrund):** system 140 MB,
`com.amazon.tv.launcher` 115, `de.c3.tv` 82, surfaceflinger 67, `com.amazon.venezia` 44,
`com.amazon.vizzini` 39, `com.amazon.device.services` 35, systemui 26, `com.amazon.tcomm` 25,
`com.vewd.core.service:browser_process` 24, `com.amazon.alexa.externalmediaplayer.fireos` 19,
`com.amazon.tv.devicecontrol` 18, `com.amazon.tv.settings.v2` 17, Audio-HAL 16,
`com.amazon.alexamediaplayer.runtime.ftv` 15.
→ 0.11.0: optionale Gruppen **Alexa** (vizzini, tcomm, `com.amazon.alexa*`) und **Vewd**
(`com.vewd.*`, HbbTV für Antenne/Kabel) – der Nutzer nutzt weder Alexa noch Live-TV.
Bleiben an: Launcher (Home-Umleitung), Appstore (Lizenzen), device.services, devicecontrol (CEC).

## 5. Streaming-Apps

| App | Paket / Start-Activity | Status |
| --- | --- | --- |
| Netflix | `com.netflix.ninja/.MainActivity` | ✅ installiert |
| Prime Video | `com.amazon.firebat/com.amazon.pyrocore.IgnitionActivity` | ✅ vorinstalliert |
| RTL+ | `de.cbc.tvnow.firetv/com.bedrockstreaming.shared.tv.activity.SplashActivity` | ✅ installiert |
| YouTube | – | ❌ **nicht installiert** |
| Twitch | – | ❌ **nicht installiert** |
| Disney+ | – | ❌ **nicht installiert** |

Fehlende Apps installierst du aus dem Amazon Appstore; danach Paketnamen per `pm list packages` nachtragen.

## 6. HDMI-Eingänge & PlayStation

| Port | Input-ID (TIF) | Eigenschaften | Gerät |
| --- | --- | --- | --- |
| HDMI 1 | `com.mediatek.tis/.HdmiInputService/HW2` | CEC | – |
| HDMI 2 | `com.mediatek.tis/.HdmiInputService/HW3` | CEC, **ARC** | **PlayStation 5** |
| HDMI 3 | `com.mediatek.tis/.HdmiInputService/HW4` | CEC | – |
| PS5 (CEC-Gerät) | `com.mediatek.tis/.HdmiInputService/HDMI20000B` | logische Adresse 0x0B, physisch 0x2000, Name „PlayStation 5“ | ✅ erkannt |

- HDMI-CEC ist am TV **aktiv** (`mHdmiControlEnabled: true`, Auto-Wakeup an).
- Der TV fragt den Ein/Aus-Status der PS5 regelmäßig per CEC ab (`Give Device Power Status`) – PS5-Status ist damit systemseitig bekannt.
- **Android-Standard-Passthrough schaltet NICHT um:** `content://android.media.tv/passthrough/...` öffnet nur die Live-TV-Oberfläche (`com.amazon.tv.livetv`, ggf. mit Auswahldialog Vewd/Live TV). ❌
- **Amazons eigener Weg (per logcat beobachtet beim manuellen Umschalten auf HDMI 2):**
  ```
  act=android.intent.action.VIEW
  dat=tvinput://tunenow?inputId=com.mediatek.tis/.HdmiInputService/HW3&suppressInputTuneNotification=false&refTag=null
  cmp=com.amazon.tv.inputpreference.service/com.amazon.tv.inputpreference.player.PassthroughPlayerActivity
  ```
  Vorgelagert: `act=com.amazon.tv.inputpreference.service.action.SWITCH_AMAZON_INPUT` → `.StartAmazonInputActivity`.
  **Ergebnis:** `PassthroughPlayerActivity` ist durch `com.amazon.tv.permission.TUNE_INPUT` geschützt, `protectionLevel: signature|amazon` (ebenso `com.amazon.tv.livetv.permission.TUNE_INPUT`). Selbst ADB (uid 2000) erhält `SecurityException`. → **Direktes Umschalten über Amazons Player ist für Drittapps nicht möglich.** ❌
  Vorgelagerter Einstieg `StartAmazonInputActivity` erfordert `com.amazon.tv.inputpreference.permission.LAUNCH_INPUTS` (`signature|privileged`). ❌
  **Fazit: Alle Amazon-Wege zum direkten Eingangswechsel sind für Drittapps gesperrt.** Kein Umgehen ohne Root/Firmware → außerhalb unserer Regeln.
- **✅ Durchbruch (P0-Diagnose, 2026-11-13): `TvView` funktioniert für Drittapps.** C3 sieht 15 TV-Inputs (inkl. CEC-Gerät „PlayStation 5“, `HDMI20000B`, parent `HW3`) und zeigt das **Live-Bild der PS5 in der eigenen Oberfläche** an (`TvView.tune(inputId, buildChannelUriForPassthroughInput(inputId))`). Ohne Sonderrechte.
  - ✅ Ton der PS5 läuft mit. Offen: Eingabeverzögerung (Bildmodus/Game-Mode), Weiterleitung der Fernbedienung per CEC. Umgesetzt als Vollbild-Player `InputPlayerActivity` (P0.1).
  - Der gemeldete Input-Status („Standby“) stimmte nicht mit dem tatsächlichen Zustand (PS5 an) überein → für Statusanzeige nicht verlässlich.
- **Ausweichwege für die PlayStation-Kachel:** (1) CEC One Touch Play (PS5 schaltet TV selbst um) ✅, (2) Amazons Eingangsauswahl öffnen 🟡, (3) HDMI-Bild per `TvView` in C3 selbst darstellen – plausibel, ungetestet, nur als Experiment 🟡.
- PlayStation-Kachel: Erkennung über den CEC-Input `HDMI20000B` bzw. dessen Label „PlayStation 5“ via `TvInputManager` (Sichtbarkeit für Drittapps noch zu prüfen).

Status Smart Inputs: **GO über eigenen `TvView`-Vollbildplayer** (Ton/Latenz noch zu prüfen); Amazons Umschaltwege bleiben gesperrt.

## 7. Einstellungs-Deep-Links

| Intent | Ziel | Praxistest `am start` |
| --- | --- | --- |
| `android.settings.SETTINGS` | `com.amazon.tv.launcher/.ui.SettingsActivity` | ✅ öffnet |
| `android.settings.WIFI_SETTINGS` | `com.amazon.tv.settings.v2/.tv.network.NetworkActivity` | ✅ öffnet |
| `android.settings.DISPLAY_SETTINGS` | `…DisplayAndSoundsActivity` (quicksettings + settings.v2) | ❌ kein Fehler, aber **nichts öffnet** |
| `android.settings.SOUND_SETTINGS` | dito | ❌ kein Fehler, aber **nichts öffnet** |
| `android.settings.DEVICE_INFO_SETTINGS` | `com.amazon.tv.settings.v2/.tv.device.DeviceActivity` | ✅ öffnet |
| `android.settings.BLUETOOTH_SETTINGS` | **kein Ziel** | ❌ |
| Bluetooth-Seite (manuell geöffnet) | `com.amazon.tv.settings.v2/.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity` | ✅ per expliziter Komponente startbar (ADB-Test: kein Permission Denial) |
| `android.settings.ACCESSIBILITY_SETTINGS` | Platzhalter | ❌ |
| `com.amazon.tv.settings.action.HDMI_CONTROL_SETTINGS` | `inputpreference…HdmiControlSettingsActivity` | ❌ erfordert `com.amazon.tv.permission.LAUNCHER_SETTINGS` |

| Bild/Ton-Seite (manuell geöffnet) | `com.amazon.tv.quicksettings.ui/.view.FullScreenSettingsActivity` | ❌ erfordert `com.amazon.tv.permission.LAUNCHER_SETTINGS` |

- Bildeinstellungen (Modus, Helligkeit, Kontrast) sind im Hauptmenü nicht verfügbar, nur kontextbezogen während eine Quelle läuft (Schnelleinstellungen: HOME lang drücken).
- **Fazit Control Center:** Direkt aus C3 öffnbar sind *Einstellungen*, *Netzwerk*, *Geräteinfo*. Bild/Ton nur über Amazons Schnelleinstellungen (HOME lang). Lautstärke über `AudioManager`.

> Hinweis: Eine Auflösung per `query-activities` beweist nicht, dass der Start erlaubt ist. Jeder Deep-Link muss per `am start` geprüft werden.

## 7a. Baseline: Kaltstartzeiten (`am start -S -W`)

| App | TotalTime | WaitTime |
| --- | --- | --- |
| Netflix | 1111 ms | 1123 ms |
| Prime Video | 2141 ms | 2158 ms |
| RTL+ | 3016 ms | 3061 ms |

Zielwert C3 Home: Kaltstart **< 500 ms**.

## 7b. Windows-Hosts (Angaben Nutzer)

| Gerät | GPU | Netzwerk | Sunshine-Encoder | WoL |
| --- | --- | --- | --- | --- |
| Gaming-PC | RTX 4060 Ti (Ada) | LAN oder WLAN | NVENC H.264/HEVC/**AV1** ✅ | ✅ nur per **LAN** zuverlässig |
| Laptop | RTX 5060 Laptop (Blackwell, Bezeichnung zu bestätigen) | LAN oder WLAN | NVENC H.264/HEVC/AV1 ✅ | 🟡 nur am LAN, eingeschränkt (Akku, Deckel, Dock) |

- Codec-Strategie: **HEVC** als Standard (am ausgereiftesten), **AV1** als Test – beide decodiert der TV in Hardware mit `low-latency`.
- Laptop mit Hybridgrafik (iGPU + RTX): Sunshine-Capture hängt davon ab, welche GPU das Display treibt → ggf. MUX-Schalter/„dGPU only“ nötig. 🟡
- TV hängt im **WLAN**; Hosts haben 1080p-Monitore → Ziel **1080p60 HEVC**, ~15–20 Mbit/s. Leichte Latenz ist laut Nutzer akzeptabel. 4K nicht erforderlich.

## 7c. P0 – erste Messung

| Wert | Messung | Ziel |
| --- | --- | --- |
| Kaltstart (erste Installation, nicht vorkompiliert) | 875 ms | < 500 ms |
| `dumpsys meminfo` TOTAL PSS (Vordergrund) | 73 MB | ≤ 40 MB (Ziel wird nach Aufschlüsselung überprüft) |
| `Debug.getMemoryInfo` PSS (Diagnose-Seite) | 31 MB | – |

## 7d. P0.1 – Praxistest (Nutzer, 2026-11-13)

- ✅ C3 Updater (Windows) aktualisiert den TV zuverlässig (Download `c3-latest` → `adb install -r` → Start).
- ✅ PlayStation-Kachel öffnet die PS5 im Vollbild über `InputPlayerActivity` (TvView), mit Ton.

## 7e. C3 Desktop – Direktstart (0.3.0-p2)

- Moonlight 12.1 (Amazon Appstore) nutzt am TV `c2.mtk.hevc.decoder` mit `FEATURE_LowLatency`, 1080p60.
- Direktstart über die exportierte Activity `com.limelight/.ShortcutTrampoline` mit Extras `UUID` + `AppId` (+ `AppName`) ✅ am Gerät bestätigt. Übergabe per PC-Name (`Name`/`AppName`-Lookup) gibt es erst in unveröffentlichten Moonlight-Versionen.
- UUID = `<uniqueid>` aus Sunshine `http://localhost:47989/serverinfo`.
- App-ID = `abs((int32) crc32(name + sha256hex(app-bild)))` (Sunshine `process.cpp`), gegen den echten Wert verifiziert. Der C3 Updater berechnet sie lokal aus `apps.json` und sendet die PC-Liste per `de.c3.tv.action.SET_PCS` an C3.

## 8. Aktualisierte Go/No-Go-Bewertung

| Modul | vorher | jetzt |
| --- | --- | --- |
| C3 Home (App) | GO | **GO** – 3 von 6 Apps vorhanden, 3 müssen installiert werden |
| C3 Home (HOME-Umleitung) | bedingt | **bedingt** – Aktivierung nur per ADB möglich |
| C3 Desktop | GO 1080p60 | **GO** – Direktstart in den Stream per Kachel; Konfiguration automatisch über den Updater |
| C3 Smart Inputs | bedingt | **GO** – PS5/HDMI per `TvView`-Vollbildplayer in C3, im Alltag bestätigt; Latenz/Fernbedienung noch zu bewerten |
| C3 Remote Power | GO | GO (unverändert, PC-Seite offen) |
| C3 Control Center | teilweise | **reduziert** – Lautstärke, Einstellungen, Netzwerk, Geräteinfo, Systeminfo; Bild/Ton nur über Amazon-Schnelleinstellungen; Bluetooth offen |

## 9. Offene Praxistests

Siehe Abschnitt 8.3 in [`archiv/01-machbarkeit.md`](archiv/01-machbarkeit.md):

1. ~~HDMI-Umschaltung~~ → erledigt: über Amazon nicht möglich; `TvView`-Experiment folgt mit Test-APK
2. ~~Kaltstartzeiten~~ → erledigt (Abschnitt 7a)
3. ~~Bild/Ton~~ → gesperrt (`LAUNCHER_SETTINGS`); Bluetooth-Seite noch offen
4. Moonlight-Installation und Latenzmessung
