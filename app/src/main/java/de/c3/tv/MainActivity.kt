package de.c3.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import de.c3.tv.core.AppCatalog
import de.c3.tv.core.AppEntry
import de.c3.tv.core.AppLauncher
import de.c3.tv.core.SystemShortcuts
import de.c3.tv.core.Tile
import de.c3.tv.core.TvInputs
import de.c3.tv.core.dp
import de.c3.tv.feature.diagnostics.DiagnosticsActivity
import de.c3.tv.feature.inputs.InputPlayerActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * C3 Home (prototype P0). Single activity, views built in code, no background work
 * except one short-lived thread that loads app banners.
 */
class MainActivity : Activity() {

    private lateinit var launcher: AppLauncher
    private lateinit var tvInputs: TvInputs
    private val mainHandler = Handler(Looper.getMainLooper())
    private var io: ExecutorService? = null

    private val appTiles = mutableListOf<Pair<AppEntry, Tile>>()
    private var playStationTile: Tile? = null
    private var moonlightTile: Tile? = null
    private var devicesRow: LinearLayout? = null
    private val hdmiTiles = mutableMapOf<String, Tile>()

    /** Updated off the main thread by [refresh]; read on the main thread only. */
    private var playStationInputId: String = TvInputs.PS5_FALLBACK_INPUT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = AppLauncher(this)
        tvInputs = TvInputs(this)
        setContentView(buildLayout())
        appTiles.firstOrNull()?.second?.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onStop() {
        super.onStop()
        io?.shutdownNow()
        io = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // C3 Home is the start screen: Back stays here (Home/Fire TV Home tile leave it).
    }

    // ---------------------------------------------------------------- layout

    private fun buildLayout(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(28), dp(20), dp(28), dp(12))
        }

        column.addView(buildHeader())

        column.addView(sectionTitle("Apps"))
        column.addView(row(AppCatalog.streaming.map { entry ->
            Tile(this, APP_W, APP_H).also { tile ->
                tile.setTitle(entry.title)
                tile.setOnClickListener { launchApp(entry) }
                appTiles += entry to tile
            }
        }))

        column.addView(sectionTitle("Geräte"))
        val ps = Tile(this, APP_W, APP_H).apply {
            setTitle("PlayStation 5", "HDMI 2")
            setOnClickListener { onPlayStation() }
        }
        val pc = Tile(this, APP_W, APP_H).apply {
            setTitle("Gaming-PC", "Moonlight")
            setOnClickListener { launchApp(AppCatalog.moonlight, hint = "Moonlight aus dem Amazon Appstore installieren") }
        }
        playStationTile = ps
        moonlightTile = pc
        val devices = row(listOf(ps, pc))
        devicesRow = (devices as HorizontalScrollView).getChildAt(0) as LinearLayout
        column.addView(devices)

        column.addView(sectionTitle("System"))
        column.addView(row(SystemShortcuts.all.map { sc ->
            Tile(this, APP_W, APP_H).apply {
                setTitle(sc.title, sc.subtitle)
                setOnClickListener {
                    if (!launcher.start(sc.intent)) toast("${sc.title} ist auf diesem Gerät nicht erreichbar")
                }
            }
        }))

        return FrameLayout(this).apply {
            clipChildren = false
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), dp(4))
        addView(TextView(context).apply {
            text = "C3"
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(getColor(R.color.c3_text))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextClock(context).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 20f
            setTextColor(getColor(R.color.c3_text))
        })
    }

    private fun sectionTitle(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(getColor(R.color.c3_text_secondary))
        setPadding(dp(8), dp(10), 0, 0)
    }

    private fun row(tiles: List<View>): View {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            tiles.forEach { addView(it) }
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            addView(inner)
        }
    }

    // ---------------------------------------------------------------- state

    /**
     * Re-evaluates installed apps and TV inputs on a short-lived background thread,
     * so the first frame is never blocked by PackageManager or TV-input binder calls.
     */
    private fun refresh() {
        val executor = Executors.newSingleThreadExecutor().also { io = it }
        executor.execute {
            val apps = appTiles.map { (entry, tile) -> tile to launcher.resolve(entry) }
            val moonlightInstalled = launcher.resolve(AppCatalog.moonlight) != null
            val inputs = tvInputs.list()
            val ps = TvInputs.findPlayStation(inputs)
            val psPort = ps?.parentId ?: TvInputs.PS5_FALLBACK_INPUT
            val otherPorts = TvInputs.hdmiPorts(inputs).filter { it.id != psPort }

            mainHandler.post {
                if (isDestroyed) return@post
                apps.forEach { (tile, pkg) -> tile.setAvailable(pkg != null) }
                moonlightTile?.setAvailable(moonlightInstalled)
                playStationInputId = ps?.id ?: TvInputs.PS5_FALLBACK_INPUT
                otherPorts.forEach { addHdmiTile(it) }
            }

            apps.forEach { (tile, pkg) ->
                if (Thread.currentThread().isInterrupted) return@execute
                val art = pkg?.let { launcher.loadArtwork(it) }
                mainHandler.post { tile.setArtwork(art?.drawable, art?.isBanner == true) }
            }
        }
        executor.shutdown()
    }

    private fun addHdmiTile(input: TvInputs.Input) {
        if (hdmiTiles.containsKey(input.id)) return
        val label = input.label.ifEmpty { "HDMI" }
        val tile = Tile(this, APP_W, APP_H).apply {
            setTitle(label, "Eingang")
            setOnClickListener { startActivity(InputPlayerActivity.intent(this@MainActivity, input.id, label)) }
        }
        hdmiTiles[input.id] = tile
        devicesRow?.addView(tile)
    }

    // ---------------------------------------------------------------- actions

    private fun launchApp(entry: AppEntry, hint: String = "Im Amazon Appstore installieren") {
        val pkg = launcher.resolve(entry)
        val intent = pkg?.let { launcher.launchIntent(it) }
        if (intent == null || !launcher.start(intent)) {
            toast("${entry.title} ist nicht installiert – $hint")
        }
    }

    /**
     * Amazon's input switcher is signature-protected (TUNE_INPUT / LAUNCH_INPUTS), so C3
     * shows the PS5 itself via TvView. HDMI-CEC One Touch Play keeps working independently.
     */
    private fun onPlayStation() {
        startActivity(InputPlayerActivity.intent(this, playStationInputId, "PlayStation 5"))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val APP_W = 132
        private const val APP_H = 74
    }
}
