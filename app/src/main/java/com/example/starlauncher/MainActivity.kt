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
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
    private val PREFS = "CharcoalWorkstationPrefs"
    private val KEY_FAVS = "key_pinned_grid"
    private val KEY_FOLDERS = "key_folders"
    private val KEY_TASKS = "key_tasks"

    // --- MONOCHROME CHARCOAL PALETTE ---
    private val COLOR_TRANSPARENT = Color.TRANSPARENT
    private val COLOR_CHARCOAL_SURFACE = Color.parseColor("#E6121215") // 90% opaque graphite
    private val COLOR_CHARCOAL_LIT = Color.parseColor("#F527272A")     // Active press state
    private val COLOR_WHITE = Color.parseColor("#FFFFFF")              // Pure white
    private val COLOR_TEXT = Color.parseColor("#E4E4E7")               // Warm bone white
    private val COLOR_MUTED = Color.parseColor("#71717A")              // Balanced ash gray
    private val COLOR_BORDER = Color.parseColor("#27272A")             // Defined graphite rim

    data class AppItem(val name: String, val packageName: String, val icon: Drawable?)
    data class FolderItem(val name: String, val packages: MutableList<String>)
    data class TaskItem(val id: Long, val text: String, var isDone: Boolean)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var folders = mutableListOf<FolderItem>()
    private var taskList = mutableListOf<TaskItem>()

    private lateinit var rootLayout: FrameLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicatorTv: TextView

    // Drawer elements
    private lateinit var drawerLayout: FrameLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerTitleTv: TextView

    // References for dynamic updates
    private var timeTv: TextView? = null
    private var dateTv: TextView? = null
    private var sleepCountdownTv: TextView? = null
    private var trackTitleTv: TextView? = null
    private var trackArtistTv: TextView? = null
    private var favoritesGridLayout: GridLayout? = null
    private var foldersRow: LinearLayout? = null
    private var tasksContainer: LinearLayout? = null

    // Alphabet Rail
    private lateinit var railContainer: LinearLayout
    private lateinit var floatingBadge: TextView
    private val alphabet = listOf('•') + ('A'..'Z').toList()
    private val railViews = mutableListOf<TextView>()
    private var lastHoverIndex = -1

    private var vibrator: Vibrator? = null

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
        registerReceiver(systemReceiver, filter)
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

        sleepCountdownTv?.text = "TARGET WAKE 03:45 (${diffHours}h ${diffMinutes}m remaining)"
    }

    private fun arm0345Alarm() {
        pulseHaptic()
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, 3)
            putExtra(AlarmClock.EXTRA_MINUTES, 45)
            putExtra(AlarmClock.EXTRA_MESSAGE, "03:45 Station Wake")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Clock app not accessible", Toast.LENGTH_SHORT).show()
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

        favoritePackages = if (favJson != null) gson.fromJson(favJson, object : TypeToken<MutableList<String>>() {}.type) else mutableListOf()
        folders = if (folderJson != null) {
            gson.fromJson(folderJson, object : TypeToken<MutableList<FolderItem>>() {}.type)
        } else {
            mutableListOf(
                FolderItem("Writing & Notes", mutableListOf()),
                FolderItem("Dev & Shell", mutableListOf()),
                FolderItem("Media & Audio", mutableListOf()),
                FolderItem("Transit & Tools", mutableListOf())
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
    }

    private fun saveUserData() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FAVS, gson.toJson(favoritePackages))
            .putString(KEY_FOLDERS, gson.toJson(folders))
            .putString(KEY_TASKS, gson.toJson(taskList))
            .apply()
    }

    private fun buildSlideInterface() {
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
                val view = if (viewType == 0) buildMainStationPage() else buildDirectivesPage()
                return object : RecyclerView.ViewHolder(view) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                // Binding is maintained dynamically
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                pulseHaptic()
                pageIndicatorTv.text = if (position == 0) "[ STATION • DIRECTIVES ]" else "[ STATION • DIRECTIVES* ]"
            }
        })

        rootLayout.addView(viewPager)

        pageIndicatorTv = TextView(this).apply {
            text = "[ STATION • DIRECTIVES ]"
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
            setBackgroundColor(COLOR_CHARCOAL_SURFACE)
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
            background = createCardDrawable(COLOR_CHARCOAL_LIT, 24f, COLOR_WHITE)
            layoutParams = FrameLayout.LayoutParams(116, 116, Gravity.END or Gravity.TOP).apply {
                marginEnd = 88
            }
            elevation = 18f
        }
        rootLayout.addView(floatingBadge)

        // ================= ALPHABET RAIL =================
        val alphabetRail = buildAlphabetRail()
        rootLayout.addView(alphabetRail)

        // Edge-to-edge insets
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
        content.addView(createOmniSearch())
        content.addView(createAudioDeck())

        val favHeader = TextView(this).apply {
            text = "CORE TOOLS (2×3)"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            letterSpacing = 0.16f
            typeface = Typeface.MONOSPACE
            setPadding(0, 18, 0, 10)
        }
        content.addView(favHeader)

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

        val foldHeader = TextView(this).apply {
            text = "WORKSPACE SHELVES"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            letterSpacing = 0.16f
            typeface = Typeface.MONOSPACE
            setPadding(0, 10, 0, 10)
        }
        content.addView(foldHeader)

        val foldersContainer = HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 18)
            layoutParams = lp
            setOnTouchListener { _, event ->
                // Disallow ViewPager from intercepting horizontal shelf scroll
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

    private fun buildDirectivesPage(): View {
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

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
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
            background = createCardDrawable(COLOR_CHARCOAL_SURFACE, 10f, COLOR_BORDER)
            setOnClickListener { showAddTaskDialog() }
        }
        headerRow.addView(title)
        headerRow.addView(addBtn)
        content.addView(headerRow)

        val tasks = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 0)
        }
        tasksContainer = tasks
        content.addView(tasks)

        scroll.addView(content)
        renderTasks()
        return scroll
    }

    private fun createChronoAndSleepBeacon(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(26, 22, 26, 22)
            background = createCardDrawable(COLOR_CHARCOAL_SURFACE, 18f, COLOR_BORDER)
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

        val armAlarmBtn = TextView(this).apply {
            text = "[ ARM 03:45 ]"
            setTextColor(COLOR_WHITE)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(12, 6, 12, 6)
            background = createCardDrawable(COLOR_CHARCOAL_LIT, 10f, COLOR_WHITE)
            setOnClickListener { arm0345Alarm() }
        }

        sleepRow.addView(sTv)
        sleepRow.addView(armAlarmBtn)

        card.addView(tTv)
        card.addView(dTv)
        card.addView(sleepRow)
        updateClockAndSleepData()
        return card
    }

    private fun createOmniSearch(): View {
        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(22, 12, 22, 12)
            background = createCardDrawable(COLOR_CHARCOAL_SURFACE, 14f, COLOR_BORDER)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 14)
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
            hint = "Search web, notes, or apps..."
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
            background = createIlluminatedState(COLOR_CHARCOAL_SURFACE, COLOR_CHARCOAL_LIT, 16f, COLOR_WHITE)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 14)
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
            background = createCardDrawable(COLOR_CHARCOAL_LIT, 8f, COLOR_BORDER)
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
                background = createCardDrawable(COLOR_CHARCOAL_SURFACE, 14f, COLOR_BORDER)
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
                text = "No pinned tools. Long-press apps to bind (Max 6)."
                setTextColor(COLOR_MUTED)
                textSize = 12f
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
                background = createIlluminatedState(COLOR_CHARCOAL_SURFACE, COLOR_CHARCOAL_LIT, 14f, COLOR_BORDER)
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
                background = createCardDrawable(COLOR_CHARCOAL_SURFACE, 14f, COLOR_BORDER)
                val lp = LinearLayout.LayoutParams(320, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 12, 0)
                }
                layoutParams = lp
                isClickable = true
                isFocusable = true
            }

            val header = TextView(this).apply {
                text = folder.name.uppercase()
                setTextColor(COLOR_WHITE)
                textSize = 11f
                letterSpacing = 0.14f
                typeface = Typeface.MONOSPACE
            }
            val count = TextView(this).apply {
                text = "${folder.packages.size} items sealed"
                setTextColor(COLOR_MUTED)
                textSize = 11f
                setPadding(0, 4, 0, 0)
            }

            panel.addView(header)
            panel.addView(count)

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
            background = createIlluminatedState(Color.TRANSPARENT, COLOR_CHARCOAL_LIT, 12f, COLOR_WHITE)
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
            layoutParams = FrameLayout.LayoutParams(56, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
            setPadding(0, 52, 8, 52)
        }

        railViews.clear()
        for (c in alphabet) {
            val tv = TextView(this).apply {
                text = c.toString()
                setTextColor(COLOR_MUTED)
                textSize = 10f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
                setPadding(2, 1, 2, 1)
            }
            railViews.add(tv)
            railContainer.addView(tv)
        }

        railContainer.setOnTouchListener { _, event ->
            // Prevent ViewPager2 from intercepting vertical rail scrub
            railContainer.parent.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val y = event.y.coerceIn(0f, railContainer.height.toFloat())
                    val normalized = y / railContainer.height
                    val targetIdx = (normalized * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)

                    floatingBadge.visibility = View.VISIBLE
                    val badgeY = (event.rawY - 180).coerceIn(120f, (rootLayout.height - 240).toFloat())
                    floatingBadge.y = badgeY

                    val selectedChar = alphabet[targetIdx]
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
            list.add(AppItem(label, pName, icon))
        }
        return list.sortedBy { it.name.lowercase() }
    }
}
