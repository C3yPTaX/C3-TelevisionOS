package de.c3.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import de.c3.tv.core.AppCatalog
import de.c3.tv.core.AppEntry
import de.c3.tv.core.AppLauncher
import de.c3.tv.core.C3Fonts
import de.c3.tv.core.DockButton
import de.c3.tv.core.PcStore
import de.c3.tv.core.SystemShortcuts
import de.c3.tv.core.Tile
import de.c3.tv.core.TvInputs
import de.c3.tv.core.dp
import de.c3.tv.feature.diagnostics.DiagnosticsActivity
import de.c3.tv.feature.inputs.InputPlayerActivity
import de.c3.tv.redirect.HomeRedirectService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * C3 Television OS home screen. Single activity, views built in code (design system:
 * docs/04-design-proposal.md), no background work except one short-lived thread that
 * loads app banners and TV inputs.
 */
class MainActivity : Activity() {

    private lateinit var launcher: AppLauncher
    private lateinit var tvInputs: TvInputs
    private val mainHandler = Handler(Looper.getMainLooper())
    private var io: ExecutorService? = null

    private val appTiles = mutableListOf<Pair<AppEntry, Tile>>()
    private var playStationTile: Tile? = null
    private lateinit var pcStore: PcStore
    private val pcTiles = mutableListOf<Tile>()
    private var moonlightInstalled = true
    private var devicesRow: LinearLayout? = null
    private val hdmiTiles = mutableMapOf<String, Tile>()

    private lateinit var shelfCategory: TextView
    private lateinit var shelfTitle: TextView

