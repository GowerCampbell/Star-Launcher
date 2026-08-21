package com.example.starlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
    private val PREFS = "WorkstationLauncherPrefs"
    private val KEY_FAVS = "key_pinned"
    private val KEY_FOLDERS = "key_folders"
    private val KEY_TASKS = "key_tasks"

    // High-readability workstation palette
    private val COLOR_BG = Color.parseColor("#0A0E14")          // Deep matte charcoal
    private val COLOR_SURFACE = Color.parseColor("#121820")     // Structured card surface
    private val COLOR_ACCENT = Color.parseColor("#10B981")      // Focused jade green
    private val COLOR_SECONDARY = Color.parseColor("#38BDF8")   // Steel cyan
    private val COLOR_TEXT = Color.parseColor("#E2E8F0")        // Dyslexia-safe bone white
    private val COLOR_MUTED = Color.parseColor("#64748B")       // Balanced slate gray
    private val COLOR_BORDER = Color.parseColor("#1E293B")      // Card rim definition

    data class AppItem(val name: String, val packageName: String, val icon: Drawable?)
    data class FolderItem(val name: String, val packages: MutableList<String>)
    data class TaskItem(val id: Long, val text: String, var isDone: Boolean)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var folders = mutableListOf<FolderItem>()
    private var taskList = mutableListOf<TaskItem>()

    private lateinit var rootLayout: FrameLayout
    private lateinit var homeStationLayout: LinearLayout
    private lateinit var drawerLayout: FrameLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerTitleTv: TextView
    private lateinit var favoritesContainer: LinearLayout
    private lateinit var foldersContainer: LinearLayout
    private lateinit var tasksContainer: LinearLayout

    private lateinit var timeTv: TextView
    private lateinit var dateTv: TextView

    // Ergonomic Alphabet Rail
    private lateinit var railContainer: LinearLayout
    private lateinit var floatingBadge: TextView
    private val alphabet = listOf('•') + ('A'..'Z').toList()
    private val railViews = mutableListOf<TextView>()
    private var lastHoverIndex = -1

    private var vibrator: Vibrator? = null

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClock()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    override fun onResume() {
        super.onResume()
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        updateClock()
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(timeTickReceiver)
        } catch (_: Exception) {}
    }

    private fun updateClock() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        if (::timeTv.isInitialized) timeTv.text = timeFormat.format(Date())
        if (::dateTv.isInitialized) dateTv.text = dateFormat.format(Date()).uppercase()
    }

    private fun pulseHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(12)
            }
        } catch (_: Exception) {}
    }

    private fun loadUserData() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val favJson = p.getString(KEY_FAVS, null)
        val folderJson = p.getString(KEY_FOLDERS, null)
        val taskJson = p.getString(KEY_TASKS, null)

        favoritePackages = if (favJson != null) gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type) else mutableListOf()
        folders = if (folderJson != null) {
            gson.fromJson(folderJson, object : TypeToken<MutableList<FolderItem>>() {}.type)
        } else {
            mutableListOf(
                FolderItem("Writing & Notes", mutableListOf()),
                FolderItem("Dev & Tools", mutableListOf()),
                FolderItem("Reading & Media", mutableListOf())
            )
        }
        taskList = if (taskJson != null) {
            gson.fromJson(taskJson, object : TypeToken<MutableList<TaskItem>>() {}.type)
        } else {
            mutableListOf(
                TaskItem(1, "Worldbuilding notes outline", false),
                TaskItem(2, "Review codebase changes", false)
            )
        }
    }

    private fun saveUserData() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, gson.toJson(favoritePackages))
            .putString(KEY_FOLDERS, gson.toJson(folders))
            .putString(KEY_TASKS, gson.toJson(taskList))
            .apply()
    }

    private fun buildInterface() {
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ================= HOME DASHBOARD =================
        val stationScroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        homeStationLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 96, 120)
        }

        // 1. Clock & Date Display
        homeStationLayout.addView(createClockWidget())

        // 2. Audio Control Deck
        homeStationLayout.addView(createMediaWidget())

        // 3. Quick Tasks / Directives
        homeStationLayout.addView(createTaskWidget())

        // 4. Pinned Applications (Max 6)
        val pinnedHeader = TextView(this).apply {
            text = "PINNED"
            setTextColor(COLOR_SECONDARY)
            textSize = 12f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 32, 0, 16)
        }
        homeStationLayout.addView(pinnedHeader)

        favoritesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeStationLayout.addView(favoritesContainer)

        // 5. Folders / Workspaces
        val folderHeader = TextView(this).apply {
            text = "FOLDERS"
            setTextColor(COLOR_ACCENT)
            textSize = 12f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 32, 0, 16)
        }
        homeStationLayout.addView(folderHeader)

        foldersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeStationLayout.addView(foldersContainer)

        stationScroll.addView(homeStationLayout)
        rootLayout.addView(stationScroll)

        // ================= APP DRAWER (OVERLAY) =================
        drawerLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            visibility = View.GONE
            alpha = 0f
            setPadding(48, 72, 96, 96)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val drawerContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        drawerTitleTv = TextView(this).apply {
            text = "APPLICATIONS"
            setTextColor(COLOR_ACCENT)
            textSize = 16f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 20)
        }
        drawerContent.addView(drawerTitleTv)

        drawerScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }
        drawerAppContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawerScroll.addView(drawerAppContainer)
        drawerContent.addView(drawerScroll)

        drawerLayout.addView(drawerContent)
        rootLayout.addView(drawerLayout)

        // ================= FLOATING LETTER MAGNIFIER BADGE =================
        floatingBadge = TextView(this).apply {
            visibility = View.GONE
            setTextColor(COLOR_TEXT)
            textSize = 28f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = createCardDrawable(COLOR_SURFACE, 28f, COLOR_ACCENT)
            layoutParams = FrameLayout.LayoutParams(130, 130, Gravity.END or Gravity.TOP).apply {
                marginEnd = 110
            }
            elevation = 16f
        }
        rootLayout.addView(floatingBadge)

        // ================= ALPHABET SCROLL RAIL =================
        val alphabetRail = buildAlphabetRail()
        rootLayout.addView(alphabetRail)

        setContentView(rootLayout)
        refreshAll()
    }

    private fun createClockWidget(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = createCardDrawable(COLOR_SURFACE, 18f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 20)
            layoutParams = lp
        }

        timeTv = TextView(this).apply {
            setTextColor(COLOR_TEXT)
            textSize = 36f
            letterSpacing = 0.04f
            typeface = Typeface.MONOSPACE
        }

        dateTv = TextView(this).apply {
            setTextColor(COLOR_ACCENT)
            textSize = 12f
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 0)
        }

        card.addView(timeTv)
        card.addView(dateTv)
        updateClock()
        return card
    }

    private fun createMediaWidget(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 20, 28, 20)
            background = createCardDrawable(COLOR_SURFACE, 18f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 20)
            layoutParams = lp
        }

        val labelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(this).apply {
            text = "AUDIO"
            setTextColor(COLOR_SECONDARY)
            textSize = 11f
            letterSpacing = 0.1f
            typeface = Typeface.MONOSPACE
        }
        val sub = TextView(this).apply {
            text = "Open Spotify"
            setTextColor(COLOR_MUTED)
            textSize = 13f
        }
        labelLayout.addView(title)
        labelLayout.addView(sub)
        card.addView(labelLayout)

        val playBtn = TextView(this).apply {
            text = "▶  ⏸"
            setTextColor(COLOR_TEXT)
            textSize = 15f
            setPadding(24, 16, 24, 16)
            setOnClickListener {
                pulseHaptic()
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
            }
        }
        card.addView(playBtn)

        card.setOnClickListener {
            pulseHaptic()
            val spotifyIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (spotifyIntent != null) startActivity(spotifyIntent)
            else Toast.makeText(this, "Spotify not installed", Toast.LENGTH_SHORT).show()
        }

        return card
    }

    private fun createTaskWidget(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = createCardDrawable(COLOR_SURFACE, 18f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 20)
            layoutParams = lp
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "DIRECTIVES"
            setTextColor(COLOR_ACCENT)
            textSize = 11f
            letterSpacing = 0.1f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addBtn = TextView(this).apply {
            text = "+ ADD"
            setTextColor(COLOR_SECONDARY)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(16, 8, 16, 8)
            setOnClickListener { showAddTaskDialog() }
        }
        topRow.addView(title)
        topRow.addView(addBtn)
        card.addView(topRow)

        tasksContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14, 0, 0)
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
                setPadding(0, 10, 0, 10)
            }
            val check = TextView(this).apply {
                text = if (task.isDone) "■" else "□"
                setTextColor(if (task.isDone) COLOR_ACCENT else COLOR_MUTED)
                textSize = 14f
                setPadding(0, 0, 18, 0)
            }
            val tv = TextView(this).apply {
                text = task.text
                setTextColor(if (task.isDone) COLOR_MUTED else COLOR_TEXT)
                textSize = 14f
                if (task.isDone) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            row.addView(check)
            row.addView(tv)

            row.setOnClickListener {
                pulseHaptic()
                task.isDone = !task.isDone
                saveUserData()
                renderTasks()
            }
            row.setOnLongClickListener {
                pulseHaptic()
                taskList.remove(task)
                saveUserData()
                renderTasks()
                true
            }
            tasksContainer.addView(row)
        }
    }

    private fun showAddTaskDialog() {
        val input = EditText(this).apply {
            hint = "New item..."
            setTextColor(COLOR_TEXT)
            setHintTextColor(COLOR_MUTED)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Directive")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    taskList.add(TaskItem(System.currentTimeMillis(), txt, false))
                    saveUserData()
                    renderTasks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================= ALPHABET RAIL + FLOATING BADGE =================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildAlphabetRail(): View {
        railContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(64, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
            setPadding(0, 56, 12, 56)
        }

        railViews.clear()
        for (c in alphabet) {
            val tv = TextView(this).apply {
                text = c.toString()
                setTextColor(COLOR_MUTED)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(4, 1, 4, 1)
            }
            railViews.add(tv)
            railContainer.addView(tv)
        }

        railContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val y = event.y.coerceIn(0f, railContainer.height.toFloat())
                    val normalized = y / railContainer.height
                    val targetIdx = (normalized * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)

                    // Position the floating badge vertically centered on touch
                    floatingBadge.visibility = View.VISIBLE
                    val badgeY = (event.rawY - 180).coerceIn(120f, (rootLayout.height - 240).toFloat())
                    floatingBadge.y = badgeY

                    val selectedChar = alphabet[targetIdx]
                    floatingBadge.text = selectedChar.toString()

                    // Highlight rail element cleanly without enlarging its frame
                    for (i in railViews.indices) {
                        if (i == targetIdx) {
                            railViews[i].setTextColor(COLOR_ACCENT)
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
        if (drawerLayout.visibility != View.VISIBLE) {
            drawerLayout.visibility = View.VISIBLE
            drawerLayout.animate().alpha(1f).setDuration(140).setInterpolator(DecelerateInterpolator()).start()
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

        drawerTitleTv.text = if (filterChar != null) "APPLICATIONS [$filterChar]" else "APPLICATIONS"

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No apps starting with '$filterChar'"
                setTextColor(COLOR_MUTED)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(0, 24, 0, 0)
            }
            drawerAppContainer.addView(emptyTv)
            return
        }

        for (app in filtered) {
            drawerAppContainer.addView(createAppRowView(app))
        }
    }

    private fun sanitizeSavedData() {
        val installed = allApps.map { it.packageName }.toSet()
        val favChanged = favoritePackages.retainAll(installed)
        var folderChanged = false
        for (folder in folders) {
            if (folder.packages.retainAll(installed)) {
                folderChanged = true
            }
        }
        if (favChanged || folderChanged) {
            saveUserData()
        }
    }

    private fun refreshAll() {
        allApps = loadInstalledApps()
        sanitizeSavedData()
        renderFavorites()
        renderFolders()
        renderTasks()
    }

    private fun renderFavorites() {
        favoritesContainer.removeAllViews()
        val favs = allApps.filter { favoritePackages.contains(it.packageName) }.take(6)
        if (favs.isEmpty()) {
            val hint = TextView(this).apply {
                text = "> Touch rail to browse apps. Long-press to pin up to 6."
                setTextColor(COLOR_MUTED)
                textSize = 13f
                setPadding(0, 6, 0, 6)
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
                setPadding(24, 18, 24, 18)
                background = createCardDrawable(COLOR_SURFACE, 14f, COLOR_BORDER)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 6, 0, 6)
                layoutParams = lp
            }

            val icon = TextView(this).apply {
                text = "📁"
                textSize = 16f
                setPadding(0, 0, 20, 0)
            }
            val title = TextView(this).apply {
                text = "${folder.name} (${folder.packages.size})"
                setTextColor(COLOR_TEXT)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
            }
            folderBox.addView(icon)
            folderBox.addView(title)

            folderBox.setOnClickListener {
                pulseHaptic()
                showFolderDialog(folder)
            }
            foldersContainer.addView(folderBox)
        }
    }

    private fun createAppRowView(app: AppItem): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 18, 0, 18)
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            val size = 92
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 24, 0) }
        }
        row.addView(icon)

        val name = TextView(this).apply {
            text = app.name
            setTextColor(COLOR_TEXT)
            textSize = 15f
            letterSpacing = 0.03f
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
        return row
    }

    private fun showAppOptions(app: AppItem) {
        val isFav = favoritePackages.contains(app.packageName)
        val favLabel = if (isFav) "Unpin from top" else "Pin to top (Max 6)"
        val opts = arrayOf(favLabel, "Add to Folder")

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        if (isFav) favoritePackages.remove(app.packageName)
                        else if (favoritePackages.size < 6) favoritePackages.add(app.packageName)
                        else Toast.makeText(this, "Pinned list full (Max 6)", Toast.LENGTH_SHORT).show()
                        saveUserData()
                        renderFavorites()
                    }
                    1 -> {
                        val names = folders.map { it.name }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Select Folder")
                            .setItems(names) { _, fIndex ->
                                val f = folders[fIndex]
                                if (!f.packages.contains(app.packageName)) {
                                    f.packages.add(app.packageName)
                                    saveUserData()
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
            .setTitle(folder.name)
            .apply {
                if (names.isEmpty()) setMessage("Folder is empty. Long-press any app to add it.")
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
            Toast.makeText(this, "Unable to launch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCardDrawable(color: Int, radius: Float, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(1, strokeColor)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.visibility == View.VISIBLE) {
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
            val icon = info.loadIcon(packageManager)
            list.add(AppItem(label, pName, icon))
        }
        return list.sortedBy { it.name.lowercase() }
    }
}
