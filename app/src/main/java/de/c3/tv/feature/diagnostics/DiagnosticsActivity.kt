package de.c3.tv.feature.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import de.c3.tv.R
import de.c3.tv.core.TvInputs
import de.c3.tv.core.dp

/**
 * Hidden diagnostics screen (MENU key on C3 Home). Answers the open Phase 1 questions
 * on the real device:
 *  - Which TV inputs can a third-party app see, with which state?
 *  - Can C3 show an HDMI input itself via TvView (public TIF API)?
 *  - How much memory does C3 use?
 */
class DiagnosticsActivity : Activity() {

    private lateinit var tvView: TvView
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val inputs = TvInputs(this).list()

        log = TextView(this).apply {
            textSize = 12f
            setTextColor(getColor(R.color.c3_text))
            setLineSpacing(0f, 1.15f)
        }

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inputs.filter { it.isPassthrough }.forEach { input ->
            buttons.addView(button("Vorschau: ${input.label.ifEmpty { input.id }}") { tune(input.id) })
        }
        buttons.addView(button("Vorschau stoppen") { stopPreview() })
        buttons.addView(button("Aktualisieren") { render(TvInputs(this).list()) })

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buttons)
            addView(log)
        }

        tvView = TvView(this).apply {
            setCallback(object : TvView.TvInputCallback() {
                override fun onConnectionFailed(inputId: String) = append("TvView: Verbindung fehlgeschlagen ($inputId)")
                override fun onDisconnected(inputId: String) = append("TvView: getrennt ($inputId)")
                override fun onVideoAvailable(inputId: String) = append("TvView: ✅ Bild verfügbar ($inputId)")
                override fun onVideoUnavailable(inputId: String, reason: Int) =
                    append("TvView: kein Bild ($inputId), Grund=$reason")
            })
        }
        val preview = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(tvView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val p = dp(24)
            setPadding(p, p, p, p)
            addView(ScrollView(context).apply { addView(left) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(View(context), LinearLayout.LayoutParams(dp(24), 1))
            addView(preview, LinearLayout.LayoutParams(dp(448), dp(252)))
        }
        setContentView(root)
        render(inputs)
        buttons.getChildAt(0)?.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        stopPreview()
    }

    private fun render(inputs: List<TvInputs.Input>) {
        val am = getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val own = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }

        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("Gerät: ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("RAM frei: ${mem.availMem / MB} MB von ${mem.totalMem / MB} MB · lowMemory=${mem.lowMemory}")
        sb.appendLine("C3 Speicher (PSS): ${own.totalPss / 1024} MB")
        sb.appendLine()
        sb.appendLine("Sichtbare TV-Inputs: ${inputs.size}")
        inputs.forEach {
            sb.appendLine("• ${it.label.ifEmpty { "(ohne Name)" }} – ${it.typeName}, ${it.stateName}" +
                (if (it.isPassthrough) ", passthrough" else "") +
                (it.parentId?.let { p -> ", parent=$p" } ?: ""))
            sb.appendLine("   ${it.id}")
        }
        log.text = sb.toString()
    }

    private fun tune(inputId: String) {
        append("TvView: tune $inputId …")
        try {
            tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
        } catch (e: Exception) {
            append("TvView: Fehler ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun stopPreview() {
        try {
            tvView.reset()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun append(line: String) {
        log.append("\n$line")
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(getColor(R.color.c3_text))
        setBackgroundResource(R.drawable.button_bg)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
            bottomMargin = dp(6)
        }
    }

    companion object {
        private const val MB = 1024L * 1024L
    }
}
