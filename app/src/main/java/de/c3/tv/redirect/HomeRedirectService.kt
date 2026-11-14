package de.c3.tv.redirect

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import de.c3.tv.MainActivity
import de.c3.tv.remote.LinkReceiver

/**
 * Optional HOME redirect (off by default).
 *
 * Fire OS does not allow third-party launchers, so Amazon's launcher stays the system
 * home. This service only receives TYPE_WINDOW_STATE_CHANGED events (no window content)
 * and brings C3 Home to the front when Amazon's home screen appears.
 *
 * - Escape: a second HOME press within [ESCAPE_WINDOW_MS] after a redirect keeps Amazon
 *   home. The escape lasts until any other app (or C3) comes to the front.
 * - Boot: Amazon home is usually shown before this service is connected, so C3 is opened
 *   once when the service connects shortly after boot.
 * - Settings: Fire TV's settings run inside Amazon's launcher (same window class as its home).
 *   When C3 opens them ([requestSettings]) the redirect pauses while the user stays in the
 *   launcher/settings. HOME ends the pause and opens C3; for that the service watches keys
 *   only during the pause (never consumed). Any other app or C3 in front ends it as well.
 *
 * Enable/disable via ADB or the C3 Updater (Fire OS hides the accessibility settings page).
 */
class HomeRedirectService : AccessibilityService() {

    private var lastRedirectAt = 0L
    private var escaped = false
    private var settingsMode = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        LinkReceiver.onServiceConnected(this)
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
            Log.i(TAG, "Connected shortly after boot – opening C3")
            openC3()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        if (pkg != AMAZON_LAUNCHER) {
            if (settingsMode && pkg !in SETTINGS_PACKAGES && pkg !in IGNORED_PACKAGES) endSettings("left for $pkg")
            // Any real app (or C3 itself) in front ends an escape. System overlays don't count.
            if (escaped && pkg !in IGNORED_PACKAGES) {
                escaped = false
                Log.d(TAG, "Escape ended by $pkg")
            }
            return
        }

        Log.d(TAG, "Amazon launcher window: $className (escaped=$escaped, settings=$settingsMode)")
        if (!className.contains(HOME_ACTIVITY_MARKER)) return

        val now = SystemClock.elapsedRealtime()
        if (now - settingsRequestedAt < ESCAPE_REQUEST_MS) {
            settingsRequestedAt = 0L
            startSettings()
            return
        }
        if (settingsMode) return
        if (escaped) return

        if (now - escapeRequestedAt < ESCAPE_REQUEST_MS) {
            // C3's own "Fire TV" button: the user explicitly wants Amazon home.
            escaped = true
            Log.i(TAG, "Escape requested by C3")
            return
        }
        val sinceRedirect = now - lastRedirectAt
        if (sinceRedirect < DUPLICATE_WINDOW_MS) return // same HOME press
        if (sinceRedirect < ESCAPE_WINDOW_MS) {
            escaped = true
            Log.i(TAG, "Escape to Amazon home")
            return
        }

        lastRedirectAt = now
        openC3()
    }

    private fun startSettings() {
        settingsMode = true
        setKeyFilter(true)
        Log.i(TAG, "Settings opened by C3 – redirect paused")
    }

    private fun endSettings(reason: String) {
        if (!settingsMode) return
        settingsMode = false
        setKeyFilter(false)
        Log.i(TAG, "Settings pause ended ($reason)")
    }

    /** Key events are only requested during the settings pause. */
    private fun setKeyFilter(enabled: Boolean) {
        val info = serviceInfo ?: return
        info.flags = if (enabled) {
            info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS.inv()
        }
        serviceInfo = info
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (settingsMode && event.keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_DOWN) {
            endSettings("HOME")
            // HOME on Amazon home may not produce a window event: open C3 unless the normal
            // redirect already did.
            val pressedAt = SystemClock.elapsedRealtime()
            handler.postDelayed({
                if (lastRedirectAt < pressedAt && !escaped) {
                    lastRedirectAt = SystemClock.elapsedRealtime()
                    openC3()
                }
            }, HOME_FALLBACK_MS)
        }
        return false // never consume: HOME and all other keys work as usual
    }

    private fun openC3() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Opening C3 failed", e)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        LinkReceiver.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        LinkReceiver.onServiceDisconnected()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "C3HomeRedirect"
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"
        private const val HOME_ACTIVITY_MARKER = "HomeActivity"
        private const val DUPLICATE_WINDOW_MS = 400L
        private const val ESCAPE_WINDOW_MS = 1200L
        private const val BOOT_WINDOW_MS = 5 * 60_000L
        private const val ESCAPE_REQUEST_MS = 3000L
        private const val HOME_FALLBACK_MS = 700L

        @Volatile
        private var escapeRequestedAt = 0L

        @Volatile
        private var settingsRequestedAt = 0L

        /** Called by C3 before it opens Amazon home on purpose (same process). */
        fun requestEscape() {
            escapeRequestedAt = SystemClock.elapsedRealtime()
        }

        /** Called by C3 before it opens the Fire TV settings (they live in Amazon's launcher). */
        fun requestSettings() {
            settingsRequestedAt = SystemClock.elapsedRealtime()
        }

        /** Settings pages outside the launcher (Netzwerk, Bluetooth, …) keep the settings pause. */
        private val SETTINGS_PACKAGES = setOf(
            "com.amazon.tv.settings.v2",
            "com.amazon.tv.settings",
        )
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.amazon.tv.ime",
            "com.amazon.tv.quicksettings.ui",
            "com.amazon.tv.quicksettings",
        )

        /** True if this service is listed in the enabled accessibility services. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            val me = "${context.packageName}/${HomeRedirectService::class.java.name}"
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
