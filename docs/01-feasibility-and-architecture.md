# C3-TelevisionOS – Machbarkeitsprüfung & Architekturvorschlag (Phase 1)

> Stand: 2026-11-13 · Status: **Entwurf, noch nicht auf dem Gerät verifiziert**
>
> Grundlage: README/Roadmap aus PR #1, öffentliche Android-API-Dokumentation, Amazon-Fire-OS-Doku und Community-Quellen.
> Alles, was hier als „plausibel“ markiert ist, wird erst durch die Messwerte aus `tools/adb/collect-device-info.sh` und die Tests in Abschnitt 7 zu „bestätigt“ oder „nicht unterstützt“.

## Legende

| Symbol | Bedeutung |
| --- | --- |
| ✅ **Bestätigt** | Öffentliche, dokumentierte API bzw. Herstellerangabe; funktioniert ohne Sonderrechte. Muss nur noch auf dem Gerät gegengeprüft werden. |
| 🟡 **Plausibel** | Technisch naheliegend oder von der Community auf ähnlichen Geräten berichtet, aber für L75MB-FPEU / Fire OS 8.1.8.5 **nicht belegt**. |
| ❌ **Nicht unterstützt** | Erfordert System-/Signaturrechte, Root oder Firmware-Eingriffe → außerhalb unserer Sicherheitsregeln. |

---

## 1. Hardware- und Systemanalyse

### 1.1 Hardware

| Merkmal | Wert | Status |
| --- | --- | --- |
| Modell | Xiaomi TV F Pro 75 (2026), L75MB-FPEU | ✅ Xiaomi-FAQ |
| SoC | MediaTek **MT9026** (MT9026EAAT/BBZA) | ✅ Xiaomi-FAQ |
| CPU | 4× Cortex-A55, bis 1,53 GHz | ✅ |
| GPU | Mali-G52 MC1 | ✅ |
| RAM / Speicher | 2 GB / 32 GB | ✅ (Spec-Seite) |
| Panel | 4K, **60 Hz**, QLED (Backlight laut FAQ DLED) | ✅ |
| HDMI | 3× (2× HDMI 2.0, 1× HDMI 2.1) | ✅ |
| Audio | 2× 10 W | ✅ |
| Hardware-Videodecoder (HEVC/VP9/AV1, 4K60, HDR) | unbekannt im Detail | 🟡 → `media_codecs*.xml` auslesen |

**Konsequenzen für das Design**

- Cortex-A55 ist ein reiner Effizienzkern: Single-Thread-Leistung ist niedrig. **Kaltstart, Layout-Inflation und Bitmap-Decoding sind die eigentlichen Engpässe**, nicht die GPU.
- Von 2 GB RAM bleiben nach Fire OS (Launcher, Alexa, Amazon-Dienste) erfahrungsgemäß nur einige hundert MB frei. Jede MB, die unser Launcher dauerhaft belegt, fehlt Netflix/Moonlight → Ziel: **≤ 40 MB PSS** im Vordergrund, nahe 0 im Hintergrund.
- 60-Hz-Panel → Animationen auf 60 fps auslegen; keine aufwendigen Blur-/Parallax-Effekte (Mali-G52 MC1 ist ein Ein-Kern-GPU-Setup).

### 1.2 Betriebssystem

| Merkmal | Wert | Status |
| --- | --- | --- |
| Fire OS | 8.1.8.5 (vom Nutzer beobachtet) | ✅ |
| Android-Basis | Fire OS 8 = **Android 11, API-Level 30** | ✅ Amazon-Doku |
| Gerätetyp | „Fire TV Smart TV“ (ehem. Fire TV Edition) mit TV-Input-Framework (HDMI/Tuner) | ✅ |
| Sideloading / ADB | Auf Fire-OS-Geräten weiterhin möglich; Entwickleroptionen seit Fire OS 7.2.8.5 versteckt (7× auf Gerätenamen unter „Info“ klicken) | 🟡 auf diesem Gerät prüfen |
| Vega OS | Amazons neues Linux-basiertes OS (kein Sideloading) betrifft laut Berichten neue Sticks, **nicht** dieses TV | 🟡 Risiko für die Zukunft, s. Risiken |
| Anti-Piraterie-Prüfung bei Installation (seit ~Jan. 2026, Paketnamen-Blockliste) | betrifft uns nicht, solange Paketnamen unauffällig sind | 🟡 |

