package de.c3.tv.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/** Resolves and starts other apps using only public, permission-free APIs. */
class AppLauncher(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    /** First installed package of [entry] that has a launchable activity, or null. */
    fun resolve(entry: AppEntry): String? = entry.packages.firstOrNull { launchIntent(it) != null }

    fun launchIntent(packageName: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Display name of an installed package, or null. Call off the main thread. */
    fun label(packageName: String): String? = try {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString().trim().ifEmpty { null }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** TV banner of the app (activity banner, then app banner, then icon). Call off the main thread. */
    fun loadArtwork(packageName: String): Artwork? {
        val intent = launchIntent(packageName)
        try {
            if (intent != null) {
                pm.getActivityBanner(intent)?.let { return Artwork(it, isBanner = true) }
            }
            val info = pm.getApplicationInfo(packageName, 0)
            info.loadBanner(pm)?.let { return Artwork(it, isBanner = true) }
            return Artwork(info.loadIcon(pm), isBanner = false)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
    }

    /** Starts [intent]; returns false instead of crashing if the target is missing or protected. */
    fun start(intent: Intent): Boolean = try {
        appContext.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    data class Artwork(val drawable: Drawable, val isBanner: Boolean)
}
