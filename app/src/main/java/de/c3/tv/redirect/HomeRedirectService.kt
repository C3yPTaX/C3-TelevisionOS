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
 * home. This service only receives TYPE_WINDOW_STATE_CHANGED events of the Amazon
 * launcher package (see res/xml/home_redirect_service.xml) and brings C3 Home to the
 * front when Amazon's home screen appears. It reads no window content.
 *
 * Escape hatch: pressing HOME again within [ESCAPE_WINDOW_MS] after a redirect keeps
 * Amazon's home screen (e.g. for profiles, the app store or Amazon's own menus).
 *
 * Enable/disable via ADB (Fire OS hides the accessibility settings page):
 *   adb shell settings put secure enabled_accessibility_services de.c3.tv/de.c3.tv.redirect.HomeRedirectService
 *   adb shell settings put secure accessibility_enabled 1
 * The C3 Updater has buttons for both directions.
 */
class HomeRedirectService : AccessibilityService() {

    private var lastRedirectAt = 0L
    private var escapedUntil = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != AMAZON_LAUNCHER) return
        val className = event.className?.toString().orEmpty()
        Log.d(TAG, "Amazon launcher window: $className")
        if (!className.contains(HOME_ACTIVITY_MARKER)) return

        val now = SystemClock.elapsedRealtime()
        val sinceRedirect = now - lastRedirectAt
        when {
            // Duplicate event of the same HOME press.
            sinceRedirect < DUPLICATE_WINDOW_MS -> return
            // User escaped to Amazon home recently: leave it alone for a while.
            now < escapedUntil -> return
            // Second HOME press shortly after a redirect: stay on Amazon home.
            sinceRedirect < ESCAPE_WINDOW_MS -> {
                escapedUntil = now + ESCAPE_HOLD_MS
                Log.i(TAG, "Escape to Amazon home")
                return
            }
        }

        lastRedirectAt = now
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Redirect failed", e)
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "C3HomeRedirect"
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"
        private const val HOME_ACTIVITY_MARKER = "HomeActivity"
        private const val DUPLICATE_WINDOW_MS = 400L
        private const val ESCAPE_WINDOW_MS = 2000L
        private const val ESCAPE_HOLD_MS = 60_000L

        /** True if this service is listed in the enabled accessibility services. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.equals("${context.packageName}/${HomeRedirectService::class.java.name}", ignoreCase = true) }
        }
    }
}
