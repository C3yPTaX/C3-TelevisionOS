package de.c3.tv.redirect

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import de.c3.tv.MainActivity

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
 *
 * Enable/disable via ADB or the C3 Updater (Fire OS hides the accessibility settings page).
 */
class HomeRedirectService : AccessibilityService() {

    private var lastRedirectAt = 0L
    private var escaped = false

    override fun onServiceConnected() {
        super.onServiceConnected()
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
            // Any real app (or C3 itself) in front ends an escape. System overlays don't count.
            if (escaped && pkg !in IGNORED_PACKAGES) {
                escaped = false
                Log.d(TAG, "Escape ended by $pkg")
            }
            return
        }

        Log.d(TAG, "Amazon launcher window: $className (escaped=$escaped)")
        if (!className.contains(HOME_ACTIVITY_MARKER)) return
        if (escaped) return

        val now = SystemClock.elapsedRealtime()
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

    companion object {
        private const val TAG = "C3HomeRedirect"
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"
        private const val HOME_ACTIVITY_MARKER = "HomeActivity"
        private const val DUPLICATE_WINDOW_MS = 400L
        private const val ESCAPE_WINDOW_MS = 1200L
        private const val BOOT_WINDOW_MS = 5 * 60_000L
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
