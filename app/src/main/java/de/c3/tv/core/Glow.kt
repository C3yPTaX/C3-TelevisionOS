package de.c3.tv.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

/**
 * Accent colour for the soft light behind the title area, taken from an app banner.
 * Colourful pixels count more than grey/black ones, so a red logo on black gives red.
 * Returns null for colourless artwork (the light then fades out).
 */
object Glow {

    private const val W = 24
    private const val H = 14

    /** Call off the main thread. */
    fun fromDrawable(drawable: Drawable): Int? = try {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        // Draw a copy so the bounds of the tile's drawable stay untouched.
        val d = drawable.constantState?.newDrawable()?.mutate() ?: drawable
        d.setBounds(0, 0, W, H)
        d.draw(Canvas(bitmap))
        val pixels = IntArray(W * H)
        bitmap.getPixels(pixels, 0, W, 0, 0, W, H)
        bitmap.recycle()
        dominant(pixels)
    } catch (e: Exception) {
        null
    }

    private fun dominant(pixels: IntArray): Int? {
        val hsv = FloatArray(3)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var weight = 0.0
        for (p in pixels) {
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val w = (hsv[1] * hsv[2]).toDouble().let { it * it }
            if (w < 0.02) continue
            r += Color.red(p) * w
            g += Color.green(p) * w
            b += Color.blue(p) * w
            weight += w
        }
        if (weight < pixels.size * 0.01) return null
        Color.colorToHSV(Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt()), hsv)
        hsv[1] = hsv[1].coerceIn(0.45f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.6f, 0.9f)
        return Color.HSVToColor(hsv)
    }
}
