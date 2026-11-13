# C3 Television OS – Designvorschlag & Umsetzungsplan

> Status: **Vorschlag, wartet auf Freigabe.** Visuelle Vorschau: Design-Canvas (Homescreen, Designsystem, Updater).
> Umsetzung nach Freigabe auf eigenem Branch (`claude/design-refresh`).

## 1. Ist-Analyse der Oberfläche

| Bereich | Heute | Schwäche |
| --- | --- | --- |
| Aufbau | 1 Activity, Views im Code (`MainActivity`, `core/Ui.kt` → `Tile`) | gut und schnell – bleibt so |
| Kopf | „C3“ + Uhr | kein Bezug zur Auswahl |
| Kacheln | eine Kachelart (Text oder Banner), 132×74 dp, Radius 12 dp | Geräte und System sehen aus wie Apps; Systemeinträge als volle Kacheln überladen die Seite |
| Fokus | Skalierung 1,1 + Rand | kein Glanz, kein Nachfedern, kein Titel |
| Farben | dunkles Grau, ein Blau | einheitlich, aber flach |
| Schrift | Systemschrift | wirkt generisch |

Logik (App-Start, PCs, PS5-Player, HDMI, Home-Umleitung, Diagnose) bleibt **unverändert**; nur Darstellung wird ersetzt.

## 2. Designsystem (Kurzfassung)

- **Farben:** Hintergrund #0A0B0F mit zwei festen Lichtscheinen; Kachel #232833→#15181E; Glas = Weiß 10 %→4 % + helle Oberkante; Text #F2F4F7 (100/62/38 %); ein Akzent **C3-Blau #5AA9FF** nur für Fokus/Aktiv; Status Grün #34C77B, Hinweis #F5A524.
- **Schrift:** Inter (Open Font License), gebündelt in 2 Schnitten (Medium, Semibold/Bold). Titel 38 sp, Uhr 17, Kachel 13, Reihe 12, Status 9.
- **Raster & Ränder:** 4-dp-Raster; sicherer Rand 40 dp seitlich, 30 dp oben/unten (entspricht der tvOS-Empfehlung für Overscan).
- **Kacheln:** App 131×73 dp (16:9-Banner), Radius 12 dp · Gerät 150×84 dp, Glas, Symbol + Name + Status-Kapsel · System als **Glas-Kapselleiste** unten.
- **Fokus:** 1,12× mit leichtem Nachfedern (180 ms), weißer Rand, Glanzverlauf oben, weicher Schatten, schwacher Akzent-Schein; Name erscheint unter der fokussierten App. Drücken 1,04× (90 ms). Nicht installiert: 38 % Deckkraft.
- **Oberer Bereich („Top Shelf“):** großer Titel + Kategorie der fokussierten Kachel, 160 ms Überblendung.
- **Bewegung:** nur bei Eingabe; im Leerlauf keine Animation.
- **Symbole:** eigene Strich-Symbole (Controller, Monitor, HDMI, Zahnrad, WLAN, Bluetooth, Info, Haus). Keine Apple-Symbole, -Schriften, -Hintergründe.
- **Name:** „C3 Television OS“, Zusatz „by Nico Lukowski“ dezent unten rechts und im Updater.

## 3. Liquid Glass – was auf diesem TV geht

Echtzeit-Unschärfe (`RenderEffect`) gibt es erst ab Android 12; Fire OS 8 ist Android 11. Glas wird deshalb aus **festen Verläufen und Rändern** gezeichnet (statische Drawables, GPU-günstig). Keine Unschärfe, kein Neuberechnen pro Bild.

## 4. Technischer Plan (Android)

1. `res/values`: Farb-, Maß- und Text-Tokens (`colors.xml`, `dimens.xml`, `styles.xml`), `res/font`: Inter.
2. `res/drawable`: Glas-, Kachel-, Fokus- und Glanz-Drawables; Strich-Symbole als Vektoren.
3. `core/Ui.kt`: `Tile` → drei Varianten (`AppTile`, `DeviceTile`, `DockButton`) mit gemeinsamem `FocusAnimator` (Hardware-Layer während der Animation).
4. `MainActivity.buildLayout()`: neuer Kopf, Top-Shelf-Titel, zwei Reihen (Apps, Geräte), Systemleiste. Bestehende Handler/Refresh-Logik wird nur angeschlossen.
5. `InputPlayerActivity`: Hinweis-Overlay als Glas-Kapsel.
6. App-Name im Launcher: „C3 Television OS“.
7. Messung vorher/nachher: Kaltstart, `dumpsys meminfo`, `dumpsys gfxinfo` (Ruckler beim Fokuswechsel).

**Budget:** ≤ 75 MB Vordergrund, Kaltstart ≤ 1,1 s, Fokuswechsel ohne verworfene Frames.

## 5. C3 Updater (Windows)

- Dunkle Titelleiste (DWM), Karten statt loser Felder: **Fernseher**, **Home-Taste** (Umschalter C3/Amazon), **C3 Desktop · PCs**, **Dieser PC · Sunshine**; Protokoll einklappbar.
- Abgerundete, selbst gezeichnete Schaltflächen; Segoe UI Variable; gleiches Farbsystem wie der TV.
- Kopf: Icon, „C3 Television OS“, „by Nico Lukowski“, Verbindungsstatus mit Version.

### Neu: „Sunshine installieren“ (Nutzerwunsch)

1. **Erkennen:** Windows-Version und Architektur, Grafikkarte (WMI `Win32_VideoController`) inkl. Hardware-Encoder (NVIDIA/AMD/Intel), Netzwerk (LAN/WLAN), Sunshine installiert/läuft (Dienst, `localhost:47989`).
2. **Installieren:** über **winget** mit dem offiziellen Paket von LizardByte (signiert, mit Windows-Sicherheitsabfrage). Fallback, wenn winget fehlt: offizielles Installationsprogramm aus dem GitHub-Release von LizardByte.
3. **Danach:** Sunshine-Weboberfläche öffnen (Benutzer und Passwort legt man dort selbst an), Hinweis zur Kopplung mit Moonlight am TV. Anschließend „Diesen PC hinzufügen“ anbieten.
4. Nicht automatisch: Passwörter setzen, Firewall- oder Treiber-Änderungen außerhalb des Sunshine-Installers.