    /** Updated off the main thread by [refresh]; read on the main thread only. */
    private var playStationInputId: String = TvInputs.PS5_FALLBACK_INPUT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = AppLauncher(this)
        tvInputs = TvInputs(this)
        pcStore = PcStore(this)
        setContentView(buildLayout())
        handleConfigIntent(intent)
        rebuildPcTiles()
        appTiles.firstOrNull()?.second?.let { first ->
            first.requestFocus()
            showShelf(first.shelfCategory, first.shelfTitle)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleConfigIntent(intent)) rebuildPcTiles()
    }

    /** PC list pushed by the C3 Updater via adb (see PcStore). */
    private fun handleConfigIntent(intent: Intent?): Boolean {
        if (intent?.action != PcStore.ACTION_SET_PCS) return false
        val pcs = pcStore.save(intent.getStringExtra(PcStore.EXTRA_PCS).orEmpty())
        toast(if (pcs.isEmpty()) "C3 Desktop: keine PCs gespeichert" else "C3 Desktop: " + pcs.joinToString { it.name })
        return true
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
            setPadding(dp(40), dp(30), dp(40), dp(30))
        }

        column.addView(buildHeader())
        column.addView(buildShelf())

        column.addView(sectionTitle("Apps"))
        column.addView(row(AppCatalog.streaming.map { entry ->
            Tile(this, Tile.Style.APP).also { tile ->
                tile.setTitle(entry.title)
                tile.shelfCategory = "Streaming"
                tile.shelfTitle = entry.title
                tile.onFocused = { showShelf(it.shelfCategory, it.shelfTitle) }
                tile.setOnClickListener { launchApp(entry) }
                appTiles += entry to tile
            }
        }))

        column.addView(sectionTitle("Geräte"))
        val ps = deviceTile("PlayStation 5", R.drawable.ic_gamepad, "Konsole · HDMI 2").apply {
            setChip("HDMI 2", ok = true)
            setOnClickListener { onPlayStation() }
        }
        playStationTile = ps
        val devices = row(listOf(ps))
        devicesRow = (devices as HorizontalScrollView).getChildAt(0) as LinearLayout
        column.addView(devices)

        return FrameLayout(this).apply {
            clipChildren = false
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(buildDock(), FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply { bottomMargin = dp(30) })
            addView(TextView(context).apply {
                text = "by Nico Lukowski"
                textSize = 8f
                typeface = C3Fonts.medium(context)
                setTextColor(getColor(R.color.c3_text_tertiary))
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END,
            ).apply {
                rightMargin = dp(40)
                bottomMargin = dp(37)
            })
        }
    }

    private fun buildHeader(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_c3) },
            LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(7) })
        addView(TextView(context).apply {
            text = getString(R.string.app_name)
            textSize = 13f
            typeface = C3Fonts.semibold(context)
            setTextColor(getColor(R.color.c3_text))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextClock(context).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 17f
            typeface = C3Fonts.medium(context)
            setTextColor(getColor(R.color.c3_text))
        })
    }

    /** Large title of the focused element ("top shelf"); cross-fades on focus change only. */
    private fun buildShelf(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(112))
        shelfCategory = TextView(context).apply {
            textSize = 11f
            typeface = C3Fonts.medium(context)
            setTextColor(getColor(R.color.c3_text_secondary))
        }
        shelfTitle = TextView(context).apply {
            textSize = 38f
            typeface = C3Fonts.bold(context)
            letterSpacing = -0.02f
            includeFontPadding = false
            maxLines = 1
            setTextColor(getColor(R.color.c3_text))
        }
        addView(shelfCategory)
        addView(shelfTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
            bottomMargin = dp(6)
        })
    }

    private fun showShelf(category: String, title: String) {
        if (shelfTitle.text == title && shelfCategory.text == category) return
        shelfTitle.animate().cancel()
        shelfTitle.animate().alpha(0f).setDuration(70L).withEndAction {
            shelfCategory.text = category
            shelfTitle.text = title
            shelfTitle.animate().alpha(1f).setDuration(90L).start()
        }.start()
    }

    private fun buildDock(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
        setBackgroundResource(R.drawable.dock_bg)
        val p = dp(5)
        setPadding(p, p, p, p)
        SystemShortcuts.all.forEach { sc ->
            addView(DockButton(context, sc.title, sc.iconRes).apply {
                onFocused = { showShelf("System", sc.subtitle) }
                setOnClickListener {
                    if (sc.iconRes == R.drawable.ic_home) HomeRedirectService.requestEscape()
                    if (!launcher.start(sc.intent)) toast("${sc.title} ist auf diesem Gerät nicht erreichbar")
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(2)
            })
        }
    }

    private fun deviceTile(name: String, iconRes: Int, category: String): Tile =
        Tile(this, Tile.Style.DEVICE).apply {
            setTitle(name)
            setIcon(iconRes)
            shelfCategory = category
            shelfTitle = name
            onFocused = { showShelf(it.shelfCategory, it.shelfTitle) }
        }

    private fun sectionTitle(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = C3Fonts.semibold(context)
        setTextColor(getColor(R.color.c3_text_secondary))
        setPadding(0, dp(12), 0, dp(8))
    }

    private fun row(tiles: List<View>): View {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            val v = dp(6)
            setPadding(0, v, dp(12), v)
            tiles.forEach { addView(it) }
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_NEVER
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
                apps.forEach { (tile, pkg) ->
                tile.setAvailable(pkg != null)
                tile.shelfCategory = if (pkg != null) "Streaming" else "Nicht installiert"
                if (tile.isFocused) showShelf(tile.shelfCategory, tile.shelfTitle)
            }
                this.moonlightInstalled = moonlightInstalled
                pcTiles.forEach { it.setAvailable(moonlightInstalled) }
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

    /** PC tiles sit right after the PlayStation tile; without configured PCs one Moonlight tile. */
    private fun rebuildPcTiles() {
        val row = devicesRow ?: return
        pcTiles.forEach { row.removeView(it) }
        pcTiles.clear()

        val pcs = pcStore.load()
        if (pcs.isEmpty()) {
            pcTiles += deviceTile("PC streamen", R.drawable.ic_monitor, "C3 Desktop").apply {
                setChip("Moonlight", ok = false)
                setOnClickListener { launchApp(AppCatalog.moonlight, hint = "Moonlight aus dem Amazon Appstore installieren") }
            }
        } else {
            pcs.forEach { pc ->
                pcTiles += deviceTile(pc.name, R.drawable.ic_monitor, "C3 Desktop · ${pc.appName}").apply {
                    setChip(pc.appName, ok = false)
                    setOnClickListener {
                        if (!launcher.start(PcStore.streamIntent(pc))) {
                            toast("Moonlight fehlt oder ist veraltet – im Amazon Appstore installieren/aktualisieren")
                        }
                    }
                }
            }
        }
        pcTiles.forEachIndexed { i, tile ->
            tile.setAvailable(moonlightInstalled)
            row.addView(tile, 1 + i)
        }
    }

    private fun addHdmiTile(input: TvInputs.Input) {
        if (hdmiTiles.containsKey(input.id)) return
        val label = input.label.ifEmpty { "HDMI" }
        val tile = deviceTile(label, R.drawable.ic_hdmi, "Eingang").apply {
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

}
