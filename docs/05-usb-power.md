# C3-TelevisionOS – USB-Stromversorgung softwareseitig steuern (Analyse)

> Ziel: Ein am USB-Port des TV angeschlossener LED-Strip soll automatisch mit dem
> Fernseher ein- und ausgeschaltet werden, möglichst später in C3 integriert.
> Stand: 2026-11-14. Dieses Dokument ist **nur Analyse** – es wurde noch nichts am
> Gerät geändert. Bezug: [AGENTS.md](../AGENTS.md), [02-device-findings.md](02-device-findings.md).

## 0. Kurzfazit (zuerst lesen)

- **Ohne Root gibt es keinen nachgewiesenen Weg**, die USB-Portspannung (VBUS) aus einer
  App oder per ADB gezielt zu schalten. Android bietet dafür keine öffentliche API, und der
  ADB-Benutzer (`shell`, uid 2000) hat keinen Schreibzugriff auf die Strom-/Regler-Knoten.
- **Mit Root ist es technisch plausibel, aber für dieses Gerät ungetestet.** VBUS hängt bei
  MediaTek-TV-SoCs an einem GPIO-gesteuerten Festspannungsregler; ob er sich zur Laufzeit
  einzeln schalten lässt (und nicht an andere Stromschienen wie HDMI gekoppelt ist), ist für
  den MT5896 nicht belegt.
- **Für den MT5896 / Codename `ginzaeu` ist derzeit kein öffentlicher Root-/Unlock-Weg
  bekannt.** Vorhandene Fire-OS-8-Exploits betreffen andere Modelle (Sticks/Cube).
- **Der billigste echte Hebel braucht gar keinen Systemeingriff:** zuerst lesend prüfen, ob der
  USB-Port im Standby ohnehin stromlos wird und ob eine „Schnellstart“-Einstellung das
  verhindert. Wenn der Port dem TV-Standby folgt, ist das Ziel ohne jede Modifikation erreicht.

Empfehlung: **Phase A (lesende Diagnose, Abschnitt 5) ausführen, bevor über Root entschieden
wird.** Externe Smart-Steckdosen sind laut Auftrag aktuell keine gewünschte Alternative.

## 1. Gerät (verifiziert, aus [02-device-findings.md](02-device-findings.md))

| Merkmal | Wert |
| --- | --- |
| Modell | Xiaomi TV F Pro 75 (Vermarktung), `ro.product.model=AFTBR92D74` |
| Codename | `ginzaeu` |
| Fire OS / Android | 8.1.8.5 (`RS8185.3885N`) / Android 11, API 30 |
| SoC | MediaTek **MT5896** (`ro.hardware=mt5896`, Platform `merak`), 4× Cortex-A55 |
| Userspace | vermutlich 32-Bit (`/proc/cpuinfo`: *CPU architecture 7*) – 🟡 per `ro.product.cpu.abilist` zu bestätigen |
| ADB | aktiv und im Projekt etabliert (Updater nutzt `adb` über Netzwerk) |

Nicht aus dem Repo belegt und **noch zu messen**: Anzahl/Typ der USB-Ports, deren
Stromabgabe, und ob sie im Standby Strom führen.

## 2. Recherche: vergleichbare Geräte

- **USB-Port-Strom per Software schalten** ist generell Hardware-/Kernel-nah. `uhubctl` kann
  Portstrom nur an USB-Hubs schalten, die *Per-Port Power Switching* implementieren – und
  selbst dann kappen manche VBUS nicht wirklich. Für einen fest verbauten TV-Port hilft das
  nicht. (uhubctl-Projekt; LibreELEC/CoreELEC-Foren.)
- **MediaTek-Boards** treiben VBUS üblicherweise über einen `regulator-fixed` im Device-Tree
  mit GPIO-Enable-Pin. Das Umschalten ist damit normalerweise eine Kernel-/DT-Sache, kein
  einfacher Laufzeit-`sysfs`-Write – außer der Enable-Knoten ist zur Laufzeit erreichbar.
- **TV-Boxen**: In Foren wird berichtet, dass USB-VCC teils an derselben Schiene wie HDMI
  hängt („HDMI aus ⇒ USB aus“), also nicht sauber einzeln schaltbar ist. Für den konkreten
  MT5896-TV ist das offen.
- **Android-Terminal**: Eine unbeantwortete Stack-Overflow-Frage zum gezielten Deaktivieren
  eines USB-Ports bestätigt, dass die Linux-üblichen Wege aus einem Android-Userspace in der
  Regel nicht greifen.