### 1.3 Relevante Android-11-Regeln (API 30)

- **Package Visibility:** Ab targetSdk 30 sehen Apps andere Pakete nur, wenn sie im Manifest per `<queries>` deklariert sind. Wir deklarieren gezielt die sechs Streaming-Apps, Moonlight und ggf. Einstellungen – **kein** `QUERY_ALL_PACKAGES`.
- **Hintergrundstarts:** Activities dürfen aus dem Hintergrund nur in Ausnahmefällen gestartet werden (u. a. von einem aktiven AccessibilityService). Relevant für die Home-Umleitung.
- **WLAN/Bluetooth-Toggles:** `WifiManager.setWifiEnabled()` ist für Apps mit targetSdk ≥ 29 wirkungslos → nur Deep-Links in Einstellungen.
- **HOME-Taste** wird nie an normale Apps zugestellt.

---

## 2. Kernfrage: Fire-TV-Launcher ersetzen oder umgehen?

| Ansatz | Bewertung | Begründung |
| --- | --- | --- |
| A. Eigene App mit `CATEGORY_HOME` als Standard-Launcher setzen | ❌ | Amazon ignoriert seit Fire OS 6.2.7/7.2.7.3 fremde HOME-Activities; „Launcher Manager“-Lücke wurde Sept./Okt. 2025 geschlossen. |
| B. `pm disable-user com.amazon.tv.launcher` | ❌ **verboten** | Verstößt gegen unsere Sicherheitsregeln. Auf Smart-TVs hängen Ersteinrichtung, Eingangsumschaltung, Einstellungen und Live-TV am Amazon-Launcher → realistisches Risiko eines nur per Werksreset reparierbaren Zustands. |
| C. **Home-Umleitung per AccessibilityService** | 🟡 **empfohlen** | Amazon-Launcher bleibt System-Home. Ein schlanker Dienst erkennt, dass `com.amazon.tv.launcher` in den Vordergrund kommt, und startet C3 Home darüber. Community-Projekt „Home on Fire“ meldet Funktion auf Fire OS **8.1.7.4–8.1.8.2** (Amazon-Ember-TV) – sehr nah an unserer 8.1.8.5. Nebenwirkung: ~250 ms sichtbares Aufblitzen des Amazon-Home. |
| D. C3 Home als normale App (Fallback) | ✅ | Funktioniert immer. Start über App-Liste, Favoritenleiste oder eine programmierbare Taste. HOME führt zurück zu Amazon. |

**Empfehlung:** Zweistufig.

1. **Prototyp = Ansatz D.** Kein Systemeingriff, sofort nutzbar.
2. **Optional Ansatz C**, als eigener Schalter in C3, standardmäßig **aus**. Eigene, minimale Implementierung (keine Fremd-APK im Dauerbetrieb):
   - `accessibilityEventTypes="typeWindowStateChanged"`, `packageNames="com.amazon.tv.launcher"` (exakter Name per ADB bestätigen), `canRetrieveWindowContent="false"`, kein Key-Filtering.
   - Das System liefert dann **nur** Ereignisse des Amazon-Launchers → praktisch keine CPU-Last. Kosten: der App-Prozess bleibt resident (~15–25 MB, zu messen). Das ist der einzige „Dauerläufer“ des Projekts und wird bewusst opt-in gemacht.
   - Notausgang: langes Drücken von HOME bzw. Doppel-HOME → Amazon-Home bleibt erreichbar (Schnelleinstellungen, Profile, Eingänge).
   - **Rückweg:** Dienst in *Einstellungen → Barrierefreiheit* abschalten **oder** `adb uninstall` – beides vollständig reversibel. `WRITE_SECURE_SETTINGS` nur dann per ADB gewähren, wenn die Barrierefreiheits-Seite auf diesem Gerät den Dienst nicht aktivieren lässt (`pm revoke` als Rückweg).
   - Vor eigener Implementierung kann die Machbarkeit **ohne Code** geprüft werden, indem „Home on Fire“ testweise installiert und danach wieder deinstalliert wird (optional, deine Entscheidung).

> Erwartung realistisch halten: Ansatz C ist ein Workaround. Ein Fire-OS-Update kann ihn jederzeit brechen. Deshalb muss C3 Home **auch ohne** Umleitung vollständig nutzbar bleiben.

