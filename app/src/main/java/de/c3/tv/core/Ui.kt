package de.c3.tv.core

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.c3.tv.R

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/**
 * A focusable tile with tvOS-like focus feedback (scale + lift).
 * Built in code instead of XML to keep inflation cost at zero.
 */
class Tile(context: Context, widthDp: Int, heightDp: Int) : FrameLayout(context) {

    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        visibility = View.GONE
        clipToOutline = true
    }

    private val title = TextView(context).apply {
        setTextColor(context.getColor(R.color.c3_text))
        textSize = 15f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        maxLines = 2
    }

    private val subtitle = TextView(context).apply {
        setTextColor(context.getColor(R.color.c3_text_secondary))
        textSize = 11f
        gravity = Gravity.CENTER
        maxLines = 2
        visibility = View.GONE
    }

    init {
        layoutParams = LinearLayout.LayoutParams(context.dp(widthDp), context.dp(heightDp)).apply {
            val m = context.dp(8)
            setMargins(m, m, m, m)
        }
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setBackgroundResource(R.drawable.tile_bg)
        clipToOutline = true

        val textBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = context.dp(8)
            setPadding(p, p, p, p)
            addView(title)
            addView(subtitle)
        }
        addView(textBox, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(image, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) FOCUS_SCALE else 1f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationZ(if (hasFocus) context.dp(8).toFloat() else 0f)
                .setDuration(ANIM_MS)
                .start()
        }
    }

    fun setTitle(text: String, sub: String? = null) {
        title.text = text
        subtitle.text = sub.orEmpty()
        subtitle.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setSubtitle(sub: String?) = setTitle(title.text.toString(), sub)

    /** Shows a full-bleed banner, or a centered icon for apps without a TV banner. */
    fun setArtwork(drawable: Drawable?, isBanner: Boolean) {
        if (drawable == null) {
            image.visibility = View.GONE
            return
        }
        image.setImageDrawable(drawable)
        if (isBanner) {
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setPadding(0, 0, 0, 0)
        } else {
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            val p = context.dp(18)
            image.setPadding(p, p, p, p)
        }
        image.visibility = View.VISIBLE
    }

    fun setAvailable(available: Boolean) {
        alpha = if (available) 1f else 0.4f
    }

    companion object {
        const val FOCUS_SCALE = 1.1f
        const val ANIM_MS = 120L
    }
}
