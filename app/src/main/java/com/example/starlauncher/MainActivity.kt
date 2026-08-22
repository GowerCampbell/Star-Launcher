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
import android.graphics.drawable.BitmapDrawable
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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val gson = Gson()
    private val PREFS = "PixelStationWidgetPrefs"
    private val KEY_FAVS = "key_pinned_favs_v3"
    private val KEY_WIDGET_IDS = "key_widget_ids"

    private val APPWIDGET_HOST_ID = 2048
    private val REQUEST_PICK_APPWIDGET = 101
    private val REQUEST_CREATE_APPWIDGET = 102

    // Palette
    private val COLOR_TRANSPARENT = Color.TRANSPARENT
    private val COLOR_SCRIM = Color.parseColor("#B3000000")
    private val COLOR_SHELF_BG = Color.parseColor("#28282B")
    private val COLOR_SHELF_INNER = Color.parseColor("#3F4045")
    private val COLOR_WINDOW_BG = Color.parseColor("#1C1C1E")
    private val COLOR_WHITE = Color.parseColor("#FFFFFF")
    private val COLOR_TEXT = Color.parseColor("#E4E4E7")
    private val COLOR_MUTED = Color.parseColor("#9CA3AF")

    data class AppItem(val name: String, val packageName: String, val rawIcon: Drawable, val themedIcon: Drawable)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var savedWidgetIds = mutableListOf<Int>()

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private lateinit var rootLayout: FrameLayout
    private lateinit var widgetContainer: LinearLayout
    private lateinit var favoritesGridLayout: GridLayout

    // Drawer Overlay Window
    private lateinit var drawerScrimLayer: FrameLayout
    private lateinit var drawerWindowSheet: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerTitleTv: TextView

    // Alphabet Rail
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
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
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

        favoritePackages = if (favJson != null) gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type) else mutableListOf()
        savedWidgetIds = if (widgetJson != null) gson.fromJson(widgetJson, object : TypeToken<MutableList<Int>>() {}.type) else mutableListOf()
    }

    private fun saveUserData() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, gson.toJson(favoritePackages))
            .putString(KEY_WIDGET_IDS, gson.toJson(savedWidgetIds))
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

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 76, 32)
        }

        // 1. Pixel At-a-Glance Top
        val now = Calendar.getInstance()
        val dTv = TextView(this).apply {
            text = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now.time)
            setTextColor(COLOR_WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(dTv)

        val weatherRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4, 0, 16)
        }
        val weatherTv = TextView(this).apply {
            text = "18°C"
            setTextColor(COLOR_WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        weatherRow.addView(weatherTv)
        content.addView(weatherRow)

        // 2. Dynamic Widget Slot Container
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
            setPadding(16, 14, 16, 14)
            background = createCardDrawable(COLOR_SHELF_BG, 20f)
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

        // 3. 4-Column Pinned App Grid
        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 16) }
            layoutParams = lp
        }
        favoritesGridLayout = grid
        content.addView(grid)

        // 4. Pixel Bottom Search Pill
        content.addView(createPixelSearchPill())

        scroll.addView(content)
        rootLayout.addView(scroll)

        // ================= APP DRAWER WINDOW SHEET =================
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
            setPadding(32, 24, 76, 24)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.78).toInt(),
                Gravity.BOTTOM
            )
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }

        val handleBar = View(this).apply {
            background = createCardDrawable(COLOR_SHELF_INNER, 6f)
            val lp = LinearLayout.LayoutParams(90, 10).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 18)
            }
            layoutParams = lp
        }
        drawerWindowSheet.addView(handleBar)

        drawerTitleTv = TextView(this).apply {
            text = "APPLICATIONS"
            setTextColor(COLOR_WHITE)
            textSize = 16f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
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

        // ================= FLOATING MAGNIFIER =================
        floatingBadge = TextView(this).apply {
            visibility = View.GONE
            setTextColor(COLOR_WHITE)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = createCardDrawable(COLOR_SHELF_BG, 28f)
            elevation = 50f
            layoutParams = FrameLayout.LayoutParams(116, 116, Gravity.END or Gravity.TOP).apply {
                marginEnd = 80
            }
        }
        rootLayout.addView(floatingBadge)

        // ================= ALPHABET RAIL =================
        val alphabetRail = buildAlphabetRail()
        rootLayout.addView(alphabetRail)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            content.setPadding(32, statusBarHeight + 16, 76, 32)
            insets
        }

        setContentView(rootLayout)
        restoreSavedWidgets()
        refreshAll()
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
                    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                        ?: return
                    configureOrBindWidget(appWidgetId)
                }
                REQUEST_CREATE_APPWIDGET -> {
                    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                        ?: return
                    attachWidgetView(appWidgetId)
                }
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
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
            ).apply { setMargins(0, 0, 0, 12) }
            layoutParams = lp
        }

        hostView.setOnLongClickListener {
            pulseHaptic()
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Remove Widget")
                .setMessage("Remove this widget from the station?")
                .setPositiveButton("Remove") { _, _ ->
                    appWidgetHost.deleteAppWidgetId(appWidgetId)
                    savedWidgetIds.remove(appWidgetId)
                    saveUserData()
                    widgetContainer.removeView(hostView)
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        widgetContainer.addView(hostView)
        if (!savedWidgetIds.contains(appWidgetId)) {
            savedWidgetIds.add(appWidgetId)
            saveUserData()
        }
    }

    private fun restoreSavedWidgets() {
        widgetContainer.removeAllViews()
        val validIds = mutableListOf<Int>()
        for (id in savedWidgetIds) {
            val info = appWidgetManager.getAppWidgetInfo(id)
            if (info != null) {
                attachWidgetView(id)
                validIds.add(id)
            } else {
                appWidgetHost.deleteAppWidgetId(id)
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
                setPadding(4, 8, 4, 8)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = tileWidth
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(2, 4, 2, 4)
                }
                isClickable = true
                isFocusable = true
            }

            val iconFrame = FrameLayout(this).apply {
                background = createCardDrawable(COLOR_SHELF_BG, 26f)
                val size = 96
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 0, 6) }
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.themedIcon)
                val innerSize = 58
                layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
            }
            iconFrame.addView(icon)
            tile.addView(iconFrame)

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
            setPadding(20, 14, 20, 14)
            background = createCardDrawable(COLOR_SHELF_BG, 28f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 4, 0, 0) }
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
            layoutParams = FrameLayout.LayoutParams(60, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, 6, 60)
            }
            setPadding(0, 12, 4, 12)
        }

        railViews.clear()
        for (c in alphabet) {
            val tv = TextView(this).apply {
                text = c.toString()
                setTextColor(COLOR_MUTED)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(4, 2, 4, 2)
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
                    val badgeY = (event.rawY - 160).coerceIn(120f, (rootLayout.height - 240).toFloat())
                    floatingBadge.y = badgeY
                    floatingBadge.text = selectedChar.toString()

                    for (i in railViews.indices) {
                        if (i == targetIdx) {
                            railViews[i].setTextColor(COLOR_WHITE)
                            railViews[i].setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                        } else {
                            railViews[i].setTextColor(COLOR_MUTED)
                            railViews[i].setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
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
                        v.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
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
                setPadding(12, 12, 12, 12)
                isClickable = true
                isFocusable = true
            }

            val iconFrame = FrameLayout(this).apply {
                background = createCardDrawable(COLOR_SHELF_INNER, 20f)
                val size = 80
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 16, 0) }
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.themedIcon)
                val innerSize = 48
                layoutParams = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
            }
            iconFrame.addView(icon)
            row.addView(iconFrame)

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
        Toast.makeText(this@MainActivity, "Pixel tools organized", Toast.LENGTH_SHORT).show()
    }

    private fun sanitizeSavedData() {
        val installed = allApps.map { it.packageName }.toSet()
        val favChanged = favoritePackages.retainAll(installed)
        if (favChanged) saveUserData()
    }

    private fun refreshAll() {
        allApps = loadInstalledApps()
        sanitizeSavedData()
        renderFavoritesGrid()
    }

    private fun showAppOptions(app: AppItem) {
        val isFav = favoritePackages.contains(app.packageName)
        val favLabel = if (isFav) "Unpin from Pixel Grid" else "Pin to Pixel Grid (Max 8)"

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(arrayOf(favLabel)) { _, which ->
                if (which == 0) {
                    if (isFav) favoritePackages.remove(app.packageName)
                    else if (favoritePackages.size < 8) favoritePackages.add(app.packageName)
                    else Toast.makeText(this@MainActivity, "Pixel Grid full (Max 8)", Toast.LENGTH_SHORT).show()
                    saveUserData()
                    renderFavoritesGrid()
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

    private fun applyPixelMonochromeFilter(drawable: Drawable): Drawable {
        val bitmap = try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(64),
                    drawable.intrinsicHeight.coerceAtLeast(64),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        } catch (_: Exception) {
            return drawable
        }

        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            val scale = 1.3f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, 30f,
                        0f, scale, 0f, 0f, 30f,
                        0f, 0f, scale, 0f, 30f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return BitmapDrawable(resources, output)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerScrimLayer.visibility == View.VISIBLE) {
            hideAppDrawer()
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
            val themedIcon = applyPixelMonochromeFilter(rawIcon)
            list.add(AppItem(label, pName, rawIcon, themedIcon))
        }
        return list.sortedBy { it.name.lowercase() }
    }
}