---

## 3. Machbarkeitsbewertung pro Modul

### 3.1 C3 Home — **GO** (mit Einschränkung „echter Launcher“)

| Funktion | Status | Umsetzung |
| --- | --- | --- |
| Eigene Vollbild-Oberfläche, D-Pad-Fokus | ✅ | Standard-Views, Fokus-Handling |
| Sechs Streaming-Apps starten | ✅ | `PackageManager.getLeanbackLaunchIntentForPackage()` mit Fallback `getLaunchIntentForPackage()` |
| Offizielle App-Banner/Icons zur Laufzeit | ✅ | `ApplicationInfo.loadBanner()` / `loadIcon()` – nichts im APK gebündelt |
| Deep-Links in Inhalte (z. B. „Weiterschauen“) | 🟡 | App-spezifisch, nicht für v1 |
| „Zuletzt verwendet“ | 🟡 | `UsageStatsManager` braucht Sonderzugriff → stattdessen eigene Liste der *aus C3 gestarteten* Apps (ohne Berechtigung) |
| Echter System-Launcher | ❌ / 🟡 | siehe Abschnitt 2 |

**Paketnamen (müssen per `pm list packages` bestätigt werden – Fire-OS-Varianten weichen oft ab):**

| App | Erwarteter Paketname | Status |
| --- | --- | --- |
| Netflix | `com.netflix.ninja` | 🟡 |
| Prime Video | `com.amazon.avod` oder `com.amazon.firebat` (vorinstalliert) | 🟡 |
| YouTube | `com.amazon.firetv.youtube` (Fire-TV-Variante) | 🟡 |
| Twitch | `tv.twitch.android.viewer` o. ä. | 🟡 |
| Disney+ | `com.disney.disneyplus` | 🟡 |
| RTL+ | unbekannt (Fire-TV-Variante) | 🟡 |

Logins/Sessions bleiben vollständig bei den Anbietern; C3 startet nur die Apps.

### 3.2 C3 Desktop (Moonlight/Sunshine) — **GO für 1080p60, 4K offen**

| Funktion | Status | Anmerkung |
| --- | --- | --- |
| Moonlight-Client auf Fire OS 8 | ✅ | Läuft auf Android ≥ 5; Fire-TV-Sticks mit MediaTek-SoC sind gängige Moonlight-Clients. Installation aus Amazon Appstore (🟡) oder offizielles GitHub-Release-APK |
| Sunshine auf Windows-Host | ✅ | Standard-Setup, gekoppelt per PIN |
| Start aus C3 direkt in einen Host/eine App | 🟡 | Moonlight hat eine `ShortcutTrampoline`-Activity, die Extras für Host-UUID/-Name und App-ID/-Name liest und einen offline Host per WoL weckt. Ob sie für Fremd-Apps **exportiert** ist, prüfen wir per `dumpsys package`. Fallback: Moonlight normal starten. |
| Latenz | 🟡 | Community-Werte für MediaTek-Fire-TV-Geräte: ~10–20 ms Decode-Latenz (Shield/Apple TV ~2 ms). Für Metin2/Desktop gut, für kompetitives Gaming mittelmäßig. **Messen mit Moonlight-Statistik-Overlay.** |
| 1080p60 HEVC | 🟡 (sehr wahrscheinlich) | |
| 4K60 / HDR | 🟡 | Abhängig vom Decoder-Profil und von der Bildverarbeitung des TVs (internes Bild bekommt keine HDMI-ALLM-Signalisierung). |
| BT-Tastatur/-Maus | 🟡 | Fire OS unterstützt BT-HID; Moonlight nutzt Pointer-Capture. Pairing über Fire-OS-Einstellungen. |

**Entscheidung:** Moonlight **nicht** einbetten oder forken (GPLv3, Wartungsaufwand, Dekoder-Tuning). C3 Desktop ist eine dünne Auswahlseite (PC/Laptop), die WoL auslöst und Moonlight per Intent startet. Kein permanenter Verbindungsaufbau.

### 3.3 C3 Smart Inputs (HDMI / PlayStation) — **teilweise GO**

