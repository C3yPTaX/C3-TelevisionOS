package de.c3.tv.core

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings

/**
 * Fire OS settings pages that a third-party app may open (verified on Fire OS 8.1.8.5,
 * see docs/02-device-findings.md). Picture, sound and HDMI-CEC pages are
 * signature-protected and therefore intentionally absent.
 */
object SystemShortcuts {

    data class Shortcut(val title: String, val subtitle: String, val intent: Intent)

    val all: List<Shortcut> = listOf(
        Shortcut("Einstellungen", "Fire TV", Intent(Settings.ACTION_SETTINGS)),
        Shortcut("Netzwerk", "WLAN", Intent(Settings.ACTION_WIFI_SETTINGS)),
        Shortcut(
            "Bluetooth",
            "Fernbedienungen & Geräte",
            Intent().setComponent(
                ComponentName(
                    "com.amazon.tv.settings.v2",
                    "com.amazon.tv.settings.v2.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity",
                ),
            ),
        ),
        Shortcut("Geräteinfo", "Neustart & Updates", Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        Shortcut(
            "Fire TV Home",
            "Zurück zu Amazon",
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        ),
    )
}
