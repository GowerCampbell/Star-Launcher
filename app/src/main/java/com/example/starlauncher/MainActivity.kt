package com.example.starlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.*
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : Activity() {

    private val gson = Gson()
    private val PREFS = "PixelStationGridPrefs"
    private val KEY_FAVS = "key_pinned_favs_v4"
    private val KEY_WIDGET_IDS = "key_widget_ids_v4"
    private val KEY_FOLDERS = "key_shelves_v4"

    private val APPWIDGET_HOST_ID = 2048
    private val REQUEST_PICK_APPWIDGET = 101
    private val REQUEST_CREATE_APPWIDGET = 102

    // --- WHITE SKETCHBOOK & SYSTEM PALETTE ---
    private val COLOR_TRANSPARENT = Color.TRANSPARENT
    private val COLOR_SCRIM = Color.parseColor("#66000000")              // 40% Backdrop Dim
    private val COLOR_PAPER_WHITE = Color.parseColor("#FAFBFD")        // Pristine White Card
    private val COLOR_INK_BLACK = Color.parseColor("#111113")          // Drafting Ink Text
    private val COLOR_INK_MUTED = Color.parseColor("#71717A")          // Graphite Secondary Text
    private val COLOR_CARD_STROKE = Color.parseColor("#26111113")        // 15% Ink Border
    private val COLOR_RAIL_PILL = Color.parseColor("#E6FFFFFF")          // High-Contrast White Rail
    private val COLOR_RAIL_ACTIVE = Color.parseColor("#111113")        // Active Letter Color

    data class AppItem(val name: String, val packageName: String, val icon: Drawable)
    data class ShelfFolder(val title: String, val packages: MutableList<String>)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var savedWidgetIds = mutableListOf<Int>()
    private var shelfFolders = mutableListOf<ShelfFolder>()

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private lateinit var rootLayout: FrameLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicatorTv: TextView
    private var widgetContainer: LinearLayout? = null
    private var favoritesGridLayout: GridLayout? = null
    private var shelvesContainer: LinearLayout? = null

    // Floating Center Paper Modal
    private lateinit var centerModalScrim: FrameLayout
    private lateinit var centerFloatingCard: LinearLayout
    private lateinit var centerScroll: ScrollView
    private lateinit var centerAppContainer: LinearLayout
    private lateinit var centerTitleTv: TextView
    private lateinit var floatingLetterBadge: TextView

    private var alphabetRailView: SketchbookAlphabetRailView? = null
    private var vibrator: Vibrator? = null
    private val springInterpolator = OvershootInterpolator(0.85f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        loadUserData()
        buildInterface()
    }

    override fun onStart() {
        super.onStart()
        try { appWidgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { appWidgetHost.stopListening() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun pulseHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(6)
            }
        } catch (_: Exception) {}
    }

    private fun loadUserData() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val favJson = p.getString(KEY_FAVS, null)
        val widgetJson = p.getString(KEY_WIDGET_IDS, null)
        val shelfJson = p.getString(KEY_FOLDERS, null)

        favoritePackages = if (favJson != null) gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type) else mutableListOf()
        savedWidgetIds = if (widgetJson != null) gson.fromJson(widgetJson, object : TypeToken<MutableList<Int>>() {}.type) else mutableListOf()
        shelfFolders = if (shelfJson != null) {
            gson.fromJson(shelfJson, object : TypeToken<MutableList<ShelfFolder>>() {}.type)
        } else {
            mutableListOf(
                ShelfFolder("Comms & Messaging", mutableListOf("com.google.android.apps.messaging", "com.google.android.dialer", "com.whatsapp", "com.discord")),
                ShelfFolder("Reading & Codex", mutableListOf("md.obsidian", "com.amazon.kindle", "com.flyersoft.moonreader")),
                ShelfFolder("Banking & HSBC", mutableListOf("uk.co.hsbc.hsbcukmobilebanking", "com.monzo.android")),
                ShelfFolder("Development & Shell", mutableListOf("com.termux", "com.github.android")),
                ShelfFolder("Transit & Maps", mutableListOf("com.trainpal", "com.thetrainline", "com.google.android.apps.maps"))
            )
        }
    }

    private fun saveUserData() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, gson.toJson(favoritePackages))
            .putString(KEY_WIDGET_IDS, gson.toJson(savedWidgetIds))
            .putString(KEY_FOLDERS, gson.toJson(shelfFolders))
            .apply()
    }

    private fun buildInterface() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        viewPager = ViewPager2(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
        }

        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = 2
            override fun getItemViewType(position: Int) = position
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = if (viewType == 0) buildPageOneMain() else buildPageTwoShelves()
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pulseHaptic()
                pageIndicatorTv.text = if (position == 0) "• ○" else "○ •"
            }
        })
        rootLayout.addView(viewPager)

        pageIndicatorTv = TextView(this).apply {
            text = "• ○"
            setTextColor(COLOR_PAPER_WHITE)
            textSize = 12f
            letterSpacing = 0.2f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { setMargins(0, 0, 0, 16) }
        }
        rootLayout.addView(pageIndicatorTv)

        // ================= FLOATING CENTER MODAL (DIRECTLY OVER WIDGETS) =================
        centerModalScrim = FrameLayout(this).apply {
            setBackgroundColor(COLOR_SCRIM)
            visibility = View.GONE
            setOnClickListener { hideFloatingCenterModal() }
        }

        val displayMetrics = resources.displayMetrics
        val cardWidth = (displayMetrics.widthPixels * 0.86).toInt()
        val cardHeight = (displayMetrics.heightPixels * 0.62).toInt()

        centerFloatingCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_PAPER_WHITE)
                cornerRadius = 36f
                setStroke(2, COLOR_CARD_STROKE)
            }
            elevation = 48f
            setPadding(32, 28, 32, 28)
            val lp = FrameLayout.LayoutParams(cardWidth, cardHeight, Gravity.CENTER)
            layoutParams = lp
            isClickable = true
        }

        val cardHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 4, 8, 16)
        }

        centerTitleTv = TextView(this).apply {
            text = "ALL APPLICATIONS"
            setTextColor(COLOR_INK_BLACK)
            textSize = 14f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closePrompt = TextView(this).apply {
            text = "[ TAP OFF ]"
            setTextColor(COLOR_INK_MUTED)
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        cardHeaderRow.addView(centerTitleTv)
        cardHeaderRow.addView(closePrompt)
        centerFloatingCard.addView(cardHeaderRow)

        centerScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        centerAppContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        centerScroll.addView(centerAppContainer)
        centerFloatingCard.addView(centerScroll)
        centerModalScrim.addView(centerFloatingCard)

        floatingLetterBadge = TextView(this).apply {
            visibility = View.GONE
            setTextColor(COLOR_INK_BLACK)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(COLOR_PAPER_WHITE)
                cornerRadius = 32f
                setStroke(2, COLOR_CARD_STROKE)
            }
            elevation = 52f
            layoutParams = FrameLayout.LayoutParams(110, 110, Gravity.END or Gravity.TOP).apply {
                marginEnd = 68
            }
        }
        centerModalScrim.addView(floatingLetterBadge)
        rootLayout.addView(centerModalScrim)

        // ================= WIDGET-SIDE ALPHABET RAIL =================
        val density = resources.displayMetrics.density
        val railParams = FrameLayout.LayoutParams(
            (density * 28).toInt(),
            (density * 430).toInt(),
            Gravity.END or Gravity.TOP
        ).apply {
            setMargins(0, (density * 160).toInt(), 8, 0)
        }

        alphabetRailView = SketchbookAlphabetRailView(this) { char, rawY ->
            if (char == null) {
                floatingLetterBadge.visibility = View.GONE
            } else {
                pulseHaptic()
                showFloatingCenterModal(char, rawY)
            }
        }.apply { layoutParams = railParams }
        rootLayout.addView(alphabetRailView)

        setContentView(rootLayout)
        restoreSavedWidgets()
        refreshAll()
    }

    // ================= PAGE 1: PRIMARY CANVAS =================
    private fun buildPageOneMain(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 48, 88, 48) // Clean right padding to leave space for the alphabet rail
        }

        val now = Calendar.getInstance()
        val dTv = TextView(this).apply {
            text = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now.time)
            setTextColor(COLOR_PAPER_WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(dTv)

        val weatherTv = TextView(this).apply {
            text = "18°C"
            setTextColor(COLOR_PAPER_WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 16)
        }
        content.addView(weatherTv)

        widgetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            layoutParams = lp
        }
        content.addView(widgetContainer)

        val addWidgetBtn = TextView(this).apply {
            text = "+ Pin Widget (Tasks / Calendar / Spotify)"
            setTextColor(COLOR_INK_MUTED)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(COLOR_PAPER_WHITE)
                cornerRadius = 18f
                setStroke(2, COLOR_CARD_STROKE)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            layoutParams = lp
            setOnClickListener {
                pulseHaptic()
                launchWidgetPicker()
            }
        }
        content.addView(addWidgetBtn)

        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) }
            layoutParams = lp
        }
        favoritesGridLayout = grid
        content.addView(grid)

        content.addView(createSketchbookSearchPill())

        scroll.addView(content)
        return scroll
    }

    // ================= PAGE 2: WORKSPACE SHELVES =================
    private fun buildPageTwoShelves(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 48, 88, 48)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val title = TextView(this).apply {
            text = "WORKSPACE SHELVES"
            setTextColor(COLOR_PAPER_WHITE)
            textSize = 15f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addShelfBtn = TextView(this).apply {
            text = "[ + SHELF ]"
            setTextColor(COLOR_PAPER_WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(10, 6, 10, 6)
            setOnClickListener { showAddShelfDialog() }
        }
        headerRow.addView(title)
        headerRow.addView(addShelfBtn)
        content.addView(headerRow)

        shelvesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(shelvesContainer)

        scroll.addView(content)
        renderShelves()
        return scroll
    }

    private fun renderShelves() {
        val container = shelvesContainer ?: return
        container.removeAllViews()

        for (shelf in shelfFolders) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 16, 20, 16)
                background = GradientDrawable().apply {
                    setColor(COLOR_PAPER_WHITE)
                    cornerRadius = 24f
                    setStroke(2, COLOR_CARD_STROKE)
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 14) }
                layoutParams = lp
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tTv = TextView(this).apply {
                text = shelf.title.uppercase()
                setTextColor(COLOR_INK_BLACK)
                textSize = 12f
                letterSpacing = 0.1f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val countTv = TextView(this).apply {
                text = "${shelf.packages.size}"
                setTextColor(COLOR_INK_MUTED)
                textSize = 11f
                typeface = Typeface.MONOSPACE
            }
            titleRow.addView(tTv)
            titleRow.addView(countTv)
            card.addView(titleRow)

            val iconRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 0)
            }

            val appsInShelf = allApps.filter { shelf.packages.contains(it.packageName) }
            if (appsInShelf.isEmpty()) {
                val emptyHint = TextView(this).apply {
                    text = "Empty shelf. Long-press apps to add."
                    setTextColor(COLOR_INK_MUTED)
                    textSize = 11f
                }
                iconRow.addView(emptyHint)
            } else {
                for (app in appsInShelf.take(5)) {
                    val iv = ImageView(this).apply {
                        setImageDrawable(app.icon)
                        val size = 76
                        layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 14, 0) }
                    }
                    iconRow.addView(iv)
                }
            }
            card.addView(iconRow)

            card.setOnClickListener {
                pulseHaptic()
                showShelfDetailsDialog(shelf)
            }
            container.addView(card)
        }
    }

    private fun showShelfDetailsDialog(shelf: ShelfFolder) {
        val apps = allApps.filter { shelf.packages.contains(it.packageName) }
        val names = apps.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(shelf.title)
            .apply {
                if (names.isEmpty()) setMessage("No tools inside this shelf.")
                else setItems(names) { _, which -> launchApp(apps[which].packageName) }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAddShelfDialog() {
        val input = EditText(this).apply {
            hint = "Shelf name (e.g. Comms, Banking)"
            setTextColor(COLOR_INK_BLACK)
            setHintTextColor(COLOR_INK_MUTED)
        }
        AlertDialog.Builder(this)
            .setTitle("New Workspace Shelf")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    shelfFolders.add(ShelfFolder(txt, mutableListOf()))
                    saveUserData()
                    renderShelves()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================= DYNAMIC WIDGET HOSTING =================
    private fun launchWidgetPicker() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_PICK_APPWIDGET -> {
                    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: return
                    configureOrBindWidget(appWidgetId)
                }
                REQUEST_CREATE_APPWIDGET -> {
                    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: return
                    attachWidgetView(appWidgetId)
                }
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                try { appWidgetHost.deleteAppWidgetId(appWidgetId) } catch (_: Exception) {}
            }
        }
    }

    private fun configureOrBindWidget(appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return
        if (appWidgetInfo.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = appWidgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
        } else {
            attachWidgetView(appWidgetId)
        }
    }

    private fun attachWidgetView(appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return
        val hostView: AppWidgetHostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo).apply {
            setAppWidget(appWidgetId, appWidgetInfo)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) }
            layoutParams = lp
        }

        hostView.setOnLongClickListener {
            pulseHaptic()
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Remove Widget")
                .setMessage("Remove this widget from canvas?")
                .setPositiveButton("Remove") { _, _ ->
                    try { appWidgetHost.deleteAppWidgetId(appWidgetId) } catch (_: Exception) {}
                    savedWidgetIds.remove(appWidgetId)
                    saveUserData()
                    widgetContainer?.removeView(hostView)
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        widgetContainer?.addView(hostView)
        if (!savedWidgetIds.contains(appWidgetId)) {
            savedWidgetIds.add(appWidgetId)
            saveUserData()
        }
    }

    private fun restoreSavedWidgets() {
        val container = widgetContainer ?: return
        container.removeAllViews()
        val validIds = mutableListOf<Int>()
        for (id in savedWidgetIds) {
            val info = appWidgetManager.getAppWidgetInfo(id)
            if (info != null) {
                attachWidgetView(id)
                validIds.add(id)
            } else {
                try { appWidgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
            }
        }
        savedWidgetIds = validIds
        saveUserData()
    }

    // ================= FULL-COLOR FAVORITES GRID =================
    private fun renderFavoritesGrid() {
        val grid = favoritesGridLayout ?: return
        grid.removeAllViews()
        val favs = allApps.filter { favoritePackages.contains(it.packageName) }.take(8)

        if (favs.isEmpty()) {
            resetFavoritesToSmartDefaults()
            return
        }

        val displayMetrics = resources.displayMetrics
        val availableWidth = displayMetrics.widthPixels - 160
        val tileWidth = availableWidth / 4

        for (app in favs) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(4, 6, 4, 6)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = tileWidth
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(2, 4, 2, 4)
                }
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                val size = 96
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 0, 6) }
            }
            tile.addView(icon)

            val name = TextView(this).apply {
                text = app.name
                setTextColor(COLOR_PAPER_WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 1
            }
            tile.addView(name)

            tile.setOnClickListener {
                pulseHaptic()
                launchApp(app.packageName)
            }
            tile.setOnLongClickListener {
                pulseHaptic()
                showAppOptions(app)
                true
            }

            grid.addView(tile)
        }
    }

    private fun createSketchbookSearchPill(): View {
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 14, 18, 14)
            background = GradientDrawable().apply {
                setColor(COLOR_PAPER_WHITE)
                cornerRadius = 28f
                setStroke(2, COLOR_CARD_STROKE)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 0) }
            layoutParams = lp
        }

        val gIcon = TextView(this).apply {
            text = "G"
            setTextColor(COLOR_INK_BLACK)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 14, 0)
        }
        searchBox.addView(gIcon)

        val sInput = EditText(this).apply {
            hint = "Search..."
            setHintTextColor(COLOR_INK_MUTED)
            setTextColor(COLOR_INK_BLACK)
            textSize = 13f
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val query = text.toString().trim()
                    if (query.isNotEmpty()) {
                        pulseHaptic()
                        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, query)
                        }
                        try { startActivity(intent) } catch (_: Exception) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query")))
                        }
                        text.clear()
                    }
                    true
                } else false
            }
        }
        searchBox.addView(sInput)
        return searchBox
    }

    // ================= FLOATING CENTER MODAL DISPLAY =================
    private fun showFloatingCenterModal(filterChar: Char, rawY: Float) {
        if (centerModalScrim.visibility != View.VISIBLE) {
            centerModalScrim.visibility = View.VISIBLE
            centerModalScrim.alpha = 0f
            centerFloatingCard.scaleX = 0.82f
            centerFloatingCard.scaleY = 0.82f
            centerModalScrim.animate().alpha(1f).setDuration(120).start()
            centerFloatingCard.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(220)
                .setInterpolator(springInterpolator)
                .start()
        }

        floatingLetterBadge.visibility = View.VISIBLE
        floatingLetterBadge.y = (rawY - 140).coerceIn(120f, (resources.displayMetrics.heightPixels - 260).toFloat())
        floatingLetterBadge.text = filterChar.toString()

        centerAppContainer.removeAllViews()
        val filtered = if (filterChar == '•') allApps else allApps.filter { it.name.startsWith(filterChar, ignoreCase = true) }

        centerTitleTv.text = if (filterChar != '•') "APPLICATIONS [$filterChar]" else "ALL APPLICATIONS"

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No tools starting with '$filterChar'"
                setTextColor(COLOR_INK_MUTED)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(12, 32, 0, 0)
            }
            centerAppContainer.addView(emptyTv)
            return
        }

        for (app in filtered) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 10, 12, 10)
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = 18f
                }
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                val size = 88
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 18, 0) }
            }
            row.addView(icon)

            val name = TextView(this).apply {
                text = app.name
                setTextColor(COLOR_INK_BLACK)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(name)

            row.setOnClickListener {
                pulseHaptic()
                hideFloatingCenterModal()
                launchApp(app.packageName)
            }
            row.setOnLongClickListener {
                pulseHaptic()
                showAppOptions(app)
                true
            }

            centerAppContainer.addView(row)
        }
        centerScroll.scrollTo(0, 0)
    }

    private fun hideFloatingCenterModal() {
        if (centerModalScrim.visibility == View.VISIBLE) {
            centerFloatingCard.animate().scaleX(0.85f).scaleY(0.85f).setDuration(140).start()
            centerModalScrim.animate().alpha(0f).setDuration(140).withEndAction {
                centerModalScrim.visibility = View.GONE
                floatingLetterBadge.visibility = View.GONE
            }.start()
        }
    }

    private fun resetFavoritesToSmartDefaults() {
        pulseHaptic()
        favoritePackages.clear()

        val priorityTargets = listOf(
            "com.trainpal",
            "com.thetrainline",
            "md.obsidian",
            "com.termux",
            "com.github.android",
            "com.bandlab.bandlab",
            "com.google.android.GoogleCamera",
            "com.google.android.apps.chromecast.app"
        )

        for (target in priorityTargets) {
            if (allApps.any { it.packageName == target } && !favoritePackages.contains(target)) {
                favoritePackages.add(target)
            }
        }

        for (app in allApps) {
            if (favoritePackages.size >= 8) break
            if (!favoritePackages.contains(app.packageName)) {
                favoritePackages.add(app.packageName)
            }
        }

        saveUserData()
        renderFavoritesGrid()
    }

    private fun sanitizeSavedData() {
        val installed = allApps.map { it.packageName }.toSet()
        favoritePackages.retainAll(installed)
        for (f in shelfFolders) f.packages.retainAll(installed)
        saveUserData()
    }

    private fun refreshAll() {
        allApps = loadInstalledApps()
        sanitizeSavedData()
        renderFavoritesGrid()
        renderShelves()
        alphabetRailView?.setAppList(allApps)
    }

    private fun showAppOptions(app: AppItem) {
        val isFav = favoritePackages.contains(app.packageName)
        val favLabel = if (isFav) "Unpin from Main Canvas" else "Pin to Main Canvas (Max 8)"
        val opts = mutableListOf(favLabel)
        for (shelf in shelfFolders) {
            opts.add("Add to: ${shelf.title}")
        }

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(opts.toTypedArray()) { _, which ->
                if (which == 0) {
                    if (isFav) favoritePackages.remove(app.packageName)
                    else if (favoritePackages.size < 8) favoritePackages.add(app.packageName)
                    else Toast.makeText(this@MainActivity, "Canvas grid full (Max 8)", Toast.LENGTH_SHORT).show()
                    saveUserData()
                    renderFavoritesGrid()
                } else {
                    val shelf = shelfFolders[which - 1]
                    if (!shelf.packages.contains(app.packageName)) {
                        shelf.packages.add(app.packageName)
                        saveUserData()
                        renderShelves()
                        Toast.makeText(this@MainActivity, "Added to ${shelf.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun launchApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (intent != null) startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this@MainActivity, "Unable to launch", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (centerModalScrim.visibility == View.VISIBLE) {
            hideFloatingCenterModal()
        } else if (viewPager.currentItem != 0) {
            viewPager.currentItem = 0
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadInstalledApps(): List<AppItem> {
        val list = mutableListOf<AppItem>()
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        for (info in resolved) {
            val pName = info.activityInfo?.packageName ?: continue
            if (pName == packageName) continue
            val label = info.loadLabel(packageManager)?.toString() ?: pName
            val rawIcon = info.loadIcon(packageManager)
            list.add(AppItem(label, pName, rawIcon))
        }
        return list.sortedBy { it.name.lowercase() }
    }

    // ================= SKETCHBOOK ALPHABET RAIL ENGINE =================
    inner class SketchbookAlphabetRailView(
        context: Context,
        private val onLetterScrubbed: (Char?, Float) -> Unit
    ) : View(context) {

        private val fullAlphabet = listOf('•') + ('A'..'Z').toList()
        private var validLetters: Set<Char> = emptySet()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val resetRunnable = Runnable {
            activeIdx = 0
            touchY = -1f
            invalidate()
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val railBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_RAIL_PILL
            style = Paint.Style.FILL
        }
        private val railStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARD_STROKE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private var activeIdx = -1
        private var touchY = -1f

        fun setAppList(apps: List<AppItem>) {
            val set = mutableSetOf('•')
            for (app in apps) {
                val firstChar = app.name.firstOrNull()?.uppercaseChar()
                if (firstChar != null && firstChar in 'A'..'Z') {
                    set.add(firstChar)
                }
            }
            validLetters = set
            invalidate()
        }

        private fun scheduleReset() {
            mainHandler.removeCallbacks(resetRunnable)
            mainHandler.postDelayed(resetRunnable, 3500) // Auto-resets to top anchor after 3.5s idle
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = RectF(2f, 2f, width.toFloat() - 2f, height.toFloat() - 2f)
            canvas.drawRoundRect(rect, 20f, 20f, railBgPaint)
            canvas.drawRoundRect(rect, 20f, 20f, railStrokePaint)

            val totalItems = fullAlphabet.size
            val stepY = height.toFloat() / totalItems

            for (i in fullAlphabet.indices) {
                val letter = fullAlphabet[i]
                val hasApps = validLetters.isEmpty() || validLetters.contains(letter)
                val baseY = (stepY * i) + (stepY / 2)

                val dist = if (touchY >= 0) abs(baseY - touchY) else 1000f
                val waveFactor = (1.0f - (dist / (height * 0.24f))).coerceIn(0f, 1f)

                // Swells outward to the left when selected
                val xOffset = - (waveFactor * 22f * resources.displayMetrics.density)
                val textSizeSp = 8.0f + (waveFactor * 5.5f)
                paint.textSize = textSizeSp * resources.displayMetrics.density

                if (i == activeIdx) {
                    paint.color = COLOR_RAIL_ACTIVE
                    paint.alpha = 255
                } else if (!hasApps) {
                    paint.color = Color.parseColor("#A1A1AA")
                    paint.alpha = 40 // Dims empty letters
                } else {
                    paint.color = COLOR_INK_MUTED
                    paint.alpha = (100 + (waveFactor * 130)).toInt()
                }

                val centerX = (width * 0.5f) + xOffset
                val textY = baseY + (paint.textSize / 3f)
                canvas.drawText(letter.toString(), centerX, textY, paint)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    mainHandler.removeCallbacks(resetRunnable)
                    touchY = event.y.coerceIn(0f, height.toFloat() - 1)
                    val rawIdx = ((touchY / height) * fullAlphabet.size).toInt().coerceIn(0, fullAlphabet.size - 1)

                    var chosenLetter = fullAlphabet[rawIdx]
                    var targetIdx = rawIdx

                    // Auto-skips dead letters to the nearest valid letter
                    if (chosenLetter != '•' && validLetters.isNotEmpty() && !validLetters.contains(chosenLetter)) {
                        val nearest = validLetters.filter { it != '•' }.minByOrNull {
                            abs(fullAlphabet.indexOf(it) - rawIdx)
                        }
                        if (nearest != null) {
                            chosenLetter = nearest
                            targetIdx = fullAlphabet.indexOf(nearest)
                        }
                    }

                    if (targetIdx != activeIdx) {
                        activeIdx = targetIdx
                        onLetterScrubbed(chosenLetter, event.rawY)
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    onLetterScrubbed(null, 0f)
                    scheduleReset()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