| Funktion | Status | Umsetzung / Grund |
| --- | --- | --- |
| HDMI-Eingänge auflisten | 🟡 | `TvInputManager.getTvInputList()` ist öffentliche API. Ob Fire OS die HDMI-Inputs für Drittapps sichtbar macht, zeigt `dumpsys tv_input`. |
| Auf HDMI-Eingang umschalten | 🟡 | Standardweg: `ACTION_VIEW` auf `TvContract.buildChannelUriForPassthroughInput(inputId)`. Ob Amazons TV-App diesen Intent annimmt, ist **der** zentrale Test (Abschnitt 7). Fallback: Deep-Link in Amazons Eingangsauswahl. |
| Verbindungsstatus pro Port (Kabel/Gerät aktiv) | 🟡 | `TvInputManager.TvInputCallback.onInputStateChanged` (öffentlich), nur solange C3 im Vordergrund ist – keine Hintergrundüberwachung. |
| PlayStation per CEC-Name erkennen | 🟡 | CEC-Geräte erscheinen oft als eigene TV-Inputs mit OSD-Namen („PS5“) – Label prüfen in `dumpsys tv_input` / `dumpsys hdmi_control`. |
| Direkter CEC-Zugriff (`HdmiControlManager`) | ❌ | System-API mit Signatur-/Privileg-Berechtigung `HDMI_CEC`. |
| Automatisch zur PS5 umschalten, wenn sie eingeschaltet wird | ✅ (ohne C3) | Das ist CEC *One Touch Play* und wird von TV-Firmware + PS5 selbst erledigt, wenn „HDMI-CEC“ am TV und „HDMI-Geräteverknüpfung“ an der PS5 aktiv sind. C3 muss hier **nichts** tun. |
| Zurück zu C3 Home aus HDMI | ✅ / 🟡 | HOME → Amazon-Home → (mit Umleitung) C3 Home. Ohne Umleitung manuell. |

### 3.4 C3 Remote Power — **GO (WoL), Shutdown später**

| Funktion | Status | Umsetzung |
| --- | --- | --- |
| Wake-on-LAN | ✅ | Magic Packet per UDP-Broadcast (Port 9), nur `INTERNET`-Berechtigung, ~50 Zeilen Code, keine Bibliothek. Voraussetzung PC-seitig: WoL im BIOS/UEFI und NIC-Treiber, **LAN-Kabel** (WLAN-WoL ist unzuverlässig), Windows-„Schnellstart“ ggf. aus. |
| Online-Status | 🟡 | On-Demand-TCP-Probe auf Sunshine-Port (47989), nur während die Seite offen ist. Kein Polling im Hintergrund. |
| Danach C3 Desktop öffnen | ✅ | Intent-Kette in C3 |
| Authentifiziertes Herunterfahren | 🟡 | Option 1 (bevorzugt, kein Zusatzdienst): Sunshine-„App“-Eintrag *Shutdown* mit `shutdown /s /t 10`, gestartet über Moonlight – nutzt die bestehende, gepaarte Sunshine-Authentisierung. Option 2: eigener Windows-Agent → mehr Angriffsfläche, erst wenn Option 1 nicht reicht. Entscheidung in Phase 4. |

### 3.5 C3 Control Center — **teilweise GO (meist Deep-Links)**

| Bereich | Status | Umsetzung |
| --- | --- | --- |
| Lautstärke ±/Mute | ✅ | `AudioManager.adjustStreamVolume` (öffentlich). Verhalten bei ARC/Soundbar 🟡. |
| Bildmodus, Helligkeit, Kontrast, HDR | ❌ direkt / 🟡 Deep-Link | Keine öffentliche Android-API auf API 30; Amazon-/MediaTek-intern. → Deep-Link in Fire-OS-Bildeinstellungen (Activity-Namen per ADB ermitteln). |
| Tonausgabe wählen | ❌ direkt / 🟡 Deep-Link | |
| WLAN / Bluetooth | ❌ direkt / 🟡 Deep-Link | `Settings.ACTION_WIFI_SETTINGS`, `ACTION_BLUETOOTH_SETTINGS` – ob Amazon diese auflöst, wird getestet. |
| HDMI | ✅/🟡 | siehe Smart Inputs |
| Geräte (PCs/Laptops) | ✅ | Eigene Konfiguration in C3 |
| Systeminfo | ✅ | `Build.*`, freier RAM über `ActivityManager.getMemoryInfo` |
| Neustart | ❌ | `REBOOT` ist Signatur-Berechtigung → Deep-Link „Einstellungen → Mein Fire TV → Neu starten“ |
| „Zurück zu Fire OS“ | ✅ | Expliziter Start von Amazon-Home + Hinweis, wie die Umleitung deaktiviert wird |

