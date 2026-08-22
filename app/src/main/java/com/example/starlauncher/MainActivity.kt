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
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val gson = Gson()
    private val PREFS = "PixelStationGridPrefs"
    private val KEY_FAVS = "key_pinned_favs_v5"
    private val KEY_WIDGET_IDS = "key_widget_ids_v5"
    private val KEY_FOLDERS = "key_shelves_v5"

    private val APPWIDGET_HOST_ID = 2048
    private val REQUEST_PICK_APPWIDGET = 101
    private val REQUEST_CREATE_APPWIDGET = 102

    // Palette
    private val COLOR_TRANSPARENT = Color.TRANSPARENT
    private val COLOR_SCRIM = Color.parseColor("#B3000000")
    private val COLOR_SHELF_BG = Color.parseColor("#28282B")
    private val COLOR_SHELF_INNER = Color.parseColor("#38383C")
    private val COLOR_WINDOW_BG = Color.parseColor("#1C1C1E")
    private val COLOR_WHITE = Color.parseColor("#FFFFFF")
    private val COLOR_TEXT = Color.parseColor("#F4F4F5")
    private val COLOR_MUTED = Color.parseColor("#A1A1AA")

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

    // Drawer Overlay
    private lateinit var drawerScrimLayer: FrameLayout
    private lateinit var drawerWindowSheet: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerTitleTv: TextView

    // Bottom Alphabet Rail
    private lateinit var railContainer: LinearLayout
    private lateinit var floatingBadge: TextView
    private val alphabet = listOf('•') + ('A'..'Z').toList()
    private val railViews = mutableListOf<TextView>()
    private var lastHoverIndex = -1

    private var vibrator: Vibrator? = null

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
        try {
            appWidgetHost.startListening()
        } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            appWidgetHost.stopListening()
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
    }

    private fun pulseHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(10)
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
            setTextColor(COLOR_MUTED)
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

        // Drawer Overlay Window
        drawerScrimLayer = FrameLayout(this).apply {
            setBackgroundColor(COLOR_SCRIM)
            visibility = View.GONE
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { hideAppDrawer() }
        }

        drawerWindowSheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createCardDrawable(COLOR_WINDOW_BG, 32f)
            elevation = 40f
            setPadding(28, 20, 76, 24)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.75).toInt(),
                Gravity.BOTTOM
            )
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }

        val handleBar = View(this).apply {
            background = createCardDrawable(COLOR_SHELF_INNER, 6f)
            val lp = LinearLayout.LayoutParams(80, 8).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 16)
            }
            layoutParams = lp
        }
        drawerWindowSheet.addView(handleBar)

        drawerTitleTv = TextView(this).apply {
            text = "APPLICATIONS"
            setTextColor(COLOR_WHITE)
            textSize = 15f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 14)
        }
        drawerWindowSheet.addView(drawerTitleTv)

        drawerScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }
        drawerAppContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawerScroll.addView(drawerAppContainer)
        drawerWindowSheet.addView(drawerScroll)

        drawerScrimLayer.addView(drawerWindowSheet)
        rootLayout.addView(drawerScrimLayer)

        // Floating Magnifier
        floatingBadge = TextView(this).apply {
            visibility = View.GONE
            setTextColor(COLOR_WHITE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createCardDrawable(COLOR_SHELF_BG, 24f)
            elevation = 50f
            layoutParams = FrameLayout.LayoutParams(104, 104, Gravity.END or Gravity.TOP).apply {
                marginEnd = 76
            }
        }
        rootLayout.addView(floatingBadge)

        // Bottom-anchored Alphabet Rail
        val alphabetRail = buildAlphabetRail()
        rootLayout.addView(alphabetRail)

        setContentView(rootLayout)
        restoreSavedWidgets()
        refreshAll()
    }

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
            setPadding(28, 48, 72, 40)
        }

        val now = Calendar.getInstance()
        val dTv = TextView(this).apply {
            text = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now.time)
            setTextColor(COLOR_WHITE)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(dTv)

        val weatherTv = TextView(this).apply {
            text = "18°C"
            setTextColor(COLOR_WHITE)
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
            setTextColor(COLOR_MUTED)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
            background = createCardDrawable(COLOR_SHELF_BG, 18f)
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

        content.addView(createPixelSearchPill())

        scroll.addView(content)
        return scroll
    }

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
            setPadding(28, 48, 72, 48)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val title = TextView(this).apply {
            text = "WORKSPACE SHELVES"
            setTextColor(COLOR_WHITE)
            textSize = 15f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addShelfBtn = TextView(this).apply {
            text = "[ + SHELF ]"
            setTextColor(COLOR_MUTED)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(10, 6, 10, 6)
            setOnClickListener { showAddShelfDialog() }
        }
        headerRow.addView(title)
        headerRow.addView(addShelfBtn)
        content.addView(headerRow)

        shelvesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
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
                background = createCardDrawable(COLOR_SHELF_BG, 22f)
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
                setTextColor(COLOR_WHITE)
                textSize = 12f
                letterSpacing = 0.1f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val countTv = TextView(this).apply {
                text = "${shelf.packages.size}"
                setTextColor(COLOR_MUTED)
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
                    setTextColor(COLOR_MUTED)
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
            setTextColor(COLOR_WHITE)
            setHintTextColor(COLOR_MUTED)
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
                try {
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                } catch (_: Exception) {}
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
                    try {
                        appWidgetHost.deleteAppWidgetId(appWidgetId)
                    } catch (_: Exception) {}
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
                try {
                    appWidgetHost.deleteAppWidgetId(id)
                } catch (_: Exception) {}
            }
        }
        savedWidgetIds = validIds
        saveUserData()
    }

    private fun renderFavoritesGrid() {
        val grid = favoritesGridLayout ?: return
        grid.removeAllViews()
        val favs = allApps.filter { favoritePackages.contains(it.packageName) }.take(8)

        if (favs.isEmpty()) {
            resetFavoritesToSmartDefaults()
            return
        }

        val displayMetrics = resources.displayMetrics
        val availableWidth = displayMetrics.widthPixels - 140
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
                setTextColor(COLOR_TEXT)
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

    private fun createPixelSearchPill(): View {
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 14, 18, 14)
            background = createCardDrawable(COLOR_SHELF_BG, 28f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 2, 0, 0) }
            layoutParams = lp
        }

        val gIcon = TextView(this).apply {
            text = "G"
            setTextColor(COLOR_WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 14, 0)
        }
        searchBox.addView(gIcon)

        val sInput = EditText(this).apply {
            hint = "Search..."
            setHintTextColor(COLOR_MUTED)
            setTextColor(COLOR_WHITE)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun buildAlphabetRail(): View {
        railContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(56, 520, Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, 4, 40)
            }
            setPadding(0, 8, 2, 8)
        }

        railViews.clear()
        for (c in alphabet) {
            val tv = TextView(this).apply {
                text = c.toString()
                setTextColor(COLOR_MUTED)
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(2, 0, 2, 0)
            }
            railViews.add(tv)
            railContainer.addView(tv)
        }

        railContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val y = event.y.coerceIn(0f, railContainer.height.toFloat())
                    val normalized = y / railContainer.height
                    var targetIdx = (normalized * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)

                    var selectedChar = alphabet[targetIdx]

                    if (selectedChar != '•') {
                        val hasApps = allApps.any { it.name.startsWith(selectedChar, ignoreCase = true) }
                        if (!hasApps) {
                            val nextChar = alphabet.filter { c -> c != '•' && allApps.any { it.name.startsWith(c, ignoreCase = true) } }
                                .minByOrNull { Math.abs(alphabet.indexOf(it) - targetIdx) }
                            if (nextChar != null) {
                                selectedChar = nextChar
                                targetIdx = alphabet.indexOf(nextChar)
                            }
                        }
                    }

                    floatingBadge.visibility = View.VISIBLE
                    val badgeY = (event.rawY - 140).coerceIn(120f, (rootLayout.height - 240).toFloat())
                    floatingBadge.y = badgeY
                    floatingBadge.text = selectedChar.toString()

                    for (i in railViews.indices) {
                        if (i == targetIdx) {
                            railViews[i].setTextColor(COLOR_WHITE)
                            railViews[i].setTypeface(Typeface.DEFAULT_BOLD)
                        } else {
                            railViews[i].setTextColor(COLOR_MUTED)
                            railViews[i].setTypeface(Typeface.DEFAULT)
                        }
                    }

                    if (targetIdx != lastHoverIndex) {
                        lastHoverIndex = targetIdx
                        pulseHaptic()
                        if (selectedChar == '•') hideAppDrawer()
                        else showAppDrawer(selectedChar)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    floatingBadge.visibility = View.GONE
                    for (v in railViews) {
                        v.setTextColor(COLOR_MUTED)
                        v.setTypeface(Typeface.DEFAULT)
                    }
                    lastHoverIndex = -1
                    true
                }
                else -> true
            }
        }
        return railContainer
    }

    private fun showAppDrawer(filterChar: Char? = null) {
        if (drawerScrimLayer.visibility != View.VISIBLE) {
            drawerScrimLayer.visibility = View.VISIBLE
            drawerWindowSheet.translationY = 600f
            drawerScrimLayer.animate().alpha(1f).setDuration(140).start()
            drawerWindowSheet.animate().translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        }
        filterAndRenderDrawer(filterChar)
    }

    private fun hideAppDrawer() {
        if (drawerScrimLayer.visibility == View.VISIBLE) {
            drawerWindowSheet.animate().translationY(600f).setDuration(160).start()
            drawerScrimLayer.animate().alpha(0f).setDuration(160).withEndAction {
                drawerScrimLayer.visibility = View.GONE
            }.start()
        }
    }

    private fun filterAndRenderDrawer(filterChar: Char?) {
        drawerAppContainer.removeAllViews()
        val filtered = if (filterChar == null) allApps else allApps.filter { it.name.startsWith(filterChar, ignoreCase = true) }

        drawerTitleTv.text = if (filterChar != null) "APPLICATIONS [$filterChar]" else "ALL APPLICATIONS"

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No tools starting with '$filterChar'"
                setTextColor(COLOR_MUTED)
                textSize = 13f
                setPadding(0, 24, 0, 0)
            }
            drawerAppContainer.addView(emptyTv)
            return
        }

        for (app in filtered) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(12, 10, 12, 10)
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                val size = 84
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 16, 0) }
            }
            row.addView(icon)

            val name = TextView(this).apply {
                text = app.name
                setTextColor(COLOR_TEXT)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(name)

            row.setOnClickListener {
                pulseHaptic()
                launchApp(app.packageName)
            }
            row.setOnLongClickListener {
                pulseHaptic()
                showAppOptions(app)
                true
            }
            drawerAppContainer.addView(row)
        }
        drawerScroll.smoothScrollTo(0, 0)
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

    private fun createCardDrawable(bgColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radius
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerScrimLayer.visibility == View.VISIBLE) {
            hideAppDrawer()
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
}
