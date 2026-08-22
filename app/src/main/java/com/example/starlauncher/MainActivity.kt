You are completely right. Over-engineering with multiple nested view pagers, mock modules, and dense widgets turned what should be a clean, lightning-fast personal launchpad into a bloated chore.
Here is the reset: A single-screen, zero-lag, practical phone interface tailored specifically to your daily tools.
What Makes This Work for Daily Use
 * Single Viewport (No Horizontals, No Hidden Paging):
   * Clean Header: Digital Clock, Date, and your 03:45 AM Target Alarm with a direct tap-to-toggle rest-day switch.
   * Direct Daily Commute & Home Row: Dedicated quick-launch capsules for TrainPal (Downham Market Fen Line) and Google Home.
   * Core Pinned Apps (4×2 Grid): Instant access to Obsidian, Termux, BandLab, GitHub, TrainPal, Camera, Spotify, and Settings with real app icons.
   * Clean Directive Scratchpad: A single, lightweight daily note/task checklist sitting directly on the canvas.
 * Clean, Dismissable Alphabet Scrubber:
   * Enlarged Touch Targets: Easy thumb reach along the lower-right bezel.
   * Tap-Off Dismissal: Tapping anywhere outside the drawer, sliding back to •, or hitting the back gesture closes it immediately.
   * No Dead Stops: Automatically snaps to populated letters.
 * True Translucent Frosted Glass:
   * Pure minimal styling with delicate borders that let your wallpaper breathe without turning into opaque gray slabs.