### 3.6 Zusammenfassung

| Modul | Go/No-Go | Hauptrisiko |
| --- | --- | --- |
| C3 Home (App) | **GO** | – |
| C3 Home (HOME-Umleitung) | **bedingt** | Fire-OS-Updates; 250-ms-Aufblitzen |
| C3 Desktop | **GO** | Decoder-Latenz des MT9026 |
| C3 Smart Inputs | **bedingt** | Passthrough-Intent von Amazon evtl. nicht angenommen |
| C3 Remote Power | **GO** (WoL) | PC-seitige WoL-Konfiguration |
| C3 Control Center | **GO als Schnellzugriff** | Bild/Ton nur per Deep-Link |

---

## 4. Empfohlener Technologie-Stack

| Bereich | Wahl | Begründung |
| --- | --- | --- |
| Sprache | **Kotlin** | Standard, keine Laufzeitkosten gegenüber Java |
| UI | **Klassische Android Views** (FrameLayout/LinearLayout, ggf. `RecyclerView` erst wenn nötig) | Schnellster Kaltstart auf Cortex-A55, kein Compose-Runtime-Overhead, präzise Fokuskontrolle. **Kein Leanback** (schwer, festes Design). |
| Abhängigkeiten | nur `androidx.core` (+ optional `androidx.recyclerview`) | Kein DI-Framework, kein Room, kein Retrofit/OkHttp, kein Coroutines-Overhead für triviale Aufgaben |
| Persistenz | `SharedPreferences` + Default-Konfiguration als JSON in `assets/` | Wenige Dutzend Einträge |
| Netzwerk | `DatagramSocket` (WoL), `Socket` (Probe) | Plattform-APIs |
| SDK | `minSdk 30`, `targetSdk`/`compileSdk` = aktuelles stabiles SDK (34/35), auf API 30 getestet | Einziges Zielgerät → keine Kompatibilitätsschichten |
| Build | Gradle (Kotlin DSL), AGP, **R8 + Ressourcen-Shrinking**, Baseline Profile in Phase 5 | Kleiner Dex, schnellerer Start |
| Desktop-Streaming | Moonlight (Client, unverändert) + Sunshine (Host) | Bewährt, extern gewartet |
| CI | GitHub Actions: Build + Lint + Unit-Tests, signiertes Release-APK als Release-Asset | Reproduzierbar |
| Installation/Update | `adb install -r` (Skript) bzw. Downloader-App mit GitHub-Release-URL | Kein Self-Updater (bräuchte `REQUEST_INSTALL_PACKAGES` + Netzwerkcode) – evtl. später |

**Signaturschlüssel:** Ein einziger Release-Keystore, außerhalb des Repos gesichert. Ohne denselben Schlüssel sind Updates nur per Deinstallation möglich.

---

## 5. Softwarearchitektur & Modulstruktur

### 5.1 Grundprinzipien

- **Ein APK, ein Prozess, eine Activity.** Mehrere APKs/Prozesse kosten auf 2 GB pro Prozess ~15–20 MB Grundlast. „Modular“ heißt bei uns: klar getrennte Pakete/Features, die nur bei Bedarf instanziiert werden.
- **Keine Dienste, keine Receiver, kein WorkManager, kein Polling.** Einzige Ausnahme: der optionale, standardmäßig deaktivierte AccessibilityService.
- **Alles On-Demand:** Netzwerk-Probe nur bei geöffneter Seite, Banner werden beim Anzeigen geladen und in einem kleinen LRU-Cache gehalten, `onTrimMemory` gibt Caches frei.

### 5.2 Paketstruktur (Gradle: zunächst ein `:app`-Modul)

