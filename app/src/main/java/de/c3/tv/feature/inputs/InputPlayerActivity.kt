package de.c3.tv.feature.inputs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import de.c3.tv.R
import de.c3.tv.core.dp

/**
 * Full-screen HDMI player using the public TV Input Framework (TvView).
 * Fire OS blocks third-party apps from Amazon's own input switcher (signature
 * permissions TUNE_INPUT / LAUNCH_INPUTS), but TvView sessions work.
 *
 * BACK returns to C3 Home; all other keys go to the input session first.
 * The session is released in onStop so Fire OS can use the input again.
 */
class InputPlayerActivity : Activity() {

    private lateinit var tvView: TvView
    private lateinit var overlay: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlay.visibility = View.GONE }

    private val inputId: String by lazy { intent.getStringExtra(EXTRA_INPUT_ID).orEmpty() }
    private val title: String by lazy { intent.getStringExtra(EXTRA_TITLE) ?: "HDMI" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tvView = TvView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setCallback(object : TvView.TvInputCallback() {
                override fun onVideoAvailable(inputId: String) {
                    showOverlay("$title  ·  Zurück-Taste: C3 Home", autoHide = true)
                }

                override fun onVideoUnavailable(inputId: String, reason: Int) {
                    showOverlay("$title: kein Signal\nGerät einschalten (PS5: PS-Taste am Controller)", autoHide = false)
                }

                override fun onConnectionFailed(inputId: String) {
                    showOverlay("$title konnte nicht geöffnet werden", autoHide = false)
                }

                override fun onDisconnected(inputId: String) {
                    showOverlay("$title wurde getrennt", autoHide = false)
                }
            })
        }

        overlay = TextView(this).apply {
            setTextColor(getColor(R.color.c3_text))
            textSize = 16f
            gravity = Gravity.CENTER
            setBackgroundColor(0xB3000000.toInt())
            val p = dp(16)
            setPadding(p * 2, p, p * 2, p)
            text = "$title wird geöffnet …"
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(tvView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
            ).apply { bottomMargin = dp(48) })
        }
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        if (inputId.isEmpty()) {
            finish()
            return
        }
        try {
            tvView.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
            tvView.requestFocus()
        } catch (e: Exception) {
            showOverlay("$title konnte nicht geöffnet werden (${e.javaClass.simpleName})", autoHide = false)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(hideOverlay)
        try {
            tvView.reset()
        } catch (e: Exception) {
            // Session already gone.
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showOverlay(message: String, autoHide: Boolean) {
        handler.removeCallbacks(hideOverlay)
        overlay.text = message
        overlay.visibility = View.VISIBLE
        if (autoHide) handler.postDelayed(hideOverlay, OVERLAY_MS)
    }

    companion object {
        private const val EXTRA_INPUT_ID = "de.c3.tv.extra.INPUT_ID"
        private const val EXTRA_TITLE = "de.c3.tv.extra.TITLE"
        private const val OVERLAY_MS = 2500L

        fun intent(context: Context, inputId: String, title: String): Intent =
            Intent(context, InputPlayerActivity::class.java)
                .putExtra(EXTRA_INPUT_ID, inputId)
                .putExtra(EXTRA_TITLE, title)
    }
}
