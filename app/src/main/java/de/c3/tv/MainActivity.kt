package de.c3.tv

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
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
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import de.c3.tv.core.AppCatalog
import de.c3.tv.core.AppEntry
import de.c3.tv.core.AppLauncher
import de.c3.tv.core.C3Fonts
import de.c3.tv.core.DockButton
import de.c3.tv.core.Glow
import de.c3.tv.core.HomeLayout
import de.c3.tv.core.OptionsPanel
import de.c3.tv.core.PcStatus
import de.c3.tv.core.PcStore
import de.c3.tv.core.SystemShortcuts
import de.c3.tv.core.Tile
import de.c3.tv.core.TvInputs
import de.c3.tv.core.WebStore
import de.c3.tv.core.dp
import de.c3.tv.core.RecentApps
import de.c3.tv.feature.diagnostics.DiagnosticsActivity
import de.c3.tv.feature.tasks.TaskPanel
import de.c3.tv.feature.inputs.InputPlayerActivity
import de.c3.tv.redirect.HomeRedirectService
import de.c3.tv.remote.LinkReceiver
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * C3 Television OS home screen. Single activity, views built in code (design system:
 * docs/04-design-proposal.md), no background work except one short-lived thread that
 * loads app banners and TV inputs, and the PC status check while the screen is visible.
 *
 * Rows: Favoriten (own order, catalog apps that are missing at the end), Geräte, Web, Privat.
 * Long press OK on an app opens its options (move, hide, show hidden again).
 * MENU opens "Laufende Apps" (close recently used apps; diagnostics from there).
 */
class MainActivity : Activity() {

    private lateinit var launcher: AppLauncher
    private lateinit var tvInputs: TvInputs
    private lateinit var home: HomeLayout
    private val mainHandler = Handler(Looper.getMainLooper())
    private var io: ExecutorService? = null

    private var playStationTile: Tile? = null
    private lateinit var pcStore: PcStore
    private lateinit var pcStatus: PcStatus
    private val pcTiles = mutableListOf<Tile>()
    private var pcStates: Map<String, PcStatus.State> = emptyMap()
    private var moonlightInstalled = true
    private var devicesRow: LinearLayout? = null
    private val hdmiTiles = mutableMapOf<String, Tile>()
    private var started = false

    private lateinit var webStore: WebStore
    private var browserTile: Tile? = null
    private var webTitle: View? = null
    private var webScroll: View? = null
    private var privateTitle: View? = null
    private var privateRow: LinearLayout? = null
    private var privateScroll: View? = null
    private lateinit var shelfCategory: TextView
    private lateinit var shelfTitle: TextView

    // Favourites row, rebuilt only when its content changes.
    private lateinit var rowsContainer: LinearLayout
    private lateinit var favouritesRow: LinearLayout
    /** Installed favourite/catalog packages; null until the first [refresh] of this process. */
    private var installedPkgs: Set<String>? = null
    private var catalogResolved: Map<String, String?> = emptyMap()
    private var appsSignature = ""
    private val artwork = ConcurrentHashMap<String, Art>()

    // Light behind the title area (two layers, alpha cross-fade only).
    private lateinit var glowFront: View
    private lateinit var glowBack: View
    private var glowColor: Int? = null

    private lateinit var content: View
    private lateinit var dock: View
    private lateinit var recentApps: RecentApps
    /** Options panel or "Laufende Apps" over the home screen; focus behind it is blocked. */
    private var overlay: View? = null
    /** Key pressed down while the overlay was open; its release closes the overlay. */
    private var overlayCloseKey: Int? = null
    /** Gets the focus back when the overlay closes (tile or system bar button). */
    private var optionsAnchor: View? = null
    private var movingTile: Tile? = null

    /** Focus to restore after a restart; dropped as soon as the user navigates. */
    private var pendingFocusKey: String? = null

    /** Updated off the main thread by [refresh]; read on the main thread only. */
    private var playStationInputId: String = TvInputs.PS5_FALLBACK_INPUT

    private class Art(val artwork: AppLauncher.Artwork?, val glow: Int?)