- **Root für Fire OS 8**: öffentlich dokumentierte Temp-Root-Exploits existieren (u. a. auf
  Basis einer 2026er CVE) – aber für **andere** Codenamen (z. B. Stick 4K 2nd Gen, Cube 3rd
  Gen), nicht für `ginzaeu`/MT5896. Übertragbarkeit ist **nicht belegt**.

> Quellen (Recherche 2026-11): uhubctl (github.com/mvp/uhubctl), LibreELEC-/CoreELEC-Foren
> zu USB-Portstrom, MediaTek-Device-Tree-Beispiele (lore.kernel.org / linux-mediatek),
> XDA-Threads zu Fire-OS-8-Temp-Root (andere Modelle). Kein Treffer, der VBUS-Steuerung auf
> MT5896/`ginzaeu` zeigt.

## 3. Geprüfte Wege

### 3.1 Ohne Root – App-/ADB-Ebene
- **Android-APIs**: `UsbManager` kann Geräte *aufzählen* und Berechtigungen anfragen, aber
  **nicht** die Portspannung schalten. Keine öffentliche API für VBUS/Port-Power.
- **ADB `shell`**: uid 2000 hat keinen Schreibzugriff auf `/sys/class/regulator/*/state`,
  GPIO- oder Power-Supply-Knoten. Analog zum bereits dokumentierten Muster (HDMI-Umschaltung,
  Bild/Ton-Seiten) sind solche systemnahen Aktionen für Drittcode gesperrt.
- **Bewertung:** ❌ kein verifizierter Weg.

### 3.2 Standby-Verhalten des Ports (ohne Systemeingriff) — **zuerst prüfen**
- Viele Fire-/Android-TVs kappen die USB-Spannung im **tiefen** Standby, halten sie aber im
  **Schnellstart-/Quick-Start-Modus**. Ist Schnellstart aus, folgt der Port evtl. schon dem
  TV-Zustand – dann ist das Ziel **ohne jede Modifikation** erfüllt.
- Zu klären (lesend + eine Menü-Einstellung, reversibel): Hat der TV eine
  „Schnellstart“/„Bei Standby verbunden bleiben“-Option? Führt der Port im Standby Strom?
- **Bewertung:** 🟡 vielversprechend, risikoarm, als Erstes messen.

### 3.3 Mit Root
- Erst möglich, wenn für `ginzaeu`/MT5896 ein Root-Weg existiert (siehe 3.4).
- Wäre Root vorhanden, lesend den passenden Knoten suchen
  (`/sys/class/regulator/`, `power_supply`, MediaTek-USB-Nodes unter `/sys/devices/platform/`,
  ggf. `otg`/`vbus`) und testweise schalten. Risiko: Kopplung an andere Schienen; Wirkung
  unbekannt. **Reversibel** (Wert zurückschreiben / Reboot), solange nur Laufzeit-Knoten
  beschrieben werden und keine Firmware/DT angefasst wird.
- **Bewertung:** 🟡 plausibel, ungetestet.

### 3.4 Ist Root überhaupt verfügbar?
- **Kein öffentlicher Root-/Bootloader-Unlock für dieses Modell bekannt.** Vorhandene
  Fire-OS-8-Exploits betreffen andere Codenamen. Eine Übertragung ist unbelegt und birgt
  Brick-Risiko.
- Nächster, **nicht verändernder** Schritt: gezielte Suche nach `AFTBR92D74` / `ginza` /
  `MT5896` in einschlägigen Foren, bevor irgendetwas ausgeführt wird.
- **Bewertung:** 🔴 derzeit kein belegter Weg; irreversibel-riskant ⇒ nur mit ausdrücklicher
  Freigabe und erst nach Hardware-/Foren-Recherche (siehe AGENTS.md).

## 4. Einordnung nach Belegstatus

| Aussage | Status |
| --- | --- |
| Ohne Root per App/ADB keine VBUS-Steuerung | ✅ verifiziert (Android-Modell + Projektbefunde) |
| USB-Portstrom hängt bei MT-SoCs an GPIO-Regler | 🟡 Vermutung (allg. MediaTek, nicht am Gerät gemessen) |
| Port folgt evtl. schon dem Standby / per Schnellstart steuerbar | 🟡 Vermutung, leicht prüfbar |
| Mit Root per sysfs-Regler schaltbar | 🟡 ungetestet (für dieses Gerät) |
| Öffentlicher Root für `ginzaeu`/MT5896 | 🔴 nicht bekannt |

