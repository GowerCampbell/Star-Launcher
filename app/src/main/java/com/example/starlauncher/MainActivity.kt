package com.example.starlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.media.AudioManager
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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val gson = Gson()
    private val PREFS = "CharcoalWorkstationPrefs"
    private val KEY_FAVS = "key_pinned_grid"
    private val KEY_FOLDERS = "key_folders"
    private val KEY_TASKS = "key_tasks"
    private val KEY_HOUSE_ITEMS = "key_house_items"
    private val KEY_ALARM_ENABLED = "key_alarm_enabled"
    private val KEY_READING_NOTE = "key_reading_note"

    // --- TRANSLUCENT FROSTED GLASS PALETTE ---
    private val COLOR_TRANSPARENT = Color.TRANSPARENT
    private val COLOR_GLASS_SURFACE = Color.parseColor("#33121215") // 20% translucent smoked glass
    private val COLOR_GLASS_CARD = Color.parseColor("#4D18181F")    // 30% translucent frosted card
    private val COLOR_GLASS_LIT = Color.parseColor("#8027272A")     // 50% lit highlight
    private val COLOR_WHITE = Color.parseColor("#FFFFFF")          // Pure white
    private val COLOR_TEXT = Color.parseColor("#E4E4E7")           // Warm bone white
    private val COLOR_MUTED = Color.parseColor("#9CA3AF")          // Slate ash gray
    private val COLOR_BORDER = Color.parseColor("#33FFFFFF")       // 20% delicate white hairline rim

    data class AppItem(val name: String, val packageName: String, val icon: Drawable?, val category: Int)
    data class FolderItem(val name: String, val packages: MutableList<String>)
    data class TaskItem(val id: Long, val text: String, var isDone: Boolean)
    data class HouseItem(val id: Long, val item: String, val cost: String, var isBought: Boolean)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var folders = mutableListOf<FolderItem>()
    private var taskList = mutableListOf<TaskItem>()
    private var houseWishlist = mutableListOf<HouseItem>()
    private var isAlarmEnabled = true
    private var readingNote = "Currently Reading: Gaiman / Lovecraft Anthology (Ch. 4)"

    private lateinit var rootLayout: FrameLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicatorTv: TextView

    // Drawer elements
    private lateinit var drawerLayout: FrameLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerTitleTv: TextView

    // Dynamic View References
    private var timeTv: TextView? = null
    private var dateTv: TextView? = null
    private var sleepCountdownTv: TextView? = null
    private var alarmToggleBtn: TextView? = null
    private var trackTitleTv: TextView? = null
    private var trackArtistTv: TextView? = null
    private var searchInput: EditText? = null
    private var favoritesGridLayout: GridLayout? = null
    private var foldersRow: LinearLayout? = null
    private var tasksContainer: LinearLayout? = null
    private var houseItemsContainer: LinearLayout? = null
    private var readingNoteTv: TextView? = null

    // Alphabet Rail
    private lateinit var railContainer: LinearLayout
    private lateinit var floatingBadge: TextView
    private val alphabet = listOf('•') + ('A'..'Z').toList()
    private val railViews = mutableListOf<TextView>()
    private var lastHoverIndex = -1

    private var vibrator: Vibrator? = null

    private var touchStartY = 0f
    private var isTrackingSwipeDown = false

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED -> updateClockAndSleepData()
                "com.spotify.music.playbackstatechanged", "com.spotify.music.metadatachanged" -> {
                    val track = intent.getStringExtra("track") ?: "Now Playing"
                    val artist = intent.getStringExtra("artist") ?: "Spotify"
                    val isPlaying = intent.getBooleanExtra("playing", false)
                    updateMedia(track, artist, isPlaying)
                }
            }
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
        buildSlideInterface()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction("com.spotify.music.playbackstatechanged")
            addAction("com.spotify.music.metadatachanged")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(systemReceiver, filter)
        }
        updateClockAndSleepData()
        refreshAll()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(systemReceiver)
        } catch (_: Exception) {}
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
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        timeTv?.text = timeFormat.format(now.time)
        dateTv?.text = dateFormat.format(now.time).uppercase()

        if (!isAlarmEnabled) {
            sleepCountdownTv?.text = "03:45 ALARM DISABLED • REST DAY"
            alarmToggleBtn?.text = "[ REST DAY ]"
            alarmToggleBtn?.setTextColor(COLOR_MUTED)
            return
        }

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 45)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val diffMs = target.timeInMillis - now.timeInMillis
        val diffHours = diffMs / (1000 * 60 * 60)
        val diffMinutes = (diffMs / (1000 * 60)) % 60

        sleepCountdownTv?.text = "WAKE 03:45 (${diffHours}h ${diffMinutes}m remaining)"
        alarmToggleBtn?.text = "[ ARMED: 03:45 ]"
        alarmToggleBtn?.setTextColor(COLOR_WHITE)
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
        } catch (_: Exception) {
            try {
                @SuppressLint("WrongConstant")
                val statusBarService = getSystemService("statusbar")
                val statusBarManager: Class<*> = Class.forName("android.app.StatusBarManager")
                val expand: Method = statusBarManager.getMethod("expand")
                expand.invoke(statusBarService)
                pulseHaptic()
            } catch (_: Exception) {}
        }
    }

    private fun updateMedia(track: String, artist: String, isPlaying: Boolean) {
        trackTitleTv?.text = track
        trackArtistTv?.text = if (isPlaying) "Playing • $artist" else "Paused • $artist"
    }

    private fun loadUserData() {
        val p = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val favJson = p.getString(KEY_FAVS, null)
        val folderJson = p.getString(KEY_FOLDERS, null)
        val taskJson = p.getString(KEY_TASKS, null)
        val houseJson = p.getString(KEY_HOUSE_ITEMS, null)
        isAlarmEnabled = p.getBoolean(KEY_ALARM_ENABLED, true)
        readingNote = p.getString(KEY_READING_NOTE, "Currently Reading: Gaiman / Lovecraft Anthology (Ch. 4)") ?: ""

        favoritePackages = if (favJson != null) gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type) else mutableListOf()
        folders = if (folderJson != null) {
            gson.fromJson(folderJson, object : TypeToken<MutableList<FolderItem>>() {}.type)
        } else {
            mutableListOf(
                FolderItem("Writing & Codex", mutableListOf()),
                FolderItem("Dev & Shell", mutableListOf()),
                FolderItem("Sound & Media", mutableListOf()),
                FolderItem("Home & Transit", mutableListOf())
            )
        }
        taskList = if (taskJson != null) {
            gson.fromJson(taskJson, object : TypeToken<MutableList<TaskItem>>() {}.type)
        } else {
            mutableListOf(
                TaskItem(1, "Draft Lorehaven worldbuilding scene", false),
                TaskItem(2, "Test BandLab atmospheric layer", false),
                TaskItem(3, "Refactor Termux build scripts", false)
            )
        }
        houseWishlist = if (houseJson != null) {
            gson.fromJson(houseJson, object : TypeToken<MutableList<HouseItem>>() {}.type)
        } else {
            mutableListOf(
                HouseItem(1, "Garden sleeper corner brackets", "£24.00", false),
                HouseItem(2, "Kitchen spice storage rack", "£18.50", false),
                HouseItem(3, "Living room floor reading lamp", "£45.00", false)
            )
        }
    }

    private fun saveUserData() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, gson.toJson(favoritePackages))
            .putString(KEY_FOLDERS, gson.toJson(folders))
            .putString(KEY_TASKS, gson.toJson(taskList))
            .putString(KEY_HOUSE_ITEMS, gson.toJson(houseWishlist))
            .putBoolean(KEY_ALARM_ENABLED, isAlarmEnabled)
            .putString(KEY_READING_NOTE, readingNote)
            .apply()
    }

    private fun resetFavoritesToSmartDefaults() {
        pulseHaptic()
        favoritePackages.clear()

        val priorityTargets = listOf(
            "com.google.android.apps.chromecast.app", // Google Home
            "md.obsidian",
            "com.termux",
            "com.bandlab.bandlab",
            "com.thetrainline",
            "com.google.android.GoogleCamera"
        )

        for (target in priorityTargets) {
            if (allApps.any { it.packageName == target }) {
                favoritePackages.add(target)
            }
        }

        for (app in allApps) {
            if (favoritePackages.size >= 6) break
            if (!favoritePackages.contains(app.packageName)) {
                favoritePackages.add(app.packageName)
            }
        }

        saveUserData()
        renderFavoritesGrid()
        Toast.makeText(this, "Core 2×3 Grid reset to defaults", Toast.LENGTH_SHORT).show()
    }

    private fun autoSortShelves() {
        pulseHaptic()
        for (folder in folders) folder.packages.clear()

        for (app in allApps) {
            val name = app.name.lowercase()
            val pkg = app.packageName.lowercase()

            when {
                name.contains("obsidian") || name.contains("note") || name.contains("reader") || name.contains("kindle") || name.contains("docs") -> {
                    folders.firstOrNull { it.name.contains("Writing", true) }?.packages?.add(app.packageName)
                }
                name.contains("termux") || name.contains("git") || name.contains("code") || name.contains("terminal") || name.contains("godot") -> {
                    folders.firstOrNull { it.name.contains("Dev", true) }?.packages?.add(app.packageName)
                }
                name.contains("bandlab") || name.contains("spotify") || name.contains("music") || name.contains("painter") || name.contains("audio") -> {
                    folders.firstOrNull { it.name.contains("Sound", true) }?.packages?.add(app.packageName)
                }
                name.contains("home") || name.contains("train") || name.contains("map") || name.contains("pay") || name.contains("ikea") || name.contains("amazon") -> {
                    folders.firstOrNull { it.name.contains("Home", true) || it.name.contains("Transit", true) }?.packages?.add(app.packageName)
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        when (app.category) {
                            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO ->
                                folders.firstOrNull { it.name.contains("Sound", true) }?.packages?.add(app.packageName)
                            ApplicationInfo.CATEGORY_MAPS ->
                                folders.firstOrNull { it.name.contains("Home", true) }?.packages?.add(app.packageName)
                            ApplicationInfo.CATEGORY_PRODUCTIVITY ->
                                folders.firstOrNull { it.name.contains("Writing", true) }?.packages?.add(app.packageName)
                        }
                    }
                }
            }
        }

        saveUserData()
        renderFolders()
        Toast.makeText(this, "Workspace shelves self-organized", Toast.LENGTH_SHORT).show()
    }

    private fun buildSlideInterface() {
        rootLayout = object : FrameLayout(this) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartY = ev.y
                        isTrackingSwipeDown = true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = ev.y - touchStartY
                        if (isTrackingSwipeDown && dy > 180 && ev.y < height * 0.45f) {
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
                val view = if (viewType == 0) buildMainStationPage() else buildDirectivesAndHousePage()
                return object : RecyclerView.ViewHolder(view) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pulseHaptic()
                pageIndicatorTv.text = if (position == 0) "[ LIVING STATION • HOUSE & CODEX ]" else "[ LIVING STATION • HOUSE & CODEX* ]"
            }
        })

        rootLayout.addView(viewPager)

        pageIndicatorTv = TextView(this).apply {
            text = "[ LIVING STATION • HOUSE & CODEX ]"
            setTextColor(COLOR_MUTED)
            textSize = 10f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                setMargins(0, 32, 0, 0)
            }
        }
        rootLayout.addView(pageIndicatorTv)

        // ================= APP DRAWER OVERLAY =================
        drawerLayout = FrameLayout(this).apply {
            setBackgroundColor(COLOR_GLASS_SURFACE)
            visibility = View.GONE
            alpha = 0f
            setPadding(36, 56, 80, 88)
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
            setPadding(0, 0, 0, 18)
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
            setTextColor(COLOR_WHITE)
            textSize = 26f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            background = createCardDrawable(COLOR_GLASS_LIT, 24f, COLOR_WHITE)
            layoutParams = FrameLayout.LayoutParams(116, 116, Gravity.END or Gravity.TOP).apply {
                marginEnd = 88
            }
            elevation = 18f
        }
        rootLayout.addView(floatingBadge)

        // ================= BOTTOM-ANCHORED ALPHABET RAIL =================
        val alphabetRail = buildAlphabetRail()
        rootLayout.addView(alphabetRail)

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            pageIndicatorTv.translationY = statusBarHeight.toFloat()
            insets
        }

        setContentView(rootLayout)
        refreshAll()
    }

    private fun buildMainStationPage(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 68, 80, 120)
        }

        content.addView(createChronoAndSleepBeacon())
        content.addView(createHomeAndCommuteDeck())
        content.addView(createOmniSearch())
        content.addView(createAudioDeck())

        // 2x3 Grid Header with Reset to Defaults action
        val favHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 18, 0, 10)
        }

        val favHeader = TextView(this).apply {
            text = "CORE TOOLS (2×3)"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            letterSpacing = 0.16f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val resetFavsBtn = TextView(this).apply {
            text = "[ RESET DEFAULTS ]"
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
            columnCount = 3
            rowCount = 2
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 18)
            layoutParams = lp
        }
        favoritesGridLayout = grid
        content.addView(grid)

        // Workspace Shelves Header with Auto-Sort action
        val foldHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 10)
        }

        val foldHeader = TextView(this).apply {
            text = "WORKSPACE SHELVES"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            letterSpacing = 0.16f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val autoSortBtn = TextView(this).apply {
            text = "[ AUTO-SORT ]"
            setTextColor(COLOR_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(8, 4, 8, 4)
            setOnClickListener { autoSortShelves() }
        }
        foldHeaderRow.addView(foldHeader)
        foldHeaderRow.addView(autoSortBtn)
        content.addView(foldHeaderRow)

        val foldersContainer = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 18)
            layoutParams = lp
            setOnTouchListener { _, _ ->
                parent.requestDisallowInterceptTouchEvent(true)
                false
            }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        foldersRow = row
        foldersContainer.addView(row)
        content.addView(foldersContainer)

        scroll.addView(content)
        renderFavoritesGrid()
        renderFolders()
        return scroll
    }

    private fun buildDirectivesAndHousePage(): View {
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 68, 80, 120)
        }

        // 1. New House Purchase & Project Wishlist
        content.addView(createHouseWishlistModule())

        // 2. Reading & Narrative Codex Module
        content.addView(createReadingHubModule())

        // 3. Directives Ledger
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 16)
        }

        val title = TextView(this).apply {
            text = "DIRECTIVES & STORY NOTES"
            setTextColor(COLOR_WHITE)
            textSize = 13f
            letterSpacing = 0.14f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addBtn = TextView(this).apply {
            text = "[ + INSCRIBE ]"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(14, 8, 14, 8)
            background = createCardDrawable(COLOR_GLASS_CARD, 10f, COLOR_BORDER)
            setOnClickListener { showAddTaskDialog() }
        }
        headerRow.addView(title)
        headerRow.addView(addBtn)
        content.addView(headerRow)

        val tasks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        tasksContainer = tasks
        content.addView(tasks)

        scroll.addView(content)
        renderTasks()
        renderHouseWishlist()
        return scroll
    }

    private fun createChronoAndSleepBeacon(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 22, 26, 22)
            background = createCardDrawable(COLOR_GLASS_CARD, 18f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 14)
            layoutParams = lp
        }

        val tTv = TextView(this).apply {
            setTextColor(COLOR_WHITE)
            textSize = 38f
            letterSpacing = 0.04f
            typeface = Typeface.MONOSPACE
        }
        timeTv = tTv

        val dTv = TextView(this).apply {
            setTextColor(COLOR_MUTED)
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 12)
        }
        dateTv = dTv

        val sleepRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 0)
        }

        val sTv = TextView(this).apply {
            setTextColor(COLOR_TEXT)
            textSize = 11f
            letterSpacing = 0.06f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        sleepCountdownTv = sTv

        val armBtn = TextView(this).apply {
            text = if (isAlarmEnabled) "[ ARMED: 03:45 ]" else "[ REST DAY ]"
            setTextColor(if (isAlarmEnabled) COLOR_WHITE else COLOR_MUTED)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(12, 6, 12, 6)
            background = createCardDrawable(COLOR_GLASS_LIT, 10f, COLOR_BORDER)
            setOnClickListener { toggleAlarmState() }
        }
        alarmToggleBtn = armBtn

        sleepRow.addView(sTv)
        sleepRow.addView(armBtn)

        card.addView(tTv)
        card.addView(dTv)
        card.addView(sleepRow)
        updateClockAndSleepData()
        return card
    }

    // ================= DOWNHAM MARKET HOME & COMMUTE DOCK =================
    private fun createHomeAndCommuteDeck(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) }
            layoutParams = lp
        }

        // Smart Home Capsule
        val homeTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            background = createIlluminatedState(COLOR_GLASS_CARD, COLOR_GLASS_LIT, 14f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }

        val homeTag = TextView(this).apply {
            text = "HOME HUB"
            setTextColor(COLOR_WHITE)
            textSize = 10f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
        }
        val homeSub = TextView(this).apply {
            text = "Smart Controls"
            setTextColor(COLOR_MUTED)
            textSize = 11f
            setPadding(0, 4, 0, 0)
        }
        homeTile.addView(homeTag)
        homeTile.addView(homeSub)
        homeTile.setOnClickListener {
            pulseHaptic()
            val homeApp = packageManager.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
                ?: packageManager.getLaunchIntentForPackage("io.homeassistant.companion.android")
                ?: packageManager.getLaunchIntentForPackage("com.tuya.smartlife")
            if (homeApp != null) startActivity(homeApp)
            else Toast.makeText(this, "Smart Home app not configured", Toast.LENGTH_SHORT).show()
        }
        container.addView(homeTile)

        // Fen Line Commute Capsule
        val transitTile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            background = createIlluminatedState(COLOR_GLASS_CARD, COLOR_GLASS_LIT, 14f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            layoutParams = lp
            isClickable = true
            isFocusable = true
        }

        val transitTag = TextView(this).apply {
            text = "FEN LINE"
            setTextColor(COLOR_WHITE)
            textSize = 10f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
        }
        val transitSub = TextView(this).apply {
            text = "TrainPal / Rail"
            setTextColor(COLOR_MUTED)
            textSize = 11f
            setPadding(0, 4, 0, 0)
        }
        transitTile.addView(transitTag)
        transitTile.addView(transitSub)
        transitTile.setOnClickListener {
            pulseHaptic()
            val trainApp = packageManager.getLaunchIntentForPackage("com.trainpal")
                ?: packageManager.getLaunchIntentForPackage("com.thetrainline")
            if (trainApp != null) startActivity(trainApp)
            else {
                val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.nationalrail.co.uk/live-trains/departures/downham-market/"))
                startActivity(browser)
            }
        }
        container.addView(transitTile)

        return container
    }

    // ================= NEW HOUSE WISHLIST MODULE =================
    private fun createHouseWishlistModule(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = createCardDrawable(COLOR_GLASS_CARD, 16f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            layoutParams = lp
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "NEW HOUSE • PURCHASES & PROJECTS"
            setTextColor(COLOR_WHITE)
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addBtn = TextView(this).apply {
            text = "[ + ITEM ]"
            setTextColor(COLOR_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(10, 4, 10, 4)
            setOnClickListener { showAddHouseItemDialog() }
        }
        topRow.addView(title)
        topRow.addView(addBtn)
        card.addView(topRow)

        houseItemsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }
        card.addView(houseItemsContainer)

        // Retail Quick Bar (John Lewis, Amazon, Homeware)
        val retailRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val retailPkgs = listOf(
            "uk.co.johnlewis.android",
            "com.amazon.mShop.android.shopping",
            "com.ikea.inter.appshop"
        )
        for (pkg in retailPkgs) {
            val app = allApps.firstOrNull { it.packageName == pkg }
            if (app != null) {
                val btn = ImageView(this).apply {
                    setImageDrawable(app.icon)
                    val size = 60
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 16, 0) }
                    setOnClickListener {
                        pulseHaptic()
                        launchApp(pkg)
                    }
                }
                retailRow.addView(btn)
            }
        }
        card.addView(retailRow)

        return card
    }

    private fun renderHouseWishlist() {
        val container = houseItemsContainer ?: return
        container.removeAllViews()
        for (item in houseWishlist) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val check = TextView(this).apply {
                text = if (item.isBought) "[BOUGHT]" else "[WANTED]"
                setTextColor(if (item.isBought) COLOR_MUTED else COLOR_WHITE)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 14, 0)
            }
            val desc = TextView(this).apply {
                text = "${item.item} (${item.cost})"
                setTextColor(if (item.isBought) COLOR_MUTED else COLOR_TEXT)
                textSize = 13f
                if (item.isBought) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            row.addView(check)
            row.addView(desc)

            row.setOnClickListener {
                pulseHaptic()
                item.isBought = !item.isBought
                saveUserData()
                renderHouseWishlist()
            }
            row.setOnLongClickListener {
                pulseHaptic()
                houseWishlist.remove(item)
                saveUserData()
                renderHouseWishlist()
                true
            }
            container.addView(row)
        }
    }

    private fun showAddHouseItemDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val nameInput = EditText(this).apply {
            hint = "Item description (e.g. Oak brackets)"
            setTextColor(COLOR_WHITE)
            setHintTextColor(COLOR_MUTED)
        }
        val costInput = EditText(this).apply {
            hint = "Estimated cost (e.g. £25.00)"
            setTextColor(COLOR_WHITE)
            setHintTextColor(COLOR_MUTED)
        }
        layout.addView(nameInput)
        layout.addView(costInput)

        AlertDialog.Builder(this)
            .setTitle("Add House Item")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                val cost = costInput.text.toString().trim()
                if (name.isNotEmpty()) {
                    houseWishlist.add(HouseItem(System.currentTimeMillis(), name, if (cost.isEmpty()) "TBD" else cost, false))
                    saveUserData()
                    renderHouseWishlist()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createReadingHubModule(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = createCardDrawable(COLOR_GLASS_CARD, 16f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            layoutParams = lp
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "READING CODEX & ANTHOLOGY"
            setTextColor(COLOR_WHITE)
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val editBtn = TextView(this).apply {
            text = "[ UPDATE ]"
            setTextColor(COLOR_MUTED)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(10, 4, 10, 4)
            setOnClickListener { showEditReadingNoteDialog() }
        }
        topRow.addView(title)
        topRow.addView(editBtn)
        card.addView(topRow)

        val note = TextView(this).apply {
            text = readingNote
            setTextColor(COLOR_TEXT)
            textSize = 13f
            setPadding(0, 10, 0, 14)
        }
        readingNoteTv = note
        card.addView(note)

        val launchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val readerTools = listOf("md.obsidian", "com.amazon.kindle", "com.flyersoft.moonreader")
        for (pkg in readerTools) {
            val app = allApps.firstOrNull { it.packageName == pkg }
            if (app != null) {
                val btn = ImageView(this).apply {
                    setImageDrawable(app.icon)
                    val size = 60
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 16, 0) }
                    setOnClickListener {
                        pulseHaptic()
                        launchApp(pkg)
                    }
                }
                launchBar.addView(btn)
            }
        }
        card.addView(launchBar)

        return card
    }

    private fun showEditReadingNoteDialog() {
        val input = EditText(this).apply {
            setText(readingNote)
            setTextColor(COLOR_WHITE)
            setHintTextColor(COLOR_MUTED)
        }
        AlertDialog.Builder(this)
            .setTitle("Reading Progress")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    readingNote = txt
                    readingNoteTv?.text = readingNote
                    saveUserData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createOmniSearch(): View {
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(22, 12, 22, 12)
            background = createCardDrawable(COLOR_GLASS_CARD, 14f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) }
            layoutParams = lp
        }

        val prompt = TextView(this).apply {
            text = "❯"
            setTextColor(COLOR_WHITE)
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 16, 0)
        }
        searchBox.addView(prompt)

        val sInput = EditText(this).apply {
            hint = "Search threads, notes, or web..."
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
                        try {
                            startActivity(intent)
                        } catch (_: Exception) {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                            startActivity(browserIntent)
                        }
                        text.clear()
                    }
                    true
                } else false
            }
        }
        searchInput = sInput
        searchBox.addView(sInput)
        return searchBox
    }

    private fun createAudioDeck(): View {
        val deck = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(22, 16, 22, 16)
            background = createIlluminatedState(COLOR_GLASS_CARD, COLOR_GLASS_LIT, 16f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 14) }
            layoutParams = lp
        }

        val disc = TextView(this).apply {
            text = "[AUDIO]"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            letterSpacing = 0.1f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 16, 0)
        }
        deck.addView(disc)

        val metaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tTv = TextView(this).apply {
            text = "Audio Resonator"
            setTextColor(COLOR_WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        trackTitleTv = tTv

        val aTv = TextView(this).apply {
            text = "Spotify • Tap to invoke"
            setTextColor(COLOR_MUTED)
            textSize = 11f
            maxLines = 1
        }
        trackArtistTv = aTv

        metaBox.addView(tTv)
        metaBox.addView(aTv)
        deck.addView(metaBox)

        val playToggle = TextView(this).apply {
            text = "PLAY / PAUSE"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
            background = createCardDrawable(COLOR_GLASS_LIT, 8f, COLOR_BORDER)
            setOnClickListener {
                pulseHaptic()
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
            }
        }
        deck.addView(playToggle)

        deck.setOnClickListener {
            pulseHaptic()
            val spotifyIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (spotifyIntent != null) startActivity(spotifyIntent)
            else Toast.makeText(this, "Spotify not found", Toast.LENGTH_SHORT).show()
        }

        return deck
    }

    private fun renderTasks() {
        val container = tasksContainer ?: return
        container.removeAllViews()
        for (task in taskList) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 16, 20, 16)
                background = createCardDrawable(COLOR_GLASS_CARD, 14f, COLOR_BORDER)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
                layoutParams = lp
            }
            val check = TextView(this).apply {
                text = if (task.isDone) "[DONE]" else "[TODO]"
                setTextColor(if (task.isDone) COLOR_WHITE else COLOR_MUTED)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 16, 0)
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

    private fun renderFavoritesGrid() {
        val grid = favoritesGridLayout ?: return
        grid.removeAllViews()
        val favs = allApps.filter { favoritePackages.contains(it.packageName) }.take(6)

        if (favs.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No pinned tools. Tap [ RESET DEFAULTS ] or long-press apps."
                setTextColor(COLOR_MUTED)
                textSize = 11f
                setPadding(0, 8, 0, 8)
            }
            grid.addView(emptyTv)
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels - 140
        val tileWidth = screenWidth / 3

        for (app in favs) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(12, 16, 12, 16)
                background = createIlluminatedState(COLOR_GLASS_CARD, COLOR_GLASS_LIT, 14f, COLOR_BORDER)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = tileWidth
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(6, 6, 6, 6)
                }
                isClickable = true
                isFocusable = true
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.icon)
                val size = 76
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 0, 8) }
            }
            tile.addView(icon)

            val name = TextView(this).apply {
                text = app.name
                setTextColor(COLOR_TEXT)
                textSize = 12f
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

    private fun renderFolders() {
        val row = foldersRow ?: return
        row.removeAllViews()
        for (folder in folders) {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 16, 20, 16)
                background = createIlluminatedState(COLOR_GLASS_CARD, COLOR_GLASS_LIT, 16f, COLOR_BORDER)
                val lp = LinearLayout.LayoutParams(360, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 14, 0)
                }
                layoutParams = lp
                isClickable = true
                isFocusable = true
            }

            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val header = TextView(this).apply {
                text = folder.name.uppercase()
                setTextColor(COLOR_WHITE)
                textSize = 11f
                letterSpacing = 0.14f
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val count = TextView(this).apply {
                text = "${folder.packages.size}"
                setTextColor(COLOR_MUTED)
                textSize = 11f
                typeface = Typeface.MONOSPACE
            }
            headerRow.addView(header)
            headerRow.addView(count)
            panel.addView(headerRow)

            val iconRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 0)
            }

            val folderApps = allApps.filter { folder.packages.contains(it.packageName) }.take(4)
            if (folderApps.isEmpty()) {
                val emptyHint = TextView(this).apply {
                    text = "Empty shelf"
                    setTextColor(COLOR_MUTED)
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                }
                iconRow.addView(emptyHint)
            } else {
                for (app in folderApps) {
                    val miniIcon = ImageView(this).apply {
                        setImageDrawable(app.icon)
                        val size = 52
                        layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 10, 0) }
                    }
                    iconRow.addView(miniIcon)
                }
            }
            panel.addView(iconRow)

            panel.setOnClickListener {
                pulseHaptic()
                showFolderDialog(folder)
            }
            row.addView(panel)
        }
    }

    private fun createAppRowView(app: AppItem): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 16, 18, 16)
            background = createIlluminatedState(Color.TRANSPARENT, COLOR_GLASS_LIT, 12f, COLOR_BORDER)
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            val size = 84
            layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 20, 0) }
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
        return row
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildAlphabetRail(): View {
        railContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(54, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, 6, 80)
            }
            setPadding(0, 12, 4, 12)
        }

        railViews.clear()
        for (c in alphabet) {
            val tv = TextView(this).apply {
                text = c.toString()
                setTextColor(COLOR_MUTED)
                textSize = 9f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(2, 1, 2, 1)
            }
            railViews.add(tv)
            railContainer.addView(tv)
        }

        railContainer.setOnTouchListener { _, event ->
            railContainer.parent.requestDisallowInterceptTouchEvent(true)
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
            drawerAppContainer.addView(createAppRowView(app))
        }
        drawerScroll.smoothScrollTo(0, 0)
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
        renderFavoritesGrid()
        renderFolders()
        renderTasks()
        renderHouseWishlist()
    }

    private fun showAppOptions(app: AppItem) {
        val isFav = favoritePackages.contains(app.packageName)
        val favLabel = if (isFav) "Unpin from 2x3 Grid" else "Pin to 2x3 Grid (Max 6)"
        val opts = arrayOf(favLabel, "Add to Shelf")

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        if (isFav) favoritePackages.remove(app.packageName)
                        else if (favoritePackages.size < 6) favoritePackages.add(app.packageName)
                        else Toast.makeText(this, "2x3 Grid full (Max 6)", Toast.LENGTH_SHORT).show()
                        saveUserData()
                        renderFavoritesGrid()
                    }
                    1 -> {
                        val names = folders.map { it.name }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Select Shelf")
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
                if (names.isEmpty()) setMessage("Shelf empty. Long-press any app to bind it.")
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
            val icon = info.loadIcon(packageManager)
            val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.activityInfo?.applicationInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
            } else {
                ApplicationInfo.CATEGORY_UNDEFINED
            }
            list.add(AppItem(label, pName, icon, category))
        }
        return list.sortedBy { it.name.lowercase() }
    }
}
