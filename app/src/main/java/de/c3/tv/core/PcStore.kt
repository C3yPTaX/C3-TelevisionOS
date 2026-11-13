package de.c3.tv.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Windows hosts for C3 Desktop (Sunshine + Moonlight). Configured from the PC side by the
 * C3 Updater, which reads name/UUID from Sunshine and computes the app id.
 *
 * Wire format (intent extra / storage): "name|uuid|appId|appName;name|uuid|appId|appName"
 */
class PcStore(context: Context) {

    data class Pc(val name: String, val uuid: String, val appId: String, val appName: String)

    private val prefs = context.getSharedPreferences("c3_pcs", Context.MODE_PRIVATE)

    fun load(): List<Pc> = parse(prefs.getString(KEY, "").orEmpty())

    /** Replaces the stored list; returns the parsed (validated) entries. */
    fun save(raw: String): List<Pc> {
        val pcs = parse(raw)
        prefs.edit().putString(KEY, serialize(pcs)).apply()
        return pcs
    }

    companion object {
        const val ACTION_SET_PCS = "de.c3.tv.action.SET_PCS"
        const val EXTRA_PCS = "de.c3.tv.extra.PCS"
        private const val KEY = "pcs"
        private const val MOONLIGHT_PACKAGE = "com.limelight"
        private const val MOONLIGHT_TRAMPOLINE = "com.limelight.ShortcutTrampoline"
        private val UUID_RE = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")
        private val APP_ID_RE = Regex("^[0-9]{1,10}$")

        fun parse(raw: String): List<Pc> = raw.split(';').mapNotNull { entry ->
            val p = entry.split('|').map { it.trim() }
            if (p.size < 3) return@mapNotNull null
            val name = p[0].take(40)
            if (name.isEmpty() || !UUID_RE.matches(p[1]) || !APP_ID_RE.matches(p[2])) return@mapNotNull null
            Pc(name, p[1], p[2], p.getOrNull(3)?.take(40)?.ifEmpty { null } ?: "Desktop")
        }.distinctBy { it.uuid.lowercase() }

        private fun serialize(pcs: List<Pc>): String =
            pcs.joinToString(";") { "${it.name}|${it.uuid}|${it.appId}|${it.appName}" }

        /** Starts the stream directly via Moonlight's exported shortcut entry point (v12+). */
        fun streamIntent(pc: Pc): Intent = Intent()
            .setComponent(ComponentName(MOONLIGHT_PACKAGE, MOONLIGHT_TRAMPOLINE))
            .putExtra("UUID", pc.uuid)
            .putExtra("AppId", pc.appId)
            .putExtra("AppName", pc.appName)
    }
}