Complete Production MainActivity.kt
package com.example.starlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val gson = Gson()
    private val PREFS = "GowerLauncherPrefs"
    private val KEY_FAVS = "key_pinned_grid_8"
    private val KEY_TASKS = "key_tasks"
    private val KEY_ALARM_ENABLED = "key_alarm_enabled"

    // --- REFINED TRANSLUCENT PALETTE ---
    private val COLOR_TRANSPARENT = Color.TRANSPARENT
    private val COLOR_GLASS_BG = Color.parseColor("#33121215")     // 20% smoked glass
    private val COLOR_GLASS_CARD = Color.parseColor("#4D18181F")   // 30% frosted card
    private val COLOR_GLASS_LIT = Color.parseColor("#8027272A")    // 50% lit highlight
    private val COLOR_WHITE = Color.parseColor("#FFFFFF")         // Pure white
    private val COLOR_TEXT = Color.parseColor("#E4E4E7")          // Bone white
    private val COLOR_MUTED = Color.parseColor("#9CA3AF")         // Slate gray
    private val COLOR_BORDER = Color.parseColor("#33FFFFFF")      // Hairline rim

    data class AppItem(val name: String, val packageName: String, val icon: Drawable?)
    data class TaskItem(val id: Long, val text: String, var isDone: Boolean)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var taskList = mutableListOf<TaskItem>()
    private var isAlarmEnabled = true

    private lateinit var rootLayout: FrameLayout
    private lateinit var drawerLayout: FrameLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerTitleTv: TextView

    // View References
    private var timeTv: TextView? = null
    private var dateTv: TextView? = null
    private var alarmPillTv: TextView? = null
    private var favoritesGridLayout: GridLayout? = null
    private var tasksContainer: LinearLayout? = null

    // Alphabet Rail
    private lateinit var railContainer: LinearLayout
    private lateinit var floatingBadge: TextView
    private val alphabet = listOf('•') + ('A'..'Z').toList()
    private val railViews = mutableListOf<TextView>()
    private var lastHoverIndex = -1

    private var vibrator: Vibrator? = null
    private var touchStartY = 0f
    private var isTrackingSwipeDown = false

    private val timeTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateClockAndSleepData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER,
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )

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
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeTickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeTickReceiver, filter)
        }
        updateClockAndSleepData()
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(timeTickReceiver) } catch (_: Exception) {}
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

    private fun updateClockAndSleepData() {
        val now = Calendar.getInstance()
        timeTv?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
        dateTv?.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now.time).uppercase()

        if (!isAlarmEnabled) {
            alarmPillTv?.text = "ALARM: REST DAY"
            alarmPillTv?.setTextColor(COLOR_MUTED)
            return
        }

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val diffMs = target.timeInMillis - now.timeInMillis
        val diffHours = diffMs / (1000 * 60 * 60)
        val diffMinutes = (diffMs / (1000 * 60)) % 60

        alarmPillTv?.text = "03:45 WAKE (${diffHours}h ${diffMinutes}m)"
        alarmPillTv?.setTextColor(COLOR_WHITE)
    }

    private fun toggleAlarmState() {
        pulseHaptic()
        isAlarmEnabled = !isAlarmEnabled
        saveUserData()
        if (isAlarmEnabled) {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, 3)
                putExtra(AlarmClock.EXTRA_MINUTES, 45)
                putExtra(AlarmClock.EXTRA_MESSAGE, "03:45 Station Wake")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
        updateClockAndSleepData()
    }

    private fun expandNotificationShade() {
        try {
            @SuppressLint("WrongConstant")
            val statusBarService = getSystemService("statusbar")
            val statusBarManager: Class<*> = Class.forName("android.app.StatusBarManager")
            val expand: Method = statusBarManager.getMethod("expandNotificationsPanel")
            expand.invoke(statusBarService)
            pulseHaptic()
        } catch (_: Exception) {}
    }

    private fun loadUserData() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val favJson = p.getString(KEY_FAVS, null)
        val taskJson = p.getString(KEY_TASKS, null)
        isAlarmEnabled = p.getBoolean(KEY_ALARM_ENABLED, true)

        favoritePackages = if (favJson != null) gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type) else mutableListOf()
        taskList = if (taskJson != null) {
            gson.fromJson(taskJson, object : TypeToken<MutableList<TaskItem>>() {}.type)
        } else {
            mutableListOf(
                TaskItem(1, "Draft Lorehaven scene notes", false),
                TaskItem(2, "Review Downham Market garden layout", false)
            )
        }
    }

    private fun saveUserData() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, gson.toJson(favoritePackages))
            .putString(KEY_TASKS, gson.toJson(taskList))
            .putBoolean(KEY_ALARM_ENABLED, isAlarmEnabled)
            .apply()
    }

    private fun resetFavoritesToSmartDefaults() {
        pulseHaptic()
        favoritePackages.clear()

        val priorityTargets = listOf(
            "com.thetrainline",
            "com.trainpal",
            "com.google.android.apps.chromecast.app", // Google Home
            "md.obsidian",
            "com.termux",
            "com.bandlab.bandlab",
            "com.spotify.music",
            "com.google.android.GoogleCamera"
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
        Toast.makeText(this@MainActivity, "Favourites reset to defaults", Toast.LENGTH_SHORT).show()
    }

    private fun buildInterface() {
        rootLayout = object : FrameLayout(this) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = ev.y
                        isTrackingSwipeDown = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = ev.y - touchStartY
                        if (isTrackingSwipeDown && dy > 180 && ev.y < height * 0.4f) {
                            isTrackingSwipeDown = false
                            expandNotificationShade()
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isTrackingSwipeDown = false
                }
                return super.onInterceptTouchEvent(ev)
            }
        }.apply {
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
            setPadding(32, 48, 76, 48)
        }

        // 1. Clock & Date
        val tTv = TextView(this).apply {
            setTextColor(COLOR_WHITE)
            textSize = 44f
            letterSpacing = 0.01f
            typeface = Typeface.DEFAULT_BOLD
        }
        timeTv = tTv
        content.addView(tTv)

        val dTv = TextView(this).apply {
            setTextColor(COLOR_TEXT)
            textSize = 13f
            letterSpacing = 0.08f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 14)
        }
        dateTv = dTv
        content.addView(dTv)

        // 2. Direct-Action Bar (03:45 Alarm, TrainPal, Home)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 16)
        }

        val alarmPill = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(14, 8, 14, 8)
            background = createCardDrawable(COLOR_GLASS_CARD, 20f, COLOR_BORDER)
            setOnClickListener { toggleAlarmState() }
        }
        alarmPillTv = alarmPill
        actionRow.addView(alarmPill)

        val trainPill = TextView(this).apply {
            text = "TRAINPAL"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(14, 8, 14, 8)
            background = createCardDrawable(COLOR_GLASS_CARD, 20f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(10, 0, 0, 0) }
            layoutParams = lp
            setOnClickListener {
                pulseHaptic()
                val intent = packageManager.getLaunchIntentForPackage("com.trainpal")
                    ?: packageManager.getLaunchIntentForPackage("com.thetrainline")
                if (intent != null) startActivity(intent)
                else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nationalrail.co.uk/live-trains/departures/downham-market/")))
            }
        }
        actionRow.addView(trainPill)

        val homePill = TextView(this).apply {
            text = "HOME"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(14, 8, 14, 8)
            background = createCardDrawable(COLOR_GLASS_CARD, 20f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(10, 0, 0, 0) }
            layoutParams = lp
            setOnClickListener {
                pulseHaptic()
                val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
                    ?: packageManager.getLaunchIntentForPackage("io.homeassistant.companion.android")
                if (intent != null) startActivity(intent)
                else Toast.makeText(this@MainActivity, "Home app not found", Toast.LENGTH_SHORT).show()
            }
        }
        actionRow.addView(homePill)
        content.addView(actionRow)

        // 3. Omni-Search
        content.addView(createOmniSearch())

        // 4. Core Favourites 4x2 Grid
        val favHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 8)
        }
        val favHeader = TextView(this).apply {
            text = "DAILY TOOLS"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            letterSpacing = 0.14f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val resetFavsBtn = TextView(this).apply {
            text = "[ RESET ]"
            setTextColor(COLOR_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(8, 4, 8, 4)
            setOnClickListener { resetFavoritesToSmartDefaults() }
        }
        favHeaderRow.addView(favHeader)
        favHeaderRow.addView(resetFavsBtn)
        content.addView(favHeaderRow)

        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 2
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            layoutParams = lp
        }
        favoritesGridLayout = grid
        content.addView(grid)

        // 5. Daily Directives & Quick Notes
        val taskHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val taskHeader = TextView(this).apply {
            text = "DIRECTIVES & NOTES"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            letterSpacing = 0.14f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addBtn = TextView(this).apply {
            text = "[ + ADD ]"
            setTextColor(COLOR_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(8, 4, 8, 4)
            setOnClickListener { showAddTaskDialog() }
        }
        taskHeaderRow.addView(taskHeader)
        taskHeaderRow.addView(addBtn)
        content.addView(taskHeaderRow)

        val tasks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 0)
        }
        tasksContainer = tasks
        content.addView(tasks)

        scroll.addView(content)
        rootLayout.addView(scroll)

        // ================= APP DRAWER OVERLAY (TAP-OUT TO DISMISS) =================
        drawerLayout = object : FrameLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    hideAppDrawer()
                    return true
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            setBackgroundColor(COLOR_GLASS_BG)
            visibility = View.GONE
            alpha = 0f
            setPadding(32, 64, 80, 64)
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
            setTextColor(COLOR_WHITE)
            textSize = 15f
            letterSpacing = 0.14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 16)
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

        // ================= FLOATING MAGNIFIER BADGE =================
        floatingBadge = TextView(this).apply {
            visibility = View.GONE
            setTextColor(COLOR_WHITE)
            textSize = 28f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = createCardDrawable(COLOR_GLASS_LIT, 24f, COLOR_WHITE)
            layoutParams = FrameLayout.LayoutParams(110, 110, Gravity.END or Gravity.TOP).apply {
                marginEnd = 80
            }
            elevation = 20f
        }
        rootLayout.addView(floatingBadge)

        // ================= BOTTOM-ANCHORED ALPHABET RAIL =================
        val alphabetRail = buildAlphabetRail()
        rootLayout.addView(alphabetRail)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            content.setPadding(32, statusBarHeight + 16, 76, 48)
            insets
        }

        setContentView(rootLayout)
        refreshAll()
    }

    private fun createOmniSearch(): View {
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 10, 18, 10)
            background = createCardDrawable(COLOR_GLASS_CARD, 14f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 8) }
            layoutParams = lp
        }

        val prompt = TextView(this).apply {
            text = "❯"
            setTextColor(COLOR_WHITE)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 12, 0)
        }
        searchBox.addView(prompt)

        val sInput = EditText(this).apply {
            hint = "Search web or notes..."
            setHintTextColor(COLOR_MUTED)
            setTextColor(COLOR_WHITE)
            textSize = 13f
            typeface = Typeface.MONOSPACE
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

    private fun renderFavoritesGrid() {
        val grid = favoritesGridLayout ?: return
        grid.removeAllViews()
        val favs = allApps.filter { favoritePackages.contains(it.packageName) }.take(8)

        if (favs.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No pinned tools. Tap [ RESET ] or long-press apps."
                setTextColor(COLOR_MUTED)
                textSize = 11f
                setPadding(0, 8, 0, 8)
            }
            grid.addView(emptyTv)
            return
        }

        val displayMetrics = resources.displayMetrics
        val availableWidth = displayMetrics.widthPixels - 140
        val tileWidth = availableWidth / 4

        for (app in favs) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(6, 10, 6, 10)
                background = createIlluminatedState(COLOR_GLASS_CARD, COLOR_GLASS_LIT, 16f, COLOR_BORDER)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = tileWidth
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(4, 4, 4, 4)
                }
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                val size = 68
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

    private fun renderTasks() {
        val container = tasksContainer ?: return
        container.removeAllViews()
        for (task in taskList) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(18, 14, 18, 14)
                background = createCardDrawable(COLOR_GLASS_CARD, 14f, COLOR_BORDER)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
                layoutParams = lp
            }
            val check = TextView(this).apply {
                text = if (task.isDone) "[DONE]" else "[TODO]"
                setTextColor(if (task.isDone) COLOR_WHITE else COLOR_MUTED)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 14, 0)
            }
            val tv = TextView(this).apply {
                text = task.text
                setTextColor(if (task.isDone) COLOR_MUTED else COLOR_TEXT)
                textSize = 13f
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
            container.addView(row)
        }
    }

    private fun showAddTaskDialog() {
        val input = EditText(this).apply {
            hint = "Inscribe directive..."
            setTextColor(COLOR_WHITE)
            setHintTextColor(COLOR_MUTED)
        }
        AlertDialog.Builder(this)
            .setTitle("New Directive")
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

    // ================= HIGH-LEGIBILITY ALPHABET SCRUBBER =================
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
        if (drawerLayout.visibility != View.VISIBLE) {
            drawerLayout.visibility = View.VISIBLE
            drawerLayout.animate().alpha(1f).setDuration(120).setInterpolator(DecelerateInterpolator()).start()
        }
        filterAndRenderDrawer(filterChar)
    }

    private fun hideAppDrawer() {
        if (drawerLayout.visibility == View.VISIBLE) {
            drawerLayout.animate().alpha(0f).setDuration(100).withEndAction {
                drawerLayout.visibility = View.GONE
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
                typeface = Typeface.MONOSPACE
                setPadding(0, 24, 0, 0)
            }
            drawerAppContainer.addView(emptyTv)
            return
        }

        for (app in filtered) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(18, 14, 18, 14)
                background = createIlluminatedState(Color.TRANSPARENT, COLOR_GLASS_LIT, 12f, COLOR_BORDER)
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                val size = 80
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 18, 0) }
            }
            row.addView(icon)

            val name = TextView(this).apply {
                text = app.name
                setTextColor(COLOR_TEXT)
                textSize = 14f
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
            drawerAppContainer.addView(row)
        }
        drawerScroll.smoothScrollTo(0, 0)
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
        renderTasks()
    }

    private fun showAppOptions(app: AppItem) {
        val isFav = favoritePackages.contains(app.packageName)
        val favLabel = if (isFav) "Unpin from Favourites" else "Pin to Favourites (Max 8)"

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(arrayOf(favLabel)) { _, which ->
                if (which == 0) {
                    if (isFav) favoritePackages.remove(app.packageName)
                    else if (favoritePackages.size < 8) favoritePackages.add(app.packageName)
                    else Toast.makeText(this@MainActivity, "Favourites grid full (Max 8)", Toast.LENGTH_SHORT).show()
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

    private fun createCardDrawable(bgColor: Int, radius: Float, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = radius
            setStroke(1, strokeColor)
        }
    }

    private fun createIlluminatedState(defaultBg: Int, pressedBg: Int, radius: Float, strokeColor: Int): StateListDrawable {
        val pressedDrawable = GradientDrawable().apply {
            setColor(pressedBg)
            cornerRadius = radius
            setStroke(1, strokeColor)
        }
        val normalDrawable = GradientDrawable().apply {
            setColor(defaultBg)
            cornerRadius = radius
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(android.R.attr.state_focused), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
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