```
app/src/main/java/de/c3/tv/
├── C3Application.kt            // keine Initialisierung außer Konfig-Lazy-Load
├── MainActivity.kt             // einzige Activity, einfacher Screen-Stack
├── core/
│   ├── config/                 // AppConfig, DeviceConfig (JSON + SharedPreferences)
│   ├── launch/                 // AppLauncher: Intents, Paketauflösung, Fallbacks
│   ├── ui/                     // Fokus-Kacheln, Theme, Fokus-Animation (Scale + Glanz)
│   └── system/                 // Deep-Links in Fire-OS-Einstellungen, Systeminfo
├── feature/
│   ├── home/                   // C3 Home: Raster aus Apps, Geräten, Eingängen
│   ├── desktop/                // PC-Auswahl → WoL → Moonlight-Intent
│   ├── inputs/                 // TvInputManager-Wrapper, HDMI-/PS-Kacheln
│   ├── power/                  // WakeOnLan, ReachabilityProbe
│   └── control/                // Control Center (Lautstärke, Deep-Links, Info)
└── redirect/
    └── HomeRedirectService.kt  // optionaler AccessibilityService (Default: aus)
```

Gradle-Module (`:core`, `:feature-*`) lohnen sich erst, wenn Build-Zeiten oder Kopplung es verlangen – derzeit wäre das Overengineering.

### 5.3 Datenfluss (Beispiel „PC starten und streamen“)

```
[Kachel „Gaming-PC“] → power.WakeOnLan.send(mac)
                     → power.ReachabilityProbe (max. ~60 s, nur solange Screen offen)
                     → launch.AppLauncher.startMoonlight(hostUuid, appName)
                         ├─ ShortcutTrampoline exportiert? → direkter Start
                         └─ sonst → Moonlight-Hauptbildschirm
```

### 5.4 Design (tvOS-inspiriert, ohne geschützte Assets)

- Dunkler, ruhiger Hintergrund (einfarbig/Verlauf, kein Video), große Kacheln in einer Reihe pro Kategorie (Apps · Geräte · Eingänge), Fokus durch Skalierung 1.0→1.08 + Schatten + leichter Lichtreflex.
- Eigene Schrift: System-Sans (Roboto/Amazon Ember – nichts bündeln). Eigene Icons als Vector Drawables. **Keine** Apple-Icons, -Schriften (SF Pro), -Hintergründe oder -Sounds.
- Animationen nur über `ViewPropertyAnimator` (Hardware-Layer), Dauer ≤ 150 ms.

---

## 6. Bekannte technische Risiken

| # | Risiko | Wahrscheinl. | Auswirkung | Gegenmaßnahme |
| --- | --- | --- | --- | --- |
| R1 | Fire-OS-Update blockiert HOME-Umleitung | mittel | C3 nicht mehr „Standard“ | C3 bleibt als App voll nutzbar; Umleitung ist optional |
| R2 | Amazon-TV-App nimmt Passthrough-Intent nicht an | mittel | Kein Direktwechsel auf HDMI | Deep-Link in Amazon-Eingangsauswahl; CEC One Touch Play nutzen |
| R3 | MT9026-Decoder hat hohe Latenz / kein Low-Latency-Modus | mittel | Spürbare Eingabeverzögerung | 1080p60, HEVC, Bitrate-Tuning, Bildmodus „Spiel“; ggf. Akzeptanzgrenze definieren |
| R4 | RAM-Druck: Moonlight/Netflix werden durch Fire-OS-Dienste verdrängt | mittel | Ruckler, App-Neustarts | Eigener Footprint minimal; Fire-OS-Einstellungen (Autoplay, Datensammlung) abschalten – reversibel |
| R5 | Paketnamen der Fire-TV-App-Varianten ändern sich | niedrig–mittel | Kachel startet nicht | Konfigurierbare Kandidatenliste je App, Fehlermeldung statt Absturz |
| R6 | Amazon schränkt Sideloading/ADB weiter ein (Vega-Strategie, Installationsprüfungen) | niedrig (dieses Gerät) | Updates erschwert | Unauffälliger Paketname, signierte Releases, keine Blocklisten-relevanten Inhalte |
| R7 | Netzwerk-ADB bleibt offen im LAN | mittel | Sicherheitsrisiko | ADB-Debugging nach Installation wieder deaktivieren |
| R8 | WoL scheitert (WLAN, Schnellstart, Router) | mittel | PC wacht nicht auf | PC per LAN; Checkliste für Windows-Konfiguration |
| R9 | AccessibilityService „sperrt“ Nutzer in C3 ein | niedrig | Bedienproblem | Fest eingebauter Notausgang (Long-Press HOME / Doppel-HOME), Deinstallation per ADB |