    private class AppsModel(
        /** Visible favourites (without hidden ones). */
        val favourites: List<HomeLayout.Favourite>,
        /** Stored order including hidden favourites, so "einblenden" brings them back in place. */
        val stored: List<HomeLayout.Favourite>,
        val placeholders: List<AppEntry>,
        val hiddenCount: Int,
        val seen: Set<String>,
    ) {
        val signature: String = buildString {
            favourites.forEach { append(it.pkg).append('=').append(it.label).append(';') }
            append('|')
            placeholders.forEach { append(it.title).append(';') }
            append('|').append(hiddenCount)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launcher = AppLauncher(this)
        tvInputs = TvInputs(this)
        home = HomeLayout(this)
        pcStore = PcStore(this)
        pcStatus = PcStatus(this) { states -> onPcStates(states) }
        webStore = WebStore(this)
        recentApps = RecentApps(this)
        setContentView(buildLayout())
        handleConfigIntent(intent)
        pendingFocusKey = home.lastFocus
        initFirstRun()
        applyApps(computeModel())
        rebuildPcTiles()
        rebuildPrivateTiles()
        if (!restorePendingFocus()) focusFirst()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleConfigIntent(intent)) {
            rebuildPcTiles()
            rebuildPrivateTiles()
            if (started) pcStatus.start(pcStore.load().map { it.uuid })
        }
    }

    /** PC list pushed by the C3 Updater via adb (see PcStore). */
    private fun handleConfigIntent(intent: Intent?): Boolean {
        if (intent?.action == WebStore.ACTION_SET_PRIVATE) {
            val enabled = intent.getBooleanExtra(WebStore.EXTRA_ENABLED, false)
            val sites = webStore.apply(
                enabled,
                intent.getStringExtra(WebStore.EXTRA_SITES).orEmpty(),
                intent.getStringExtra(WebStore.EXTRA_TOKEN),
            )
            LinkReceiver.update(this)
            toast(if (enabled) "Privat: ${sites.size} Seiten" else "Privat ausgeschaltet")
            return true
        }
        if (intent?.action != PcStore.ACTION_SET_PCS) return false
        val pcs = pcStore.save(intent.getStringExtra(PcStore.EXTRA_PCS).orEmpty())
        toast(if (pcs.isEmpty()) "C3 Desktop: keine PCs gespeichert" else "C3 Desktop: " + pcs.joinToString { it.name })
        return true
    }

    override fun onStart() {
        super.onStart()
        started = true
        refresh()
        pcStatus.start(pcStore.load().map { it.uuid })
    }

    override fun onStop() {
        super.onStop()
        started = false
        if (movingTile != null) endMove()
        closeOverlay()
        (currentFocus as? Tile)?.key?.takeIf { it.isNotEmpty() }?.let { home.lastFocus = it }
        pcStatus.stop()
        io?.shutdownNow()
        io = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && code in NAV_KEYS) pendingFocusKey = null

