package de.c3.tv.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Browser (Amazon Silk) and the private area.
 *
 * The private area is switched on/off only from the C3 Updater on the PC. While it is off,
 * nothing about it is stored on the TV: switching off deletes the list here, and the updater
 * additionally clears Silk's data (history, cookies, cache) and disables Silk.
 *
 * Wire format: extra [EXTRA_SITES] = "name|url;name|url", extra [EXTRA_ENABLED] = boolean.
 */
class WebStore(context: Context) {

    data class Site(val name: String, val url: String)

    private val prefs = context.getSharedPreferences("c3_private", Context.MODE_PRIVATE)

    fun privateSites(): List<Site> =
        if (prefs.getBoolean(KEY_ENABLED, false)) parse(prefs.getString(KEY_SITES, "").orEmpty()) else emptyList()

    /** Applies an update from the C3 Updater; returns the active private sites. */
    fun apply(enabled: Boolean, raw: String): List<Site> {
        if (!enabled) {
            prefs.edit().clear().apply()
            return emptyList()
        }
        val sites = parse(raw)
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_SITES, sites.joinToString(";") { "${it.name}|${it.url}" })
            .apply()
        return sites
    }

    companion object {
        const val ACTION_SET_PRIVATE = "de.c3.tv.action.SET_PRIVATE"
        const val EXTRA_SITES = "de.c3.tv.extra.SITES"
        const val EXTRA_ENABLED = "de.c3.tv.extra.ENABLED"
        const val SILK_PACKAGE = "com.amazon.cloud9"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SITES = "sites"

        val silk = AppEntry("Browser", listOf(SILK_PACKAGE))

        fun parse(raw: String): List<Site> = raw.split(';').mapNotNull { entry ->
            val sep = entry.indexOf('|')
            if (sep <= 0) return@mapNotNull null
            val name = entry.substring(0, sep).trim().take(40)
            val url = entry.substring(sep + 1).trim()
            val scheme = Uri.parse(url).scheme?.lowercase()
            if (name.isEmpty() || (scheme != "http" && scheme != "https")) return@mapNotNull null
            Site(name, url)
        }.take(24)

        /** Opens [url] in Silk; falls back to any installed browser. */
        fun openIntents(url: String): List<Intent> {
            val uri = Uri.parse(url)
            return listOf(
                Intent(Intent.ACTION_VIEW, uri).setPackage(SILK_PACKAGE),
                Intent(Intent.ACTION_VIEW, uri),
            )
        }
    }
}