---

## 7. Konkrete Schritte bis zum ersten Prototyp

### Schritt 0 – Daten sammeln (nur lesend, kein Risiko)
`tools/adb/collect-device-info.sh` ausführen und die Ausgabe (nach Prüfung auf private Daten) teilen.

### Schritt 1 – Baseline messen
- Speicher: `adb shell dumpsys meminfo` (Gesamt + Top-Prozesse) im Leerlauf auf Amazon-Home.
- Startzeiten: `adb shell am start -W -n <Netflix-Activity>` (Kaltstart, 3×) – Referenz für später.

### Schritt 2 – Manuelle Intent-Tests (harmlos, nur App-Starts)
1. Jede Streaming-App per `am start` starten (Befehle in Abschnitt 8.3).
2. HDMI-Passthrough-Intent mit den echten Input-IDs aus `dumpsys tv_input` testen.
3. Einstellungs-Deep-Links testen (`android.settings.SETTINGS`, `WIFI_SETTINGS`, `BLUETOOTH_SETTINGS`, `SOUND_SETTINGS`, `DISPLAY_SETTINGS`).
4. Moonlight installieren, mit Sunshine koppeln, Latenz-Overlay ablesen (1080p60 und 4K60).

### Schritt 3 – Go/No-Go je Modul dokumentieren
Ergebnisse in `docs/02-device-findings.md` eintragen; Status 🟡 → ✅/❌.

### Schritt 4 – Prototyp P0 „C3 Home“ (erst nach Freigabe)
- Ein APK, eine Activity, sechs App-Kacheln + Moonlight-Kachel + HDMI-Kacheln (falls Schritt 2 positiv).
- Akzeptanzkriterien: Kaltstart < 500 ms (`am start -W`), ≤ 40 MB PSS, flüssige D-Pad-Navigation, jede Kachel startet korrekt oder zeigt eine klare Meldung.
- Installation: `adb install -r c3-home.apk`; Rückweg: `adb uninstall de.c3.tv`.

### Schritt 5 – Prototyp P1 „Home-Umleitung“ (optional)
Eigenen minimalen AccessibilityService ergänzen, auf 8.1.8.5 testen, Speicher- und Stabilitätsmessung über mehrere Tage.

### Rückweg (gilt für alle Schritte)
```
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME   # Amazon-Home öffnen
adb uninstall de.c3.tv                                                                # C3 vollständig entfernen
```
Keine Systempakete werden deaktiviert, keine Firmware wird verändert, keine Einstellungen außerhalb von C3 werden ohne dokumentiertes Gegenkommando geändert.

---

## 8. Informationen, die ich vom Fernseher brauche

### 8.1 Vorbereitung am TV
1. *Einstellungen → Mein Fire TV (bzw. „Gerät & Software“) → Info* → 7× auf den Gerätenamen klicken → Entwickleroptionen erscheinen.
2. *Entwickleroptionen → ADB-Debugging: EIN*.
3. IP-Adresse unter *Info → Netzwerk* ablesen.
4. Am PC: `adb connect <TV-IP>:5555` und die Abfrage am TV bestätigen.

### 8.2 Lesende Abfragen
Komplett im Skript `tools/adb/collect-device-info.sh`. Was ich daraus brauche:

| Information | Befehl (Auszug) | Wofür |
| --- | --- | --- |
| API-Level, Fire-OS-Build, Plattform | `getprop ro.build.version.sdk`, `ro.build.version.release`, `ro.build.display.id`, `ro.board.platform` | SDK-Ziel, Bestätigung API 30 |
| Freier RAM | `cat /proc/meminfo`, `dumpsys meminfo` | Speicherbudget |
| Display-Modi & HDR | `dumpsys display` | Animation, Moonlight-Auflösung |
| Video-Decoder | `cat /vendor/etc/media_codecs*.xml` | HEVC/AV1/4K/HDR für Moonlight |
| Exakter Home-Launcher | `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` | Paketname für Umleitung |
| Installierte Apps | `pm list packages` | Paketnamen der sechs Apps |
| Launch-Activities | `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER <paket>` | exakte Start-Intents |
| HDMI-/TV-Inputs | `dumpsys tv_input` | Input-IDs für Passthrough |
| CEC-Geräte | `dumpsys hdmi_control` | PS5-Erkennung |
| Barrierefreiheit | `dumpsys accessibility`, `settings get secure enabled_accessibility_services` | Machbarkeit Umleitung |
| Einstellungs-Activities | `cmd package query-activities --brief -a android.settings.SETTINGS` u. a. | Deep-Links für Control Center |
| Moonlight-Exports (nach Installation) | `dumpsys package com.limelight` | ShortcutTrampoline exportiert? |

