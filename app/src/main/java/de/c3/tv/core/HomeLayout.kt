package de.c3.tv.core

import android.content.Context

/**
 * User arrangement of the home screen, stored only on the TV (SharedPreferences):
 * - favourites: ordered installed apps of the first row ("pkg|label", label cached for the first frame)
 * - hidden: tile keys ("app:<pkg>" or catalog placeholder "cat:<Title>") the user removed from view
 * - seen catalog entries: catalog apps that were once added automatically, so removing one
 *   from the favourites is permanent
 * - last focused tile, restored on the next start
 *
 * Reset: `adb shell pm clear de.c3.tv` (also clears the PC list and the private area).
 */
class HomeLayout(context: Context) {

    data class Favourite(val pkg: String, val label: String)

    private val prefs = context.getSharedPreferences("c3_home", Context.MODE_PRIVATE)

    val isInitialized: Boolean get() = prefs.contains(KEY_FAVOURITES)

    fun favourites(): List<Favourite> = prefs.getString(KEY_FAVOURITES, "").orEmpty()
        .split(SEP_ENTRY).mapNotNull { e ->
            val i = e.indexOf(SEP_FIELD)
            if (i <= 0) null else Favourite(e.substring(0, i), e.substring(i + 1))
        }.distinctBy { it.pkg }

    fun saveFavourites(list: List<Favourite>) {
        prefs.edit().putString(
            KEY_FAVOURITES,
            list.distinctBy { it.pkg }.joinToString(SEP_ENTRY) { it.pkg + SEP_FIELD + clean(it.label) },
        ).apply()
    }

    fun hidden(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun setHidden(keys: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN, keys.toSet()).apply()
    }

    fun seenCatalog(): Set<String> = prefs.getStringSet(KEY_SEEN, emptySet()).orEmpty().toSet()

    fun setSeenCatalog(titles: Set<String>) {
        prefs.edit().putStringSet(KEY_SEEN, titles.toSet()).apply()
    }

    var lastFocus: String?
        get() = prefs.getString(KEY_FOCUS, null)
        set(value) {
            prefs.edit().putString(KEY_FOCUS, value).apply()
        }

    private fun clean(label: String): String =
        label.replace(SEP_ENTRY, " ").replace(SEP_FIELD, " ").trim().take(60)

    companion object {
        private const val KEY_FAVOURITES = "favourites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_SEEN = "seen_catalog"
        private const val KEY_FOCUS = "last_focus"
        private const val SEP_ENTRY = "\n"
        private const val SEP_FIELD = "|"

        fun catalogKey(entry: AppEntry): String = "cat:" + entry.title
    }
}