        val moving = movingTile
        if (moving != null && code in MOVE_KEYS) {
            if (event.action == KeyEvent.ACTION_DOWN) when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> moveFavourite(moving, -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveFavourite(moving, +1)
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {}
                else -> endMove()
            }
            return true // keep the tile in place while moving; volume etc. pass through
        }
        if (overlay != null && (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_MENU)) {
            // Close on release, but only for a press that started while the overlay was open:
            // releasing the MENU press that opened "Laufende Apps" must not close it again.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) overlayCloseKey = code
            if (event.action == KeyEvent.ACTION_UP && overlayCloseKey == code) closeOverlay()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event.repeatCount == 0) {
            showTasks()
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
        content = column

        column.addView(buildHeader())
        column.addView(buildShelf())

        // Rows scroll vertically (focus-driven) and end above the dock.
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), 0, dp(12), dp(14))
        }
        rowsContainer = rows
        // Rows must not paint over the title area or the dock when scrolled. A view's content is
        // only clipped to its bounds if its *parent* clips children – `column` does not (focus
        // scale), so the scroll view sits in its own clipping frame. Padding inside the rows
        // leaves room for the focus scale.
        val rowScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isSmoothScrollingEnabled = true
            addView(rows)
        }
        val rowClip = FrameLayout(this).apply {
            clipChildren = true
            addView(rowScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // 960×540 dp: header + shelf + Favoriten/Geräte fit without scrolling; ends above the dock.
        column.addView(rowClip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            leftMargin = -dp(12)
            rightMargin = -dp(12)
            bottomMargin = dp(44)
        })

        rows.addView(sectionTitle("Favoriten"))
        val favs = row(emptyList())
        favouritesRow = (favs as HorizontalScrollView).getChildAt(0) as LinearLayout
        rows.addView(favs)

        rows.addView(sectionTitle("Geräte"))
        val ps = deviceTile("PlayStation 5", R.drawable.ic_gamepad, "Konsole · HDMI 2").apply {
            key = "ps5"
            glowColor = PLAYSTATION_BLUE
            setChip("HDMI 2", ok = true)
            setOnClickListener { onPlayStation() }
        }
        playStationTile = ps
        val devices = row(listOf(ps))
        devicesRow = (devices as HorizontalScrollView).getChildAt(0) as LinearLayout
        rows.addView(devices)

        val wTitle = sectionTitle("Web")
        webTitle = wTitle
        rows.addView(wTitle)
        val browser = deviceTile("Browser", R.drawable.ic_globe, "Web · Silk").apply {
            key = "web"
            setChip("Silk", ok = false)
            setOnClickListener { launchApp(WebStore.silk, hint = "Silk Browser im Amazon Appstore installieren") }
        }
        browserTile = browser
        val wRow = row(listOf(browser))
        webScroll = wRow
        rows.addView(wRow)

        val pTitle = sectionTitle("Privat")
        val pRow = row(emptyList())
        privateTitle = pTitle
        privateScroll = pRow
        privateRow = (pRow as HorizontalScrollView).getChildAt(0) as LinearLayout
        rows.addView(pTitle)
        rows.addView(pRow)


        glowFront = glowLayer()
        glowBack = glowLayer()
        dock = buildDock()

        return FrameLayout(this).apply {
            clipChildren = false
            addView(glowBack, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(glowFront, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(dock, FrameLayout.LayoutParams(
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
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66))
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
            topMargin = dp(3)
            bottomMargin = dp(4)
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

    private fun onTileFocused(tile: Tile) {
        showShelf(tile.shelfCategory, tile.shelfTitle)
        showGlow(tile.glowColor)
    }

    /** Full-screen radial light from the upper left; drawn without an offscreen layer. */
    private fun glowLayer(): View = object : View(this) {
        override fun hasOverlappingRendering() = false
    }.apply {
        alpha = 0f
        isFocusable = false
        background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(620).toFloat()
            setGradientCenter(0.2f, 0f)
            colors = intArrayOf(0, 0)
        }
    }

    private fun showGlow(color: Int?) {
        if (color == glowColor) return
        glowColor = color
        val incoming = glowBack
        val outgoing = glowFront
        outgoing.animate().cancel()
        incoming.animate().cancel()
        outgoing.animate().alpha(0f).setDuration(GLOW_FADE_MS).start()
        if (color != null) {
            (incoming.background as GradientDrawable).colors = intArrayOf(color, color and 0x00FFFFFF)
            incoming.animate().alpha(GLOW_ALPHA).setDuration(GLOW_FADE_MS).start()
        }
        glowFront = incoming
        glowBack = outgoing
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
                onFocused = {
                    showShelf("System", sc.subtitle)
                    showGlow(null)
                }
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
            onFocused = { onTileFocused(it) }
        }

    private fun sectionTitle(text: String): View = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = C3Fonts.semibold(context)
        setTextColor(getColor(R.color.c3_text_secondary))
        setPadding(0, dp(4), 0, dp(3))
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

    // ---------------------------------------------------------------- apps

    /** First start: the installed catalog apps become the favourites (6 quick lookups, once). */
    private fun initFirstRun() {
        if (home.isInitialized) return
        val favs = AppCatalog.streaming.mapNotNull { entry ->
            launcher.resolve(entry)?.let { HomeLayout.Favourite(it, entry.title) }
        }
        home.saveFavourites(favs)
        home.setSeenCatalog(AppCatalog.streaming.filter { launcher.resolve(it) != null }.map { it.title }.toSet())
    }

    /**
     * Arrangement from the stored layout and the latest install state. Before the first [refresh]
     * of this process ([installedPkgs] == null) only stored data is used, so the first frame
     * needs no PackageManager call.
     */
    private fun computeModel(): AppsModel {
        val hidden = home.hidden()
        val seen = home.seenCatalog().toMutableSet()
        val installed = installedPkgs

        val stored = home.favourites()
            .filter { installed == null || it.pkg in installed }
            .toMutableList()
        if (installed != null) {
            AppCatalog.streaming.forEach { entry ->
                val pkg = catalogResolved[entry.title] ?: return@forEach
                if (!seen.add(entry.title)) return@forEach
                if (stored.none { it.pkg == pkg }) stored += HomeLayout.Favourite(pkg, entry.title)
            }
        }
        val favs = stored.filter { appKey(it.pkg) !in hidden }
        val placeholders = AppCatalog.streaming.filter { entry ->
            entry.title !in seen && (installed == null || catalogResolved[entry.title] == null) &&
                HomeLayout.catalogKey(entry) !in hidden
        }
        return AppsModel(favs, stored, placeholders, hidden.size, seen)
    }

    private fun applyApps(model: AppsModel) {
        home.saveFavourites(model.stored)
        home.setSeenCatalog(model.seen)
        if (model.signature == appsSignature) return
        appsSignature = model.signature

        val focusedKey = (currentFocus as? Tile)?.key
        val focusWasInApps = currentFocus?.let { isInApps(it) } == true

        favouritesRow.removeAllViews()
        model.favourites.forEach { fav ->
            favouritesRow.addView(appTile(appKey(fav.pkg), fav.label, fav.pkg, "Favorit"))
        }
        model.placeholders.forEach { entry ->
            favouritesRow.addView(appTile(HomeLayout.catalogKey(entry), entry.title, null, "Nicht installiert").apply {
                setAvailable(false)
                setOnClickListener { toast("${entry.title} ist nicht installiert – im Amazon Appstore installieren") }
            })
        }

        // Everything hidden: one tile brings the apps back (otherwise this is in the options).
        if (favouritesRow.childCount == 0 && model.hiddenCount > 0) {
            favouritesRow.addView(Tile(this, Tile.Style.APP).apply {
                key = KEY_RESTORE
                setTitle("Ausgeblendete einblenden (${model.hiddenCount})")
                shelfCategory = "Favoriten"
                shelfTitle = "Ausgeblendete Apps"
                onFocused = { onTileFocused(it) }
                setOnClickListener { restoreHidden() }
            })
        }

        when {
            restorePendingFocus() -> {}
            focusedKey != null && findTile(focusedKey)?.requestFocus() == true -> {}
            focusWasInApps || currentFocus == null || currentFocus?.isShown != true -> focusFirst()
        }
    }

    private fun appTile(key: String, title: String, pkg: String?, category: String): Tile =
        Tile(this, Tile.Style.APP).apply {
            this.key = key
            setTitle(title)
            shelfCategory = category
            shelfTitle = title
            onFocused = { onTileFocused(it) }
            if (pkg != null) {
                artwork[pkg]?.let { applyArt(this, it) }
                setOnClickListener { launchPackage(pkg, title) }
            }
            setOnLongClickListener {
                showOptions(this)
                true
            }
        }

    private fun applyArt(tile: Tile, art: Art) {
        tile.setArtwork(art.artwork?.drawable, art.artwork?.isBanner == true)
        tile.glowColor = art.glow
        if (tile.isFocused) showGlow(art.glow)
    }

    private fun isInApps(view: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v === favouritesRow) return true
            v = v.parent as? View
        }
        return false
    }

    private fun findTile(key: String): Tile? {
        fun search(group: ViewGroup): Tile? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is Tile && child.key == key) return child
                if (child is ViewGroup && child !is Tile) search(child)?.let { return it }
            }
            return null
        }
        return search(rowsContainer)
    }

    private fun restorePendingFocus(): Boolean {
        val key = pendingFocusKey ?: return false
        val tile = findTile(key) ?: return false
        if (!tile.isShown || !tile.requestFocus()) return false
        pendingFocusKey = null
        return true
    }

    private fun focusFirst() {
        val first = (0 until favouritesRow.childCount).map { favouritesRow.getChildAt(it) }.firstOrNull()
            ?: playStationTile
        first?.requestFocus()
    }

    // ---------------------------------------------------------------- options & moving

    private fun showOptions(tile: Tile) {
        if (overlay != null || movingTile != null) return
        val key = tile.key
        val pkg = key.takeIf { it.startsWith("app:") }?.removePrefix("app:")
        val title = tile.shelfTitle
        val items = mutableListOf<Pair<String, () -> Unit>>()
        when {
            pkg != null -> {
                items += "Verschieben" to { startMove(tile) }
                items += "Ausblenden" to { hide(key) }
            }
            key.startsWith("cat:") -> items += "Ausblenden" to { hide(key) }
            else -> return
        }
        val hiddenCount = home.hidden().size
        if (hiddenCount > 0) items += "Ausgeblendete einblenden ($hiddenCount)" to { restoreHidden() }
        val panel = OptionsPanel(this, title, items) { closeOverlay() }
        optionsAnchor = tile
        showOverlay(panel, panel.firstButton)
    }

    private fun showOverlay(panel: View, focus: View) {
        overlay = panel
        overlayCloseKey = null
        setBehindFocusable(false)
        (window.decorView as ViewGroup).addView(panel, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        focus.requestFocus()
    }

    private fun closeOverlay() {
        val panel = overlay ?: return
        overlay = null
        (window.decorView as ViewGroup).removeView(panel)
        setBehindFocusable(true)
        val anchor = optionsAnchor
        optionsAnchor = null
        if (anchor != null && anchor.isAttachedToWindow) anchor.requestFocus()
    }

    private fun setBehindFocusable(focusable: Boolean) {
        val mode = if (focusable) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        (content as ViewGroup).descendantFocusability = mode
        (dock as ViewGroup).descendantFocusability = mode
    }

    private fun startMove(tile: Tile) {
        if (!tile.isAttachedToWindow) return
        movingTile = tile
        tile.setMoving(true)
        tile.requestFocus()
        showShelf("Verschieben · ◀ ▶, OK zum Beenden", tile.shelfTitle)
    }

    private fun moveFavourite(tile: Tile, direction: Int) {
        val index = favouritesRow.indexOfChild(tile)
        val favCount = (0 until favouritesRow.childCount).count {
            (favouritesRow.getChildAt(it) as? Tile)?.key?.startsWith("app:") == true
        }
        val target = index + direction
        if (index < 0 || target < 0 || target >= favCount) return
        favouritesRow.removeViewAt(index)
        favouritesRow.addView(tile, target)
        tile.requestFocus()
    }

    private fun endMove() {
        val tile = movingTile ?: return
        movingTile = null
        tile.setMoving(false)
        val order = (0 until favouritesRow.childCount)
            .mapNotNull { (favouritesRow.getChildAt(it) as? Tile)?.key?.takeIf { k -> k.startsWith("app:") } }
            .map { it.removePrefix("app:") }
        val stored = home.favourites()
        val byPkg = stored.associateBy { it.pkg }
        // Visible favourites in the new order, hidden ones keep their place at the end.
        home.saveFavourites(order.mapNotNull { byPkg[it] } + stored.filter { it.pkg !in order })
        // The row already shows the new order: only remember it, no rebuild.
        appsSignature = computeModel().signature
        if (tile.isFocused) onTileFocused(tile)
    }

    private fun hide(key: String) {
        home.setHidden(home.hidden() + key)
        applyApps(computeModel())
        if (currentFocus?.isShown != true) focusFirst()
        toast("Ausgeblendet – OK lang drücken auf einer App blendet es wieder ein")
    }

    private fun restoreHidden() {
        val count = home.hidden().size
        home.setHidden(emptySet())
        applyApps(computeModel())
        toast(if (count == 1) "1 App wieder eingeblendet" else "$count Apps wieder eingeblendet")
        if (currentFocus?.isShown != true) focusFirst()
    }

    // ---------------------------------------------------------------- Laufende Apps (MENU)

    private fun showTasks() {
        if (overlay != null || movingTile != null) return
        optionsAnchor = currentFocus
        Thread({
            val hasAccess = recentApps.hasUsageAccess()
            val entries = recentApps.load(launcher, TASK_EXCLUDED)
            entries.forEach { e ->
                if (!artwork.containsKey(e.pkg)) {
                    val art = launcher.loadArtwork(e.pkg)
                    artwork[e.pkg] = Art(art, art?.drawable?.let { Glow.fromDrawable(it) })
                }
            }
            mainHandler.post {
                if (isDestroyed || !started || overlay != null) return@post
                val panel = TaskPanel(
                    this, entries, hasAccess,
                    artwork = { pkg ->
                        // Own copy: the same banner may be shown by a favourite tile at the same time.
                        artwork[pkg]?.artwork?.let { a -> (a.drawable.constantState?.newDrawable() ?: a.drawable) to a.isBanner }
                    },
                    onCloseApp = { recentApps.close(it.pkg) },
                    onCloseAll = { list ->
                        list.forEach { recentApps.close(it.pkg) }
                        closeOverlay()
                        toast(if (list.size == 1) "1 App geschlossen" else "${list.size} Apps geschlossen")
                    },
                    onDiagnostics = {
                        closeOverlay()
                        startActivity(Intent(this, DiagnosticsActivity::class.java))
                    },
                )
                showOverlay(panel, panel.firstFocus)
            }
        }, "c3-tasks").start()
    }

    // ---------------------------------------------------------------- state

    /**
     * Re-evaluates installed apps and TV inputs on a short-lived background thread,
     * so the first frame is never blocked by PackageManager or TV-input binder calls.
     */
    private fun refresh() {
        val executor = Executors.newSingleThreadExecutor().also { io = it }
        val hidden = home.hidden()
        val favourites = home.favourites().map { it.pkg }
        executor.execute {
            val catalog = AppCatalog.streaming.associate { it.title to launcher.resolve(it) }
            val installed = (favourites.filter { launcher.launchIntent(it) != null } + catalog.values.filterNotNull()).toSet()
            val moonlightInstalled = launcher.resolve(AppCatalog.moonlight) != null
            val silkAvailable = launcher.resolve(WebStore.silk) != null
            val inputs = tvInputs.list()
            val ps = TvInputs.findPlayStation(inputs)
            val psPort = ps?.parentId ?: TvInputs.PS5_FALLBACK_INPUT
            val otherPorts = TvInputs.hdmiPorts(inputs).filter { it.id != psPort }

            mainHandler.post {
                if (isDestroyed) return@post
                installedPkgs = installed
                catalogResolved = catalog
                applyApps(computeModel())
                this.moonlightInstalled = moonlightInstalled
                browserTile?.setAvailable(silkAvailable)
                pcTiles.forEach { it.setAvailable(moonlightInstalled) }
                playStationInputId = ps?.id ?: TvInputs.PS5_FALLBACK_INPUT
                otherPorts.forEach { addHdmiTile(it) }
                restorePendingFocus()
            }

            // Banners: hidden apps skipped, each package loaded once per process.
            val order = installed.filter { appKey(it) !in hidden && !artwork.containsKey(it) }
            for (pkg in order) {
                if (Thread.currentThread().isInterrupted) return@execute
                val art = launcher.loadArtwork(pkg)
                val entry = Art(art, art?.drawable?.let { Glow.fromDrawable(it) })
                artwork[pkg] = entry
                mainHandler.post { if (!isDestroyed) findTile(appKey(pkg))?.let { applyArt(it, entry) } }
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
                key = "pc:moonlight"
                glowColor = getColor(R.color.c3_accent)
                setChip("Moonlight", ok = false)
                setOnClickListener { launchApp(AppCatalog.moonlight, hint = "Moonlight aus dem Amazon Appstore installieren") }
            }
        } else {
            pcs.forEach { pc ->
                pcTiles += deviceTile(pc.name, R.drawable.ic_monitor, "C3 Desktop · ${pc.appName}").apply {
                    key = "pc:" + pc.uuid.lowercase()
                    glowColor = getColor(R.color.c3_accent)
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
        applyPcStates()
        restorePendingFocus()
    }

    private fun onPcStates(states: Map<String, PcStatus.State>) {
        pcStates = states
        applyPcStates()
    }

    /** Status capsule + title-area text of the PC tiles (Online / Belegt / Offline). */
    private fun applyPcStates() {
        val pcs = pcStore.load().associateBy { "pc:" + it.uuid.lowercase() }
        pcTiles.forEach { tile ->
            val pc = pcs[tile.key] ?: return@forEach
            val state = pcStates[pc.uuid.lowercase()]
            val label = when (state) {
                PcStatus.State.ONLINE -> "Online"
                PcStatus.State.BUSY -> "Belegt"
                PcStatus.State.OFFLINE -> "Offline"
                null -> null
            }
            tile.setChip(label ?: pc.appName, ok = state == PcStatus.State.ONLINE)
            tile.shelfCategory = "C3 Desktop · " + (label ?: pc.appName)
            if (tile.isFocused) showShelf(tile.shelfCategory, tile.shelfTitle)
        }
    }

    /**
     * Web row (browser tile) and private row exist only while the private area is switched on
     * in the C3 Updater; "Privat aus" removes both from the home screen.
     */
    private fun rebuildPrivateTiles() {
        val row = privateRow ?: return
        val hadFocus = browserTile?.isFocused == true || row.hasFocus()
        row.removeAllViews()
        val webVisible = if (webStore.isPrivateEnabled()) View.VISIBLE else View.GONE
        webTitle?.visibility = webVisible
        webScroll?.visibility = webVisible
        val sites = webStore.privateSites()
        val visible = if (sites.isEmpty()) View.GONE else View.VISIBLE
        privateTitle?.visibility = visible
        privateScroll?.visibility = visible
        if (hadFocus && currentFocus?.isShown != true) focusFirst()
        sites.forEach { site ->
            row.addView(deviceTile(site.name, R.drawable.ic_globe, "Privat").apply {
                key = "site:" + site.url
                setOnClickListener { openUrl(site.url) }
            })
        }
        restorePendingFocus()
    }

    private fun openUrl(url: String) {
        if (WebStore.openIntents(url).none { launcher.start(it) }) {
            toast("Kein Browser verfügbar – Silk im Amazon Appstore installieren")
        }
    }

    private fun addHdmiTile(input: TvInputs.Input) {
        if (hdmiTiles.containsKey(input.id)) return
        val label = input.label.ifEmpty { "HDMI" }
        val tile = deviceTile(label, R.drawable.ic_hdmi, "Eingang").apply {
            key = "hdmi:" + input.id
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

    private fun launchPackage(pkg: String, title: String) {
        val intent = launcher.launchIntent(pkg)
        if (intent == null || !launcher.start(intent)) {
            toast("$title lässt sich nicht starten – im Amazon Appstore prüfen")
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
        private const val GLOW_ALPHA = 0.32f
        private const val GLOW_FADE_MS = 320L
        private const val PLAYSTATION_BLUE = 0xFF2F6FE0.toInt()
        private const val KEY_RESTORE = "hidden"

        /** System parts of Fire OS that are not "apps" in the task list. */
        private val TASK_EXCLUDED = setOf(
            "com.amazon.tv.launcher",
            "com.amazon.tv.settings.v2",
            "com.amazon.firehomestarter",
            "com.amazon.tv.quicksettings.ui",
        )

        private val MOVE_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU,
        )

        private val NAV_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )

        private fun appKey(pkg: String) = "app:$pkg"
    }
}