### 8.3 Aktive, aber harmlose Tests (startet nur Apps/Bildschirme)
```
# App-Start mit Zeitmessung (Paketname aus pm list packages einsetzen)
adb shell monkey -p com.netflix.ninja -c android.intent.category.LEANBACK_LAUNCHER 1

# HDMI-Passthrough (INPUT_ID aus dumpsys tv_input, URL-kodiert: / → %2F)
adb shell am start -a android.intent.action.VIEW -d "content://android.media.tv/passthrough/<INPUT_ID>"

# Einstellungs-Deep-Links
adb shell am start -a android.settings.SETTINGS
adb shell am start -a android.settings.WIFI_SETTINGS
adb shell am start -a android.settings.BLUETOOTH_SETTINGS
adb shell am start -a android.settings.SOUND_SETTINGS
adb shell am start -a android.settings.DISPLAY_SETTINGS

# Während Amazon-Bildeinstellungen offen sind: welche Activity ist das?
adb shell dumpsys activity activities | grep -E "topResumedActivity|mResumedActivity"
```

Bitte zu jedem Test notieren: *öffnet sich das Erwartete? / Fehlermeldung? / nichts?* sowie die Ausgabe des letzten Befehls auf den Einstellungsseiten **Bild**, **Ton**, **Eingänge** und **Neustart**.

### 8.4 Zusätzlich von dir
- Welche Geräte hängen an welchem HDMI-Port (PS5, sonstiges)? Ist HDMI-CEC am TV und an der PS5 aktiv?
- Gaming-PC und Laptop: per LAN oder WLAN? Windows-Version? GPU (für Sunshine-Encoder: NVENC/AMF/QSV)?
- Soundbar/AV-Receiver per ARC?
- Welche Fernbedienung (Tasten, die wir belegen könnten)?

---

## Quellen

- Xiaomi TV F Pro 75 2026 – Spezifikationen und FAQ: <https://www.mi.com/global/product/xiaomi-tv-f-pro-75-2026/specs/>, <https://www.mi.com/uk/support/faq/details/KA-567125/>
- Amazon: Fire OS 8 (Android 11 / API 30): <https://developer.amazon.com/docs/fire-tv/fire-os-8.html>
- Amazon: ADB-Verbindung zu Fire TV: <https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html>
- Home on Fire (HOME-Umleitung per AccessibilityService, getestet 8.1.7.4–8.1.8.2): <https://github.com/Khantanha8/home-on-fire>
- Launcher Manager (Lücke 2025 geschlossen): <https://xdaforums.com/t/app-firetv-noroot-launcher-manager-change-launcher-without-root.4176349/>
- Monet Launcher Issue zu Fire OS 7/8: <https://github.com/Klevico/Monet-Launcher/issues/18>
- Fire OS 7.2.7.3 blockiert Custom-Launcher: <https://www.techradar.com/news/amazon-fire-tv-update-kills-off-a-cool-secret-feature-for-cord-cutters>
- Moonlight `ShortcutTrampoline` (Extras, WoL-Verhalten): <https://github.com/moonlight-stream/moonlight-android/blob/master/app/src/main/java/com/limelight/ShortcutTrampoline.java>
- Moonlight-Latenz auf MediaTek-Geräten (Community): <https://github.com/moonlight-stream/moonlight-android/issues/689>
- HDMI-Passthrough-URIs auf Android-TVs (Community): <https://community.home-assistant.io/t/android-tv-hdmi/234411?page=2>
- Fire-TV-Sideloading-Situation 2026 (Blogs, nicht offiziell): <https://www.kunalganglani.com/blog/fire-tv-sideloading-restrictions>, <https://iptvlime.com/why-developer-options-disappeared-fire-tv-2026/>
