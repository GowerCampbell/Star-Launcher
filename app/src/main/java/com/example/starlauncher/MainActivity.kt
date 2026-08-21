package com.example.starlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val gson = Gson()
    private val PREFS = "StarLauncherStation"
    private val KEY_FAVS = "key_favs"
    private val KEY_FOLDERS = "key_folders"
    private val KEY_TASKS = "key_tasks"

    // High-readability cosmic palette
    private val BG_COLOR = Color.parseColor("#080C10")
    private val SURFACE_CARD = Color.parseColor("#121820")
    private val ACCENT_JADE = Color.parseColor("#10B981")
    private val ACCENT_EMBER = Color.parseColor("#F59E0B")
    private val TEXT_PRIMARY = Color.parseColor("#F8FAFC")
    private val TEXT_MUTED = Color.parseColor("#94A3B8")

    data class AppItem(val name: String, val packageName: String, val icon: Drawable?)
    data class FolderItem(val name: String, val glyph: String, val packages: MutableList<String>)
    data class TaskItem(val id: Long, val text: String, var isDone: Boolean)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var folders = mutableListOf<FolderItem>()
    private var taskList = mutableListOf<TaskItem>()

    private lateinit var homeStationLayout: LinearLayout
    private lateinit var drawerLayout: LinearLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerHeaderTv: TextView
    private lateinit var favoritesContainer: LinearLayout
    private lateinit var foldersContainer: LinearLayout
    private lateinit var tasksContainer: LinearLayout

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        loadData()
        buildStationUi()
    }

    private fun haptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    private fun loadData() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val favJson = p.getString(KEY_FAVS, null)
        val folderJson = p.getString(KEY_FOLDERS, null)
        val taskJson = p.getString(KEY_TASKS, null)

        favoritePackages = if (favJson != null) {
            gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type)
        } else {
            mutableListOf()
        }

        folders = if (folderJson != null) {
            gson.fromJson(folderJson, object : TypeToken<MutableList<FolderItem>>() {}.type)
        } else {
            mutableListOf(
                FolderItem("Grimoire & Readers", "🕮", mutableListOf()),
                FolderItem("Dev & Terminal Station", "⚙", mutableListOf()),
                FolderItem("Comms & Intel", "👁", mutableListOf())
            )
        }

        taskList = if (taskJson != null) {
            gson.fromJson(taskJson, object : TypeToken<MutableList<TaskItem>>() {}.type)
        } else {
            mutableListOf(
                TaskItem(1, "Configure Star Station", false),
                TaskItem(2, "Review Worldbuilding Codex", false)
            )
        }
    }

    private fun saveData() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, gson.toJson(favoritePackages))
            .putString(KEY_FOLDERS, gson.toJson(folders))
            .putString(KEY_TASKS, gson.toJson(taskList))
            .apply()
    }

    private fun buildStationUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(BG_COLOR)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ================= HOME STATION VIEW =================
        val stationScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        homeStationLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 88, 120)
        }

        homeStationLayout.addView(createGlanceWidget())
        homeStationLayout.addView(createMediaCapsule())
        homeStationLayout.addView(createTaskStation())

        val favLabel = TextView(this).apply {
            text = "★ INNER CIRCLE [FAVOURITES]"
            setTextColor(ACCENT_EMBER)
            textSize = 12f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 32, 0, 16)
        }
        homeStationLayout.addView(favLabel)

        favoritesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeStationLayout.addView(favoritesContainer)

        val foldLabel = TextView(this).apply {
            text = "🜏 ARCHIVE TOMES"
            setTextColor(ACCENT_JADE)
            textSize = 12f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 32, 0, 16)
        }
        homeStationLayout.addView(foldLabel)

        foldersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeStationLayout.addView(foldersContainer)

        stationScroll.addView(homeStationLayout)
        root.addView(stationScroll)

        // ================= APP DRAWER OVERLAY =================
        drawerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_COLOR)
            visibility = View.GONE
            alpha = 0f
            setPadding(48, 80, 88, 96)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        drawerHeaderTv = TextView(this).apply {
            text = "ALL SANCTIONED APPS"
            setTextColor(ACCENT_JADE)
            textSize = 18f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 24)
        }
        drawerLayout.addView(drawerHeaderTv)

        drawerScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }
        drawerAppContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawerScroll.addView(drawerAppContainer)
        drawerLayout.addView(drawerScroll)

        root.addView(drawerLayout)

        // ================= NIAGARA WAVE RAIL =================
        val alphabetRail = buildAlphabetRail()
        root.addView(alphabetRail)

        setContentView(root)
        refreshAll()
    }

    private fun createGlanceWidget(): View {
        val glanceCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = cardDrawable(SURFACE_CARD, 20f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 24)
            layoutParams = lp
        }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())

        val timeTv = TextView(this).apply {
            text = timeFormat.format(Date())
            setTextColor(TEXT_PRIMARY)
            textSize = 34f
            typeface = Typeface.MONOSPACE
        }

        val dateTv = TextView(this).apply {
            text = dateFormat.format(Date()).uppercase()
            setTextColor(ACCENT_JADE)
            textSize = 13f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 0)
        }

        glanceCard.addView(timeTv)
        glanceCard.addView(dateTv)
        return glanceCard
    }

    private fun createMediaCapsule(): View {
        val capsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 20, 28, 20)
            background = cardDrawable(SURFACE_CARD, 20f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 24)
            layoutParams = lp
        }

        val labelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(this).apply {
            text = "AUDIO DECK"
            setTextColor(ACCENT_EMBER)
            textSize = 11f
            letterSpacing = 0.1f
            typeface = Typeface.MONOSPACE
        }
        val sub = TextView(this).apply {
            text = "Tap to invoke Player"
            setTextColor(TEXT_MUTED)
            textSize = 13f
        }
        labelLayout.addView(title)
        labelLayout.addView(sub)
        capsule.addView(labelLayout)

        val playBtn = TextView(this).apply {
            text = "▶ / ⏸"
            setTextColor(TEXT_PRIMARY)
            textSize = 18f
            setPadding(24, 16, 24, 16)
            setOnClickListener {
                haptic()
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
            }
        }
        capsule.addView(playBtn)

        capsule.setOnClickListener {
            haptic()
            val spotifyIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (spotifyIntent != null) startActivity(spotifyIntent)
            else Toast.makeText(this, "Spotify not detected", Toast.LENGTH_SHORT).show()
        }

        return capsule
    }

    private fun createTaskStation(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = cardDrawable(SURFACE_CARD, 20f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 24)
            layoutParams = lp
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "DIRECTIVES & TASKS"
            setTextColor(ACCENT_JADE)
            textSize = 12f
            letterSpacing = 0.1f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addBtn = TextView(this).apply {
            text = "+ NEW"
            setTextColor(ACCENT_EMBER)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(16, 8, 16, 8)
            setOnClickListener { showAddTaskDialog() }
        }
        topRow.addView(title)
        topRow.addView(addBtn)
        card.addView(topRow)

        tasksContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }
        card.addView(tasksContainer)
        return card
    }

    private fun renderTasks() {
        tasksContainer.removeAllViews()
        for (task in taskList) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val check = TextView(this).apply {
                text = if (task.isDone) "▣" else "▢"
                setTextColor(if (task.isDone) ACCENT_JADE else TEXT_MUTED)
                textSize = 16f
                setPadding(0, 0, 20, 0)
            }
            val tv = TextView(this).apply {
                text = task.text
                setTextColor(if (task.isDone) TEXT_MUTED else TEXT_PRIMARY)
                textSize = 14f
                if (task.isDone) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            row.addView(check)
            row.addView(tv)

            row.setOnClickListener {
                haptic()
                task.isDone = !task.isDone
                saveData()
                renderTasks()
            }
            row.setOnLongClickListener {
                haptic()
                taskList.remove(task)
                saveData()
                renderTasks()
                true
            }
            tasksContainer.addView(row)
        }
    }

    private fun showAddTaskDialog() {
        val input = EditText(this).apply {
            hint = "Enter directive..."
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_MUTED)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Directive")
            .setView(input)
            .setPositiveButton("Engrave") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    taskList.add(TaskItem(System.currentTimeMillis(), txt, false))
                    saveData()
                    renderTasks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildAlphabetRail(): View {
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(72, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
            setPadding(0, 48, 8, 48)
        }

        val chars = listOf('★') + ('A'..'Z').toList()
        for (c in chars) {
            val tv = TextView(this).apply {
                text = c.toString()
                setTextColor(TEXT_MUTED)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(4, 2, 4, 2)
            }
            rail.addView(tv)
        }

        rail.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val childCount = rail.childCount
                    val y = event.y.coerceIn(0f, rail.height.toFloat())
                    val index = ((y / rail.height) * childCount).toInt().coerceIn(0, childCount - 1)
                    val letter = chars[index]
                    haptic()
                    if (letter == '★') {
                        hideAppDrawer()
                    } else {
                        showAppDrawer(letter)
                    }
                    true
                }
                else -> true
            }
        }
        return rail
    }

    private fun showAppDrawer(filterChar: Char? = null) {
        if (drawerLayout.visibility != View.VISIBLE) {
            drawerLayout.visibility = View.VISIBLE
            drawerLayout.animate().alpha(1f).setDuration(150).setInterpolator(DecelerateInterpolator()).start()
        }
        filterAndRenderDrawer(filterChar)
    }

    private fun hideAppDrawer() {
        if (drawerLayout.visibility == View.VISIBLE) {
            drawerLayout.animate().alpha(0f).setDuration(120).withEndAction {
                drawerLayout.visibility = View.GONE
            }.start()
        }
    }

    private fun filterAndRenderDrawer(filterChar: Char?) {
        drawerAppContainer.removeAllViews()
        val filtered = if (filterChar == null) allApps else allApps.filter { it.name.startsWith(filterChar, ignoreCase = true) }

        drawerHeaderTv.text = if (filterChar != null) "APPS // SECTOR $filterChar" else "ALL SANCTIONED APPS"

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No apps bound to sector '$filterChar'"
                setTextColor(TEXT_MUTED)
                textSize = 14f
                setPadding(0, 32, 0, 0)
            }
            drawerAppContainer.addView(emptyTv)
            return
        }

        for (app in filtered) {
            drawerAppContainer.addView(createAppRowView(app))
        }
    }

    private fun refreshAll() {
        allApps = loadInstalledApps()
        renderFavorites()
        renderFolders()
        renderTasks()
    }

    private fun renderFavorites() {
        favoritesContainer.removeAllViews()
        val favs = allApps.filter { favoritePackages.contains(it.packageName) }.take(6)
        if (favs.isEmpty()) {
            val hint = TextView(this).apply {
                text = "> Touch rail to open Apps. Long-press to bind to Circle."
                setTextColor(TEXT_MUTED)
                textSize = 13f
                setPadding(0, 8, 0, 8)
            }
            favoritesContainer.addView(hint)
            return
        }
        for (app in favs) {
            favoritesContainer.addView(createAppRowView(app))
        }
    }

    private fun renderFolders() {
        foldersContainer.removeAllViews()
        for (folder in folders) {
            val folderBox = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(28, 20, 28, 20)
                background = cardDrawable(SURFACE_CARD, 16f)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 6, 0, 6)
                layoutParams = lp
            }

            val icon = TextView(this).apply {
                text = folder.glyph
                textSize = 18f
                setTextColor(ACCENT_JADE)
                setPadding(0, 0, 24, 0)
            }
            val title = TextView(this).apply {
                text = "${folder.name} (${folder.packages.size})"
                setTextColor(TEXT_PRIMARY)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            folderBox.addView(icon)
            folderBox.addView(title)

            folderBox.setOnClickListener {
                haptic()
                showFolderDialog(folder)
            }
            foldersContainer.addView(folderBox)
        }
    }

    private fun createAppRowView(app: AppItem): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 20)
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            val size = 96
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 28, 0) }
        }
        row.addView(icon)

        val name = TextView(this).apply {
            text = app.name
            setTextColor(TEXT_PRIMARY)
            textSize = 15f
            letterSpacing = 0.04f
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(name)

        row.setOnClickListener {
            haptic()
            launchApp(app.packageName)
        }
        row.setOnLongClickListener {
            haptic()
            showAppOptions(app)
            true
        }
        return row
    }

    private fun showAppOptions(app: AppItem) {
        val isFav = favoritePackages.contains(app.packageName)
        val favLabel = if (isFav) "Remove from Inner Circle" else "Bind to Inner Circle (Max 6)"
        val opts = arrayOf(favLabel, "Assign to Archive/Tome")

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        if (isFav) favoritePackages.remove(app.packageName)
                        else if (favoritePackages.size < 6) favoritePackages.add(app.packageName)
                        else Toast.makeText(this, "Circle is full (Max 6)", Toast.LENGTH_SHORT).show()
                        saveData()
                        renderFavorites()
                    }
                    1 -> {
                        val names = folders.map { "${it.glyph} ${it.name}" }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Select Archive")
                            .setItems(names) { _, fIndex ->
                                val f = folders[fIndex]
                                if (!f.packages.contains(app.packageName)) {
                                    f.packages.add(app.packageName)
                                    saveData()
                                    renderFolders()
                                }
                            }.show()
                    }
                }
            }.show()
    }

    private fun showFolderDialog(folder: FolderItem) {
        val apps = allApps.filter { folder.packages.contains(it.packageName) }
        val names = apps.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${folder.glyph} ${folder.name}")
            .apply {
                if (names.isEmpty()) setMessage("Archive is empty. Long-press apps to assign them here.")
                else setItems(names) { _, w -> launchApp(apps[w].packageName) }
            }
            .setPositiveButton("Close", null)
            .show()
    }

    private fun launchApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Cannot open portal", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cardDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.visibility == View.VISIBLE) {
            hideAppDrawer()
        } else {
            // Stay on home screen
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
