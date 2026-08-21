package com.example.starlauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MainActivity : Activity() {

    private val APPWIDGET_HOST_ID = 2048
    private val REQUEST_PICK_APPWIDGET = 1001
    private val REQUEST_CREATE_APPWIDGET = 1002

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var widgetContainer: LinearLayout

    private lateinit var mainScroll: ScrollView
    private lateinit var appContainer: LinearLayout
    private lateinit var favoritesContainer: LinearLayout
    private lateinit var foldersContainer: LinearLayout

    private val gson = Gson()
    private val PREFS_NAME = "GrimoirePrefs"
    private val KEY_FAVORITES = "fav_apps"
    private val KEY_FOLDERS = "tome_folders"

    // High readability cosmic palette
    private val COLOR_BG = Color.parseColor("#080B10")       // Deep abyssal black
    private val COLOR_SURFACE = Color.parseColor("#121924")  // Vault dark slate
    private val COLOR_ACCENT = Color.parseColor("#10B988")   // Eldritch jade
    private val COLOR_AMBER = Color.parseColor("#F59E0B")    // Arcane gold
    private val COLOR_TEXT = Color.parseColor("#F1F5F9")     // High contrast dyslexia-safe white
    private val COLOR_MUTED = Color.parseColor("#94A3B8")    // Muted parchment silver

    data class AppItem(val name: String, val packageName: String, val icon: Drawable?)
    data class FolderItem(val name: String, val glyph: String, val packages: MutableList<String>)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var folders = mutableListOf<FolderItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        appWidgetHost.startListening()

        loadUserData()
        buildUi()
    }

    private fun loadUserData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val favJson = prefs.getString(KEY_FAVORITES, null)
        val folderJson = prefs.getString(KEY_FOLDERS, null)

        favoritePackages = if (favJson != null) {
            gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type)
        } else {
            mutableListOf()
        }

        folders = if (folderJson != null) {
            gson.fromJson(folderJson, object : TypeToken<MutableList<FolderItem>>() {}.type)
        } else {
            mutableListOf(
                FolderItem("Tome of Comms", "🜏", mutableListOf()),
                FolderItem("Vault of Tools", "🜂", mutableListOf())
            )
        }
    }

    private fun saveUserData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FAVORITES, gson.toJson(favoritePackages))
            .putString(KEY_FOLDERS, gson.toJson(folders))
            .apply()
    }

    private fun buildUi() {
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        mainScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 80, 80, 96) // Extra right padding for the alphabet bar
        }

        // --- HEADER ---
        val header = TextView(this).apply {
            text = "🜏  S T A R  L A U N C H E R"
            setTextColor(COLOR_ACCENT)
            textSize = 18f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 16)
        }
        contentLayout.addView(header)

        // --- WIDGET CONTAINER ---
        widgetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 32)
            setOnLongClickListener {
                selectWidget()
                true
            }
        }
        val widgetHint = TextView(this).apply {
            text = "[ Hold to invoke Widget Shrine ]"
            setTextColor(COLOR_MUTED)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8, 0, 16)
        }
        widgetContainer.addView(widgetHint)
        contentLayout.addView(widgetContainer)

        // --- FAVORITES: "THE INNER CIRCLE" (MAX 6) ---
        val favHeader = TextView(this).apply {
            text = "★ INNER CIRCLE (FAVORITES)"
            setTextColor(COLOR_AMBER)
            textSize = 13f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 16)
        }
        contentLayout.addView(favHeader)

        favoritesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(favoritesContainer)

        // --- TOMES / FOLDERS ---
        val folderHeader = TextView(this).apply {
            text = "🕮 ARCHIVES & TOMES"
            setTextColor(COLOR_AMBER)
            textSize = 13f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 32, 0, 16)
        }
        contentLayout.addView(folderHeader)

        foldersContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(foldersContainer)

        // --- ALL APPS STREAM ---
        val streamHeader = TextView(this).apply {
            text = "👁 ALL SANCTIONED APPS"
            setTextColor(COLOR_ACCENT)
            textSize = 13f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 32, 0, 16)
        }
        contentLayout.addView(streamHeader)

        appContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(appContainer)

        mainScroll.addView(contentLayout)
        rootLayout.addView(mainScroll)

        // --- NIAGARA-STYLE ALPHABET SCROLLER ---
        val alphabetLayout = buildAlphabetScroller()
        rootLayout.addView(alphabetLayout)

        setContentView(rootLayout)
        refreshAppList()
    }

    private fun refreshAppList() {
        allApps = loadInstalledApps()
        renderFavorites()
        renderFolders()
        renderAllApps()
    }

    private fun renderFavorites() {
        favoritesContainer.removeAllViews()
        val favApps = allApps.filter { favoritePackages.contains(it.packageName) }.take(6)

        if (favApps.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "> No bindings. Long-press any app below to bind (Max 6)."
                setTextColor(COLOR_MUTED)
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            favoritesContainer.addView(emptyTv)
            return
        }

        for (app in favApps) {
            favoritesContainer.addView(createAppRowView(app, isFavoriteSection = true))
        }
    }

    private fun renderFolders() {
        foldersContainer.removeAllViews()
        for (folder in folders) {
            val folderBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
                background = GradientDrawable().apply {
                    setColor(COLOR_SURFACE)
                    cornerRadius = 16f
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
                layoutParams = lp
            }

            val titleView = TextView(this).apply {
                text = "${folder.glyph}  ${folder.name} (${folder.packages.size})"
                setTextColor(COLOR_TEXT)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            folderBox.addView(titleView)

            folderBox.setOnClickListener {
                showFolderDialog(folder)
            }

            foldersContainer.addView(folderBox)
        }
    }

    private fun renderAllApps() {
        appContainer.removeAllViews()
        for (app in allApps) {
            appContainer.addView(createAppRowView(app, isFavoriteSection = false))
        }
    }

    private fun createAppRowView(app: AppItem, isFavoriteSection: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 24) // Extra tap surface for motor precision
            isClickable = true
            isFocusable = true
        }

        val iconView = ImageView(this).apply {
            setImageDrawable(app.icon)
            val iconSize = 96
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                setMargins(0, 0, 32, 0)
            }
        }
        row.addView(iconView)

        val nameView = TextView(this).apply {
            text = app.name
            setTextColor(COLOR_TEXT)
            textSize = 16f
            letterSpacing = 0.05f
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(nameView)

        row.setOnClickListener {
            launchApp(app.packageName)
        }

        row.setOnLongClickListener {
            showAppOptions(app)
            true
        }

        return row
    }

    private fun showAppOptions(app: AppItem) {
        val isFav = favoritePackages.contains(app.packageName)
        val favLabel = if (isFav) "Remove from Inner Circle" else "Bind to Inner Circle (Max 6)"

        val options = mutableListOf(favLabel, "Add to Tome/Folder")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Ritual: ${app.name}")
        builder.setItems(options.toTypedArray()) { _, which ->
            when (which) {
                0 -> {
                    if (isFav) {
                        favoritePackages.remove(app.packageName)
                    } else {
                        if (favoritePackages.size < 6) {
                            favoritePackages.add(app.packageName)
                        } else {
                            Toast.makeText(this, "The Circle is full (Max 6)", Toast.LENGTH_SHORT).show()
                        }
                    }
                    saveUserData()
                    renderFavorites()
                }
                1 -> showAddToFolderDialog(app)
            }
        }
        builder.show()
    }

    private fun showAddToFolderDialog(app: AppItem) {
        val folderNames = folders.map { "${it.glyph} ${it.name}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Assign to Archive")
            .setItems(folderNames) { _, which ->
                val target = folders[which]
                if (!target.packages.contains(app.packageName)) {
                    target.packages.add(app.packageName)
                    saveUserData()
                    renderFolders()
                    Toast.makeText(this, "Bound to ${target.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showFolderDialog(folder: FolderItem) {
        val folderApps = allApps.filter { folder.packages.contains(it.packageName) }
        val names = folderApps.map { it.name }.toTypedArray()

        val builder = AlertDialog.Builder(this)
        builder.setTitle("${folder.glyph} ${folder.name}")
        if (names.isEmpty()) {
            builder.setMessage("This Tome is empty. Long press apps to seal them here.")
        } else {
            builder.setItems(names) { _, which ->
                launchApp(folderApps[which].packageName)
            }
        }
        builder.setPositiveButton("Close", null)
        builder.show()
    }

    private fun buildAlphabetScroller(): View {
        val alphabetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                60,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
            setPadding(0, 100, 16, 100)
        }

        val chars = ('A'..'Z').toList()
        for (c in chars) {
            val charTv = TextView(this).apply {
                text = c.toString()
                setTextColor(COLOR_MUTED)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(4, 2, 4, 2)
                setOnClickListener {
                    jumpToLetter(c)
                }
            }
            alphabetLayout.addView(charTv)
        }
        return alphabetLayout
    }

    private fun jumpToLetter(letter: Char) {
        val index = allApps.indexOfFirst { it.name.startsWith(letter, ignoreCase = true) }
        if (index != -1) {
            val targetView = appContainer.getChildAt(index)
            if (targetView != null) {
                mainScroll.smoothScrollTo(0, targetView.top + appContainer.top)
            }
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Portal closed: Cannot open", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Invocation failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectWidget() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_PICK_APPWIDGET -> {
                    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                    if (appWidgetId != -1) configureWidget(appWidgetId)
                }
                REQUEST_CREATE_APPWIDGET -> {
                    val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                    if (appWidgetId != -1) attachWidget(appWidgetId)
                }
            }
        } else if (requestCode == REQUEST_PICK_APPWIDGET && data != null) {
            val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (appWidgetId != -1) appWidgetHost.deleteAppWidgetId(appWidgetId)
        }
    }

    private fun configureWidget(appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = appWidgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
        } else {
            attachWidget(appWidgetId)
        }
    }

    private fun attachWidget(appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val hostView: AppWidgetHostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo)
        hostView.setAppWidget(appWidgetId, appWidgetInfo)
        widgetContainer.removeAllViews()
        widgetContainer.addView(hostView)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        appWidgetHost.stopListening()
    }

    private fun loadInstalledApps(): List<AppItem> {
        val list = mutableListOf<AppItem>()
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = packageManager.queryIntentActivities(intent, 0)
        for (info in resolved) {
            val pName = info.activityInfo?.packageName ?: continue
            if (pName == packageName) continue
            val label = info.loadLabel(packageManager)?.toString() ?: pName
            val icon = info.loadIcon(packageManager)
            list.add(AppItem(label, pName, icon))
        }
        return list.sortedBy { it.name.lowercase() }
    }
}
