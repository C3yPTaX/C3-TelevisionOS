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
import de.c3.tv.core.C3Fonts
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
    private lateinit var overlayText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideOverlay = Runnable { overlayText.visibility = View.GONE }

    private val inputId: String by lazy { intent.getStringExtra(EXTRA_INPUT_ID).orEmpty() }
    private val inputTitle: String by lazy { intent.getStringExtra(EXTRA_TITLE) ?: "HDMI" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tvView = TvView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setCallback(object : TvView.TvInputCallback() {
                override fun onVideoAvailable(inputId: String) {
                    showOverlay("$inputTitle  ·  Zurück-Taste: C3 Home", autoHide = true)
                }

                override fun onVideoUnavailable(inputId: String, reason: Int) {
                    showOverlay("$inputTitle: kein Signal\nGerät einschalten (PS5: PS-Taste am Controller)", autoHide = false)
                }

                override fun onConnectionFailed(inputId: String) {
                    showOverlay("$inputTitle konnte nicht geöffnet werden", autoHide = false)
                }

                override fun onDisconnected(inputId: String) {
                    showOverlay("$inputTitle wurde getrennt", autoHide = false)
                }
            })
        }

        overlayText = TextView(this).apply {
            setTextColor(getColor(R.color.c3_text))
            textSize = 13f
            typeface = C3Fonts.medium(context)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.overlay_pill)
            setPadding(dp(20), dp(10), dp(20), dp(10))
            text = "$inputTitle wird geöffnet …"
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            addView(tvView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlayText, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
            ).apply { bottomMargin = dp(36) })
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
            showOverlay("$inputTitle konnte nicht geöffnet werden (${e.javaClass.simpleName})", autoHide = false)
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
        overlayText.text = message
        overlayText.visibility = View.VISIBLE
        if (autoHide) handler.postDelayed(hideOverlay, OVERLAY_MS)
    }

    companion object {
        private const val EXTRA_INPUT_ID = "de.c3.tv.extra.INPUT_ID"
        private const val EXTRA_TITLE = "de.c3.tv.extra.TITLE"
        private const val OVERLAY_MS = 2500L

        fun intent(context: Context, inputId: String, inputTitle: String): Intent =
            Intent(context, InputPlayerActivity::class.java)
                .putExtra(EXTRA_INPUT_ID, inputId)
                .putExtra(EXTRA_TITLE, inputTitle)
    }
}
