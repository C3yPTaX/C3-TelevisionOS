package de.c3.tv.core

import android.content.Context
import de.c3.tv.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Current weather for the header (next to the clock), fixed location Coesfeld.
 *
 * Source: Open-Meteo (free, no key, no account), HTTPS. Only the fixed coordinates are sent.
 * The last value is kept, so the first frame shows it right away; [isStale] says when to reload.
 */
class Weather(context: Context) {

    data class Now(val temperature: Int, val code: Int, val isDay: Boolean, val fetchedAt: Long) {
        val iconRes: Int get() = iconFor(code, isDay)
        val text: String get() = "$temperature°"
        val description: String get() = describe(code)
    }

    private val prefs = context.applicationContext.getSharedPreferences("c3_weather", Context.MODE_PRIVATE)

    fun cached(): Now? {
        if (!prefs.contains(KEY_TEMP)) return null
        val at = prefs.getLong(KEY_AT, 0L)
        if (System.currentTimeMillis() - at > MAX_AGE_MS) return null // too old to show
        return Now(prefs.getInt(KEY_TEMP, 0), prefs.getInt(KEY_CODE, 0), prefs.getBoolean(KEY_DAY, true), at)
    }

    fun isStale(now: Now?): Boolean = now == null || System.currentTimeMillis() - now.fetchedAt > REFRESH_MS

    /** Loads the current weather; null on any error. Call off the main thread. */
    fun fetch(): Now? = try {
        val conn = (URL(URL_COESFELD).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "C3-TelevisionOS")
        }
        try {
            if (conn.responseCode != 200) {
                null
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val current = JSONObject(body).getJSONObject("current")
                Now(
                    temperature = current.getDouble("temperature_2m").roundToInt(),
                    code = current.getInt("weather_code"),
                    isDay = current.optInt("is_day", 1) == 1,
                    fetchedAt = System.currentTimeMillis(),
                ).also { save(it) }
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun save(now: Now) {
        prefs.edit()
            .putInt(KEY_TEMP, now.temperature)
            .putInt(KEY_CODE, now.code)
            .putBoolean(KEY_DAY, now.isDay)
            .putLong(KEY_AT, now.fetchedAt)
            .apply()
    }

    companion object {
        /** Coesfeld (NRW): 51.9458 N, 7.1675 E. */
        private const val URL_COESFELD = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=51.9458&longitude=7.1675&current=temperature_2m,weather_code,is_day&timezone=Europe%2FBerlin"
        const val REFRESH_MS = 15L * 60 * 1000
        private const val MAX_AGE_MS = 3L * 60 * 60 * 1000
        private const val TIMEOUT_MS = 6000
        private const val KEY_TEMP = "temp"
        private const val KEY_CODE = "code"
        private const val KEY_DAY = "day"
        private const val KEY_AT = "at"

        /** WMO weather codes as used by Open-Meteo. */
        fun iconFor(code: Int, isDay: Boolean): Int = when (code) {
            0 -> if (isDay) R.drawable.ic_w_sun else R.drawable.ic_w_moon
            1, 2 -> if (isDay) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
            3 -> R.drawable.ic_w_cloud
            45, 48 -> R.drawable.ic_w_fog
            in 51..67, in 80..82 -> R.drawable.ic_w_rain
            in 71..77, 85, 86 -> R.drawable.ic_w_snow
            in 95..99 -> R.drawable.ic_w_storm
            else -> R.drawable.ic_w_cloud
        }

        fun describe(code: Int): String = when (code) {
            0 -> "Klar"
            1 -> "Überwiegend klar"
            2 -> "Teils bewölkt"
            3 -> "Bedeckt"
            45, 48 -> "Nebel"
            in 51..57 -> "Nieselregen"
            in 61..67 -> "Regen"
            in 71..77 -> "Schnee"
            in 80..82 -> "Regenschauer"
            85, 86 -> "Schneeschauer"
            in 95..99 -> "Gewitter"
            else -> "Wetter"
        }
    }
}
