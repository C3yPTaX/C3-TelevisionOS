package de.c3.tv.core

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.c3.tv.R

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/** Inter (OFL), bundled as three static Latin subsets; loaded once per process. */
object C3Fonts {
    private var cache: Array<Typeface>? = null

    private fun load(context: Context): Array<Typeface> = cache ?: try {
        val r = context.applicationContext.resources
        arrayOf(r.getFont(R.font.inter_medium), r.getFont(R.font.inter_semibold), r.getFont(R.font.inter_bold))
    } catch (e: Exception) {
        arrayOf(Typeface.DEFAULT, Typeface.DEFAULT_BOLD, Typeface.DEFAULT_BOLD)
    }.also { cache = it }

    fun medium(context: Context): Typeface = load(context)[0]
    fun semibold(context: Context): Typeface = load(context)[1]
    fun bold(context: Context): Typeface = load(context)[2]
}

/**
 * Focus motion shared by all focusable elements (design system: 180 ms in with a soft
 * overshoot, 140 ms out, 90 ms press). Only runs on input – nothing animates while idle.
 */
object Focus {
    private val inInterpolator = OvershootInterpolator(1.1f)
    private val outInterpolator = DecelerateInterpolator()

    fun attach(view: View, focusedScale: Float, lift: Float, onChange: (Boolean) -> Unit) {
        view.setOnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) focusedScale else 1f)
                .scaleY(if (hasFocus) focusedScale else 1f)
                .translationZ(if (hasFocus) lift else 0f)
                .setInterpolator(if (hasFocus) inInterpolator else outInterpolator)
                .setDuration(if (hasFocus) 180L else 140L)
                .start()
            onChange(hasFocus)
        }
        view.setOnKeyListener { v, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                val pressed = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
                val released = event.action == KeyEvent.ACTION_UP
                if (pressed || released) {
                    val s = if (pressed) 1f + (focusedScale - 1f) / 3f else focusedScale
                    v.animate().scaleX(s).scaleY(s).setInterpolator(outInterpolator)
                        .setDuration(if (pressed) 90L else 140L).start()
                }
            }
            false // never consume: the click still happens
        }
    }
}

/**
 * Focusable tile in two styles:
 * - [Style.APP]: 16:9 banner card (131 × 73 dp)
 * - [Style.DEVICE]: glass card with icon, status chip and name (150 × 84 dp)
 *
 * Focus: 1.12× with overshoot, white ring + top gloss (foreground drawable), lifted with an
 * accent-coloured shadow. No blur, no idle animation.
 */
class Tile(context: Context, val style: Style) : FrameLayout(context) {

    enum class Style { APP, DEVICE }

    /** Text for the large title area above the rows while this tile is focused. */
    var shelfCategory: String = ""
    var shelfTitle: String = ""
    var onFocused: ((Tile) -> Unit)? = null

    private var available = true

    private val image = ImageView(context).apply {
        visibility = View.GONE
    }
    private val icon = ImageView(context).apply {
        visibility = View.GONE
        imageTintList = context.getColorStateList(R.color.c3_text)
    }
    private val title = TextView(context).apply {
        setTextColor(context.getColor(R.color.c3_text))
        textSize = 13f
        typeface = C3Fonts.semibold(context)
        maxLines = 2
    }
    private val chip = TextView(context).apply {
        textSize = 9f
        typeface = C3Fonts.semibold(context)
        visibility = View.GONE
        val h = context.dp(7)
        val v = context.dp(3)
        setPadding(h, v, h, v)
    }

    init {
        val w = if (style == Style.APP) APP_W else DEVICE_W
        val h = if (style == Style.APP) APP_H else DEVICE_H
        layoutParams = LinearLayout.LayoutParams(context.dp(w), context.dp(h)).apply {
            marginEnd = context.dp(if (style == Style.APP) APP_GAP else DEVICE_GAP)
        }
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        clipToOutline = true
        setBackgroundResource(if (style == Style.APP) R.drawable.tile_app else R.drawable.tile_glass)
        foreground = context.getDrawable(if (style == Style.APP) R.drawable.focus_app else R.drawable.focus_device)
        outlineSpotShadowColor = context.getColor(R.color.c3_accent_glow)
        outlineAmbientShadowColor = context.getColor(R.color.c3_accent_glow)

        if (style == Style.APP) {
            title.gravity = Gravity.CENTER
            val p = context.dp(8)
            title.setPadding(p, p, p, p)
            addView(title, LayoutParams(MATCH, MATCH))
            addView(image, LayoutParams(MATCH, MATCH))
        } else {
            val p = context.dp(12)
            setPadding(p, p, p, p)
            addView(icon, LayoutParams(context.dp(20), context.dp(20), Gravity.TOP or Gravity.START))
            addView(chip, LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END))
            title.maxLines = 1
            addView(title, LayoutParams(MATCH, WRAP, Gravity.BOTTOM or Gravity.START))
        }

        Focus.attach(this, FOCUS_SCALE, context.dp(10).toFloat()) { focused ->
            alpha = alphaFor(focused)
            if (focused) onFocused?.invoke(this)
        }
        alpha = alphaFor(false)
    }

    fun setTitle(text: String, sub: String? = null) {
        title.text = text
        if (style == Style.DEVICE) setChip(sub, ok = false)
    }

    fun setSubtitle(sub: String?) = setChip(sub, ok = false)

    fun setChip(text: String?, ok: Boolean) {
        if (text.isNullOrEmpty()) {
            chip.visibility = View.GONE
            return
        }
        chip.text = text
        chip.setTextColor(context.getColor(if (ok) R.color.c3_ok_text else R.color.c3_text_secondary))
        chip.setBackgroundResource(if (ok) R.drawable.chip_ok_bg else R.drawable.chip_bg)
        chip.visibility = View.VISIBLE
    }

    fun setIcon(resId: Int) {
        icon.setImageResource(resId)
        icon.visibility = View.VISIBLE
    }

    /** Full-bleed banner, or a centred icon for apps without a TV banner. */
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
            val p = context.dp(16)
            image.setPadding(p, p, p, p)
        }
        image.visibility = View.VISIBLE
    }

    fun setAvailable(available: Boolean) {
        this.available = available
        alpha = alphaFor(isFocused)
    }

    val isAvailable: Boolean get() = available

    private fun alphaFor(focused: Boolean): Float = when {
        !available -> if (focused) 0.7f else 0.38f
        focused -> 1f
        else -> 0.92f
    }

    companion object {
        const val FOCUS_SCALE = 1.12f
        const val APP_W = 131
        const val APP_H = 73
        const val APP_GAP = 18
        const val DEVICE_W = 150
        const val DEVICE_H = 84
        const val DEVICE_GAP = 20
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

/** Glass capsule button of the system bar (white capsule with dark content when focused). */
class DockButton(context: Context, label: String, iconRes: Int) : LinearLayout(context) {

    var onFocused: ((DockButton) -> Unit)? = null
    val label: String = label

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        setBackgroundResource(R.drawable.dock_button)
        val h = context.dp(13)
        val v = context.dp(7)
        setPadding(h, v, h, v)

        val content = context.getColorStateList(R.color.dock_content)
        addView(ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = content
            isDuplicateParentStateEnabled = true
        }, LayoutParams(context.dp(13), context.dp(13)).apply { marginEnd = context.dp(6) })
        addView(TextView(context).apply {
            text = label
            textSize = 11f
            typeface = C3Fonts.medium(context)
            setTextColor(content)
            isDuplicateParentStateEnabled = true
        })

        Focus.attach(this, 1.06f, 0f) { focused -> if (focused) onFocused?.invoke(this) }
    }
}
