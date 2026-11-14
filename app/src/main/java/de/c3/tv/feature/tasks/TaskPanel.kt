package de.c3.tv.feature.tasks

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import de.c3.tv.R
import de.c3.tv.core.C3Fonts
import de.c3.tv.core.DockButton
import de.c3.tv.core.RecentApps
import de.c3.tv.core.Tile
import de.c3.tv.core.dp

/**
 * "Laufende Apps" over the home screen (MENU key): banners of the recently used apps,
 * OK closes one, plus "Alle schließen" and the diagnostics screen. Back or MENU closes the panel
 * (handled by MainActivity, which also blocks the focus behind it).
 */
class TaskPanel(
    context: Context,
    entries: List<RecentApps.Entry>,
    hasAccess: Boolean,
    artwork: (String) -> Pair<Drawable?, Boolean>?,
    private val onCloseApp: (RecentApps.Entry) -> Unit,
    onCloseAll: (List<RecentApps.Entry>) -> Unit,
    onDiagnostics: () -> Unit,
) : FrameLayout(context) {

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
        val v = context.dp(8)
        setPadding(context.dp(10), v, context.dp(10), v)
    }
    private val subtitle = text(11f, R.color.c3_text_secondary)
    private val info = text(11f, R.color.c3_text_secondary)
    private val remaining = entries.toMutableList()
    private val closeAll: DockButton
    private val diagnostics: DockButton
    val firstFocus: View

    init {
        setBackgroundColor(0x99000000.toInt())
        isClickable = true // swallow touches behind the panel

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.panel_bg)
            val p = context.dp(18)
            setPadding(p, context.dp(16), p, p)
            clipChildren = false
            clipToPadding = false
        }
        card.addView(text(17f, R.color.c3_text, bold = true).apply { text = "Laufende Apps" })
        card.addView(subtitle, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(2) })

        entries.forEach { entry -> row.addView(tile(entry, artwork(entry.pkg))) }
        card.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP).apply {
            topMargin = context.dp(6)
            leftMargin = -context.dp(10)
            rightMargin = -context.dp(10)
        })
        card.addView(info, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(2) })

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        closeAll = DockButton(context, "Alle schließen", 0).apply {
            setOnClickListener { onCloseAll(remaining.toList()) }
        }
        diagnostics = DockButton(context, "Diagnose", R.drawable.ic_info).apply {
            setOnClickListener { onDiagnostics() }
        }
        buttons.addView(closeAll, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = context.dp(6) })
        buttons.addView(diagnostics, LinearLayout.LayoutParams(WRAP, WRAP))
        card.addView(buttons, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(12) })

        addView(card, LayoutParams(context.dp(CARD_W), WRAP, Gravity.CENTER))

        if (!hasAccess) {
            subtitle.text = "Für die Liste im C3 Updater einmal „TV aktualisieren“ klicken."
        }
        updateState()
        firstFocus = row.getChildAt(0) ?: diagnostics
    }

    private fun tile(entry: RecentApps.Entry, art: Pair<Drawable?, Boolean>?): Tile =
        Tile(context, Tile.Style.APP).apply {
            key = entry.pkg
            setTitle(entry.label)
            art?.let { (drawable, isBanner) -> setArtwork(drawable, isBanner) }
            onFocused = { info.text = entry.label + " · " + ago(entry.lastUsed) }
            setOnClickListener { close(this, entry) }
        }

    private fun close(tile: Tile, entry: RecentApps.Entry) {
        onCloseApp(entry)
        remaining.remove(entry)
        val index = row.indexOfChild(tile)
        tile.isFocusable = false
        tile.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(160L).withEndAction {
            row.removeView(tile)
            updateState()
            val next = row.getChildAt(index.coerceAtMost(row.childCount - 1))
            (next ?: diagnostics).requestFocus()
        }.start()
    }

    private fun updateState() {
        val empty = remaining.isEmpty()
        closeAll.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            info.text = ""
            if (subtitle.text.isNullOrEmpty() || subtitle.text.startsWith("OK")) {
                subtitle.text = "Keine Apps im Hintergrund."
            }
        } else {
            subtitle.text = "OK schließt eine App."
        }
    }

    private fun text(size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        textSize = size
        typeface = if (bold) C3Fonts.semibold(context) else C3Fonts.medium(context)
        setTextColor(context.getColor(color))
        maxLines = 1
    }

    companion object {
        private const val CARD_W = 720
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun ago(time: Long): String {
            val minutes = ((System.currentTimeMillis() - time) / 60_000L).coerceAtLeast(0)
            return when {
                minutes < 1 -> "gerade benutzt"
                minutes < 60 -> "vor $minutes Min."
                else -> "vor ${minutes / 60} Std."
            }
        }
    }
}