## 5. Phase A — lesende Diagnose (nächster Schritt, nichts wird geändert)

Alle Befehle sind **read-only**. Am besten in `tools/adb/collect-device-info.sh` als eigenen
Abschnitt ergänzen. Zuerst Verbindung: `adb connect <TV-IP>:5555`.

```sh
# Rechte und Architektur
adb shell id
adb shell getprop ro.product.cpu.abilist

# USB-Subsystem
adb shell dumpsys usb
adb shell ls -l /sys/bus/usb/devices/ 2>/dev/null
adb shell ls -l /sys/devices/platform/ 2>/dev/null | grep -iE "usb|mtu3|xhci|musb|otg"

# Strom-/Reglerknoten (Lesbarkeit und Namen, NICHT schreiben)
adb shell ls -l /sys/class/regulator/ 2>/dev/null
adb shell 'for d in /sys/class/regulator/*/; do n=$(cat "$d/name" 2>/dev/null); s=$(cat "$d/state" 2>/dev/null); echo "$d  name=$n  state=$s"; done' 2>/dev/null
adb shell ls -l /sys/class/power_supply/ 2>/dev/null
adb shell 'getprop | grep -iE "usb|otg|vbus"'

# Standby/Schnellstart (der billigste Hebel)
adb shell settings list global 2>/dev/null | grep -iE "boot|standby|usb|suspend"
adb shell dumpsys power 2>/dev/null | grep -iE "wake|boot|standby|suspend" | head
```

Zusätzlich **ohne ADB**: im TV-Menü nach „Schnellstart“/„Schnelles Einschalten“ suchen und
– reversibel – testen, ob der USB-Port im Standby dann stromlos wird (Multimeter oder einfach
am Verhalten des LED-Strips). Das ist der schnellste Weg zu einer Lösung ohne Systemeingriff.

## 6. Implementierungsplan für C3 (bedingt)

Die Integration hängt vom Ergebnis von Phase A ab. Drei Szenarien:

1. **Port folgt bereits dem Standby (kein Eingriff nötig).**
   C3 muss nichts tun. Dokumentieren, ggf. im README als unterstützter LED-Anwendungsfall.

2. **Root wird verfügbar und ein schaltbarer Laufzeit-Knoten existiert.**
   - Kleiner Helper in C3 (`core/UsbPower.kt`): schreibt den in Phase A gefundenen Knoten über
     eine Root-Shell (`su -c "echo 0 > <node>"` / `… 1 …`). Knotenpfad **konfigurierbar**, kein
     fest verdrahteter Pfad.
   - Auslöser „mit dem TV ein/aus“: Fire-TV-„Aus“ = Screen-Off/Standby. Ein `BroadcastReceiver`
     auf `ACTION_SCREEN_OFF`/`ACTION_SCREEN_ON` (registriert aus einem leichten Dienst) schaltet
     den Knoten. Beim Boot Anfangszustand setzen.
   - Updater: Schalter „LED mit TV schalten“ plus Anzeige des erkannten Knotens/Status.
   - Rückweg: Funktion deaktivieren = C3 fasst den Knoten nicht mehr an; Reboot stellt den
     Standardzustand her. Kein Firmware-/DT-Eingriff.

3. **Kein Root und Port folgt nicht dem Standby.**
   Softwareseitig auf diesem Gerät **nicht lösbar**. Dann erst Entscheidung des Betreibers, ob
   ein Root-Weg erforscht werden soll (irreversibel-riskant ⇒ ausdrückliche Freigabe nötig),
   oder ob die Auftragsvorgabe „keine Zusatzhardware“ gelockert wird.

## 7. Rückwege (Dokumentation vorhandener Wiederherstellung)

- **Phase A**: rein lesend, kein Rückweg nötig.
- **Schnellstart-Einstellung**: im Menü umschaltbar, jederzeit zurückstellbar.
- **Root-Laufzeit-Knoten schreiben** (falls je relevant): ursprünglichen Wert vorher auslesen
  und notieren; Reboot stellt den DT-Standard wieder her.
- **Root/Firmware/Bootloader**: nicht Teil dieser Phase. Solche irreversiblen Schritte erst nach
  gesonderter Hardware-/Foren-Recherche und ausdrücklicher Freigabe (AGENTS.md).
