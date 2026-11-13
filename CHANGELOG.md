# Changelog

Nur die wesentlichen Änderungen. Version: Datei `VERSION` (x.y.z).

## 0.6.0

- Neu: „An C3 TV senden“ – Links vom iPhone (Teilen-Menü, App „Kurzbefehle“) öffnen sich
  direkt in Silk auf dem TV. Nur aktiv, solange „Privat“ an ist; geschützt durch einen
  geheimen Code; keine Speicherung gesendeter Links.
- TV ist im Heimnetz als `c3tv.local` erreichbar (IP-Wechsel egal); Ersatzseite unter
  `http://c3tv.local:8765` zum Einfügen eines Links von jedem Gerät.
- Updater: „iPhone einrichten“ mit Schritt-für-Schritt-Anleitung, Adresse und Code zum
  Kopieren; „Neuen Code erzeugen“.
- Diagnose-Seite zeigt, ob der Link-Empfang aktiv ist.

## 0.5.0

- TV: neue Reihe „Web“ mit Browser-Kachel (Amazon Silk).
- TV: privater Bereich – Reihe „Privat“ mit eigenen Seiten, nur sichtbar, wenn im Updater eingeschaltet.
  Ausgeschaltet speichert der TV nichts davon.
- TV: Reihen scrollen senkrecht, Titelbereich und Systemleiste bleiben fest.
- Updater: Karte „Privat“ – Seiten verwalten (auf dem PC verschlüsselt gespeichert),
  „Privat an“ (Silk aktivieren, Seiten senden), „Privat aus“ (Reihe entfernen,
  Silk-Daten löschen, Silk deaktivieren; bei Systempaket nur Daten löschen).
- Updater: Fenster passt sich an kleine/skalierte Bildschirme an (scrollt bei Bedarf).

## 0.4.0

- Erste versionierte Ausgabe von C3 Television OS.
- TV: C3 Home mit sechs Streaming-Apps, PlayStation 5 und HDMI-Eingängen im Vollbild,
  C3 Desktop (direkter Moonlight-Stream), optionale Umleitung der Home-Taste.
- TV: neues Design (Inter, Glas-Kacheln, Fokus mit Glanz, Titelbereich, Systemleiste).
- Updater: neues Design, Geräte und PCs verwalten, Home-Taste umschalten,
  Sunshine erkennen und installieren, Kanal „Stabil“ oder „Entwicklerversion“.
- Release-ZIP `C3-TelevisionOS_vX.Y.Z.zip` mit Updater, APK, README und Changelog.
