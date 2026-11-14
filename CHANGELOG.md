# Changelog

Version: Datei `VERSION` (x.y.z).

## 0.10.0

- Updater: Karte **Amazon entrümpeln** schaltet Werbung, Datensammlung (u. a. Inhaltserkennung)
  und ungenutzte Amazon-Dienste per ADB ab, ohne Root und ohne etwas zu löschen.
  Optional auch Fotos, Musik, Casting und Benachrichtigungen. **Rückgängig** schaltet alles
  wieder ein. Das Protokoll zeigt jedes Paket und den freien Arbeitsspeicher vorher/nachher.

## 0.9.1

- TV: „Einstellungen“ in der Systemleiste öffnet die Fire-TV-Einstellungen auch bei
  eingeschalteter Home-Umleitung (vorher sprang C3 sofort zurück). Home in den
  Einstellungen führt wie gewohnt zu C3.

## 0.9.0

- TV: aktuelles Wetter für Coesfeld oben links neben der Uhr (Symbol + Temperatur),
  aktualisiert alle 15 Minuten, solange der Homescreen sichtbar ist (Quelle: Open-Meteo).

## 0.8.1

- TV: „Laufende Apps“ bleibt nach dem Loslassen der Menü-Taste offen.

## 0.8.0

- TV: Die **Menü-Taste ☰** öffnet „Laufende Apps“: zuletzt benutzte Apps mit Banner,
  OK schließt eine App, dazu „Alle schließen“. Die Diagnose ist von dort erreichbar.
- Updater: erteilt beim „TV aktualisieren“ den dafür nötigen Nutzungszugriff
  (Rückweg: `adb shell appops set de.c3.tv GET_USAGE_STATS default`).

## 0.7.0

- TV: Reihe **Favoriten** statt fester Apps. Nicht installierte Apps stehen blass am Ende.
- TV: Langer Druck auf OK öffnet die Optionen einer App: Verschieben, Ausblenden,
  Ausgeblendete einblenden.
- TV: sanfter Farbschein oben in der Farbe der fokussierten App.
- TV: C3 merkt sich die zuletzt gewählte Kachel.
- TV: PC-Kacheln zeigen **Online**, **Belegt** oder **Offline** (Sunshine im Heimnetz).

## 0.6.7

- Projektregeln (`AGENTS.md`): pauschale Verbote (Root, Firmware, Systemeingriffe) durch
  Grundsätze für experimentelle Eingriffe auf ausdrücklich freigegebenen Testgeräten ersetzt.
- Neue Analyse `docs/05-usb-power.md`: Machbarkeit der softwareseitigen USB-Stromsteuerung
  (LED-Strip mit dem TV schalten) – Kurzfazit, Recherche, Belegstatus und lesende Diagnoseschritte.

## 0.6.6

- README auf Deutsch und gekürzt, Changelog gestrafft, veraltete Planungsdokumente nach `docs/archiv/`.

## 0.6.5

- Updater: aktualisiert sich selbst („Updater aktualisieren“). Eine neue Version wird grün angezeigt.

## 0.6.4

- Updater: graue Hinweistexte in leeren Eingabefeldern.

## 0.6.3

- TV: Die Browser-Kachel („Web“) ist nur noch bei „Privat an“ sichtbar.

## 0.6.2

- TV: Reihen schieben sich beim Scrollen nicht mehr über den Titel. Kompaktere Abstände.

## 0.6.1

- Updater: zweispaltiges Fenster, passt auf Laptop-Bildschirme.

## 0.6.0

- „An C3 TV senden“: Links vom iPhone (Kurzbefehl) öffnen sich am TV, nur bei „Privat an“, mit Code geschützt.
- TV im Heimnetz als `c3tv.local` erreichbar. Updater: „iPhone einrichten“.

## 0.5.0

- TV: Browser-Kachel (Silk) und privater Bereich mit eigenen Seiten.
- Updater: Karte „Privat“ (Seiten verschlüsselt, Privat an/aus).

## 0.4.0

- Erste versionierte Ausgabe: C3 Home mit Apps, PS5/HDMI, C3 Desktop, Home-Umleitung, neues Design.
- Updater mit Geräten, PCs, Sunshine-Installation und Kanalwahl. Release-ZIP.
