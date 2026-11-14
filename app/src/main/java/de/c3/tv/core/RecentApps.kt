package de.c3.tv.core

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.SystemClock

/**
 * "Laufende Apps" (MENU key on C3 Home).
 *
 * Android 11 does not show third-party apps which other processes run. With the usage access
 * app-op (granted by the C3 Updater via adb, see [hasUsageAccess]) C3 lists the apps that were in
 * the foreground since the TV was switched on and have not been closed from C3 since. That is
 * "recently used", which is the best available stand-in for "running".
 *
 * Closing uses [ActivityManager.killBackgroundProcesses] (normal permission): it ends an app that
 * is in the background (C3 is in front while the list is open). Apps with a foreground service
 * (e.g. music playing on) are kept alive by Android and may restart.
 *
 * Grant: `adb shell appops set de.c3.tv GET_USAGE_STATS allow`
 * Undo:  `adb shell appops set de.c3.tv GET_USAGE_STATS default`
 */
class RecentApps(context: Context) {

    data class Entry(val pkg: String, val label: String, val lastUsed: Long)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("c3_tasks", Context.MODE_PRIVATE)
    private val am = appContext.getSystemService(ActivityManager::class.java)

    fun hasUsageAccess(): Boolean {
        val ops = appContext.getSystemService(AppOpsManager::class.java) ?: return false
        return try {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Apps used since boot (newest first) that can be started from a launcher, without C3 itself
     * and [exclude]. Empty without usage access. Call off the main thread.
     */
    fun load(launcher: AppLauncher, exclude: Set<String>): List<Entry> {
        if (!hasUsageAccess()) return emptyList()
        val usm = appContext.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val boot = now - SystemClock.elapsedRealtime()
        val events = try {
            usm.queryEvents(maxOf(boot, now - MAX_AGE_MS), now)
        } catch (e: Exception) {
            return emptyList()
        }
        val lastUsed = HashMap<String, Long>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED
            ) {
                lastUsed[event.packageName] = event.timeStamp
            }
        }
        val own = appContext.packageName
        return lastUsed.entries
            .asSequence()
            .filter { (pkg, used) -> pkg != own && pkg !in exclude && closedAt(pkg) < used }
            .filter { (pkg, _) -> launcher.launchIntent(pkg) != null }
            .map { (pkg, used) -> Entry(pkg, launcher.label(pkg) ?: pkg, used) }
            .sortedByDescending { it.lastUsed }
            .toList()
    }

    /** Ends [pkg] if it is in the background; remembers it so the list drops it. */
    fun close(pkg: String) {
        try {
            am?.killBackgroundProcesses(pkg)
        } catch (e: SecurityException) {
            // Permission missing: nothing to do.
        }
        prefs.edit().putLong(pkg, System.currentTimeMillis()).apply()
    }

    private fun closedAt(pkg: String): Long = prefs.getLong(pkg, 0L)

    companion object {
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
