package de.c3.tv.core

/**
 * An app C3 can launch. Fire OS variants of the same app sometimes use different
 * package names, so each entry lists candidates in order of preference.
 */
data class AppEntry(
    val title: String,
    val packages: List<String>,
)

object AppCatalog {

    /** The six approved streaming apps (README, scope v1.0). */
    val streaming: List<AppEntry> = listOf(
        AppEntry("Netflix", listOf("com.netflix.ninja")),
        AppEntry("Prime Video", listOf("com.amazon.firebat", "com.amazon.avod")),
        AppEntry(
            "YouTube",
            listOf("com.amazon.firetv.youtube", "com.amazon.firetv.youtube.tv", "com.google.android.youtube.tv"),
        ),
        AppEntry("Twitch", listOf("tv.twitch.android.viewer", "tv.twitch.android.app")),
        AppEntry("Disney+", listOf("com.disney.disneyplus")),
        AppEntry("RTL+", listOf("de.cbc.tvnow.firetv")),
    )

    val moonlight = AppEntry("Moonlight", listOf("com.limelight"))
}
