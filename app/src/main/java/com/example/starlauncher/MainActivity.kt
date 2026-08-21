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
import kotlin.math.abs

class MainActivity : Activity() {

    private val gson = Gson()
    private val PREFS = "StarBeastRelayPrefs"
    private val KEY_FAVS = "key_inner_circle"
    private val KEY_FOLDERS = "key_astral_tomes"
    private val KEY_TASKS = "key_directives"

    // --- ELDRITCH CYBERPUNK PALETTE ---
    private val VOID_BLACK = Color.parseColor("#05070A")        // Deep cosmic charcoal
    private val SHADOW_SURFACE = Color.parseColor("#0D1219")    // Obsidian card slate
    private val JADE_PHOSPHOR = Color.parseColor("#10B981")     // Eldritch signal phosphor
    private val AMBER_EMBER = Color.parseColor("#D97706")       // Dying star ember
    private val PARCHMENT_HIGH = Color.parseColor("#F1F5F9")    // Dyslexia-safe bone white
    private val TEXT_ASH = Color.parseColor("#64748B")          // Muted ash gray
    private val BORDER_SUBTLE = Color.parseColor("#1E293B")     // Thin containment rim

    data class AppItem(val name: String, val packageName: String, val icon: Drawable?)
    data class FolderItem(val name: String, val glyph: String, val packages: MutableList<String>)
    data class TaskItem(val id: Long, val text: String, var isDone: Boolean)

    private var allApps: List<AppItem> = emptyList()
    private var favoritePackages = mutableListOf<String>()
    private var folders = mutableListOf<FolderItem>()
    private var taskList = mutableListOf<TaskItem>()

    private lateinit var homeStationLayout: LinearLayout
    private lateinit var drawerLayout: FrameLayout
    private lateinit var drawerScroll: ScrollView
    private lateinit var drawerAppContainer: LinearLayout
    private lateinit var drawerSectorTv: TextView
    private lateinit var favoritesContainer: LinearLayout
    private lateinit var foldersContainer: LinearLayout
    private lateinit var tasksContainer: LinearLayout

    private lateinit var beaconTimeTv: TextView
    private lateinit var beaconDateTv: TextView

    private lateinit var railContainer: LinearLayout
    private val alphabet = listOf('★') + ('A'..'Z').toList()
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
        buildAstralInterface()
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
        val dateFormat = SimpleDateFormat("EEEE // dd.MM.yyyy", Locale.getDefault())
        if (::beaconTimeTv.isInitialized) beaconTimeTv.text = timeFormat.format(Date())
        if (::beaconDateTv.isInitialized) beaconDateTv.text = "☄ ASTRAL CYCLE: ${dateFormat.format(Date()).uppercase()}"
    }

    private fun pulseHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(15)
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
                FolderItem("Codex & Reading", "🕮", mutableListOf()),
                FolderItem("Occult Dev Terminal", "⚙", mutableListOf()),
                FolderItem("Star Beast Comms", "⛯", mutableListOf())
            )
        }
        taskList = if (taskJson != null) {
            gson.fromJson(taskJson, object : TypeToken<MutableList<TaskItem>>() {}.type)
        } else {
            mutableListOf(
                TaskItem(1, "Commune with Star Relays", false),
                TaskItem(2, "Catalogue Elder Artifacts", false)
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

    private fun buildAstralInterface() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(VOID_BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ================= HOME SANCTUARY =================
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

        // 1. BEACON GLANCE
        homeStationLayout.addView(createBeaconHeader())

        // 2. ASTRAL AUDIO DECK (Spotify / Media)
        homeStationLayout.addView(createAudioRelay())

        // 3. TASK GRIMOIRE
        homeStationLayout.addView(createDirectiveDesk())

        // 4. INNER CIRCLE (FAVORITES)
        val favHeader = TextView(this).apply {
            text = "★ THE INNER CIRCLE [RESONANT BEACONS]"
            setTextColor(AMBER_EMBER)
            textSize = 11f
            letterSpacing = 0.18f
            typeface = Typeface.MONOSPACE
            setPadding(0, 36, 0, 16)
        }
        homeStationLayout.addView(favHeader)

        favoritesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeStationLayout.addView(favoritesContainer)

        // 5. TOMES OF THE ARCHIVE
        val folderHeader = TextView(this).apply {
            text = "🜏 FORBIDDEN ARCHIVES [TOMES]"
            setTextColor(JADE_PHOSPHOR)
            textSize = 11f
            letterSpacing = 0.18f
            typeface = Typeface.MONOSPACE
            setPadding(0, 36, 0, 16)
        }
        homeStationLayout.addView(folderHeader)

        foldersContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeStationLayout.addView(foldersContainer)

        stationScroll.addView(homeStationLayout)
        root.addView(stationScroll)

        // ================= APP DRAWER OVERLAY =================
        drawerLayout = FrameLayout(this).apply {
            setBackgroundColor(VOID_BLACK)
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

        drawerSectorTv = TextView(this).apply {
            text = "SECTOR // CONSTELLATION"
            setTextColor(JADE_PHOSPHOR)
            textSize = 18f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 24)
        }
        drawerContent.addView(drawerSectorTv)

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
        root.addView(drawerLayout)

        // ================= FLUID FISHEYE RAIL =================
        val waveRail = buildFisheyeAlphabetRail()
        root.addView(waveRail)

        setContentView(root)
        refreshAll()
    }

    private fun createBeaconHeader(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = createShadowTile(SHADOW_SURFACE, 20f)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 24)
            layoutParams = lp
        }

        beaconTimeTv = TextView(this).apply {
            setTextColor(PARCHMENT_HIGH)
            textSize = 38f
            letterSpacing = 0.05f
            typeface = Typeface.MONOSPACE
        }

        beaconDateTv = TextView(this).apply {
            setTextColor(JADE_PHOSPHOR)
            textSize = 11f
            letterSpacing = 0.15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 4, 0, 0)
        }

        card.addView(beaconTimeTv)
        card.addView(beaconDateTv)
        updateClock()
        return card
    }

    private fun createAudioRelay(): View {
        val capsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 20, 28, 20)
            background = createShadowTile(SHADOW_SURFACE, 20f)
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
            text = "⛯ STAR BEAST RESONATOR"
            setTextColor(AMBER_EMBER)
            textSize = 10f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
        }
        val sub = TextView(this).apply {
            text = "Tap to invoke Spotify Portal"
            setTextColor(TEXT_ASH)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        labelLayout.addView(title)
        labelLayout.addView(sub)
        capsule.addView(labelLayout)

        val playBtn = TextView(this).apply {
            text = "▶  ⏸"
            setTextColor(PARCHMENT_HIGH)
            textSize = 16f
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
        capsule.addView(playBtn)

        capsule.setOnClickListener {
            pulseHaptic()
            val spotifyIntent = packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (spotifyIntent != null) startActivity(spotifyIntent)
            else Toast.makeText(this, "Relay unreachable: Spotify missing", Toast.LENGTH_SHORT).show()
        }

        return capsule
    }

    private fun createDirectiveDesk(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            background = createShadowTile(SHADOW_SURFACE, 20f)
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
            text = "🜂 CODEX DIRECTIVES"
            setTextColor(JADE_PHOSPHOR)
            textSize = 11f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val addBtn = TextView(this).apply {
            text = "[ + ENGRAVE ]"
            setTextColor(AMBER_EMBER)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(16, 8, 16, 8)
            setOnClickListener { showAddDirectiveDialog() }
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

    private fun renderDirectives() {
        tasksContainer.removeAllViews()
        for (task in taskList) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 12, 0, 12)
            }
            val glyph = TextView(this).apply {
                text = if (task.isDone) "🜏" else "☿"
                setTextColor(if (task.isDone) JADE_PHOSPHOR else TEXT_ASH)
                textSize = 15f
                setPadding(0, 0, 20, 0)
            }
            val tv = TextView(this).apply {
                text = task.text
                setTextColor(if (task.isDone) TEXT_ASH else PARCHMENT_HIGH)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                if (task.isDone) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            row.addView(glyph)
            row.addView(tv)

            row.setOnClickListener {
                pulseHaptic()
                task.isDone = !task.isDone
                saveUserData()
                renderDirectives()
            }
            row.setOnLongClickListener {
                pulseHaptic()
                taskList.remove(task)
                saveUserData()
                renderDirectives()
                true
            }
            tasksContainer.addView(row)
        }
    }

    private fun showAddDirectiveDialog() {
        val input = EditText(this).apply {
            hint = "Inscribe directive into codex..."
            setTextColor(PARCHMENT_HIGH)
            setHintTextColor(TEXT_ASH)
        }
        AlertDialog.Builder(this)
            .setTitle("Engrave Directive")
            .setView(input)
            .setPositiveButton("Seal") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) {
                    taskList.add(TaskItem(System.currentTimeMillis(), txt, false))
                    saveUserData()
                    renderDirectives()
                }
            }
            .setNegativeButton("Abort", null)
            .show()
    }

    // ================= DYNAMIC WAVE / FISHEYE RAIL =================
    @SuppressLint("ClickableViewAccessibility")
    private fun buildFisheyeAlphabetRail(): View {
        railContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(80, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
            setPadding(0, 56, 8, 56)
        }

        railViews.clear()
        for (c in alphabet) {
            val tv = TextView(this).apply {
                text = c.toString()
                setTextColor(TEXT_ASH)
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

                    applyFisheyeTransformation(targetIdx)

                    if (targetIdx != lastHoverIndex) {
                        lastHoverIndex = targetIdx
                        pulseHaptic()
                        val selectedChar = alphabet[targetIdx]
                        if (selectedChar == '★') hideAppDrawer()
                        else showAppDrawer(selectedChar)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resetFisheyeRail()
                    true
                }
                else -> true
            }
        }
        return railContainer
    }

    private fun applyFisheyeTransformation(centerIndex: Int) {
        for (i in railViews.indices) {
            val view = railViews[i]
            val distance = abs(i - centerIndex)
            when (distance) {
                0 -> {
                    view.textSize = 22f
                    view.setTextColor(JADE_PHOSPHOR)
                    view.translationX = -32f
                }
                1 -> {
                    view.textSize = 15f
                    view.setTextColor(AMBER_EMBER)
                    view.translationX = -18f
                }
                2 -> {
                    view.textSize = 12f
                    view.setTextColor(PARCHMENT_HIGH)
                    view.translationX = -8f
                }
                else -> {
                    view.textSize = 9f
                    view.setTextColor(TEXT_ASH)
                    view.translationX = 0f
                }
            }
        }
    }

    private fun resetFisheyeRail() {
        for (view in railViews) {
            view.textSize = 10f
            view.setTextColor(TEXT_ASH)
            view.translationX = 0f
        }
        lastHoverIndex = -1
    }

    private fun showAppDrawer(filterChar: Char? = null) {
        if (drawerLayout.visibility != View.VISIBLE) {
            drawerLayout.visibility = View.VISIBLE
            drawerLayout.animate().alpha(1f).setDuration(160).setInterpolator(DecelerateInterpolator()).start()
        }
        filterAndRenderDrawer(filterChar)
    }

    private fun hideAppDrawer() {
        if (drawerLayout.visibility == View.VISIBLE) {
            drawerLayout.animate().alpha(0f).setDuration(130).withEndAction {
                drawerLayout.visibility = View.GONE
            }.start()
        }
    }

    private fun filterAndRenderDrawer(filterChar: Char?) {
        drawerAppContainer.removeAllViews()
        val filtered = if (filterChar == null) allApps else allApps.filter { it.name.startsWith(filterChar, ignoreCase = true) }

        drawerSectorTv.text = if (filterChar != null) "CONSTELLATION // SECTOR $filterChar" else "ALL SANCTIONED TRANSMISSIONS"

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No astral signatures detected in sector '$filterChar'"
                setTextColor(TEXT_ASH)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(0, 32, 0, 0)
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
        renderDirectives()
    }

    private fun renderFavorites() {
        favoritesContainer.removeAllViews()
        val favs = allApps.filter { favoritePackages.contains(it.packageName) }.take(6)
        if (favs.isEmpty()) {
            val hint = TextView(this).apply {
                text = "> Glide along the star rail to manifest apps. Long press to bind to the Inner Circle."
                setTextColor(TEXT_ASH)
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
                background = createShadowTile(SHADOW_SURFACE, 16f)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(0, 8, 0, 8)
                layoutParams = lp
            }

            val icon = TextView(this).apply {
                text = folder.glyph
                textSize = 19f
                setTextColor(JADE_PHOSPHOR)
                setPadding(0, 0, 24, 0)
            }
            val title = TextView(this).apply {
                text = "${folder.name} [${folder.packages.size}]"
                setTextColor(PARCHMENT_HIGH)
                textSize = 15f
                letterSpacing = 0.05f
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
            setPadding(0, 22, 0, 22)
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
            setTextColor(PARCHMENT_HIGH)
            textSize = 15f
            letterSpacing = 0.04f
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
        val favLabel = if (isFav) "Sever from Inner Circle" else "Bind to Inner Circle (Max 6)"
        val opts = arrayOf(favLabel, "Assign to Codex Archive")

        AlertDialog.Builder(this)
            .setTitle(app.name)
            .setItems(opts) { _, which ->
                when (which) {
                    0 -> {
                        if (isFav) favoritePackages.remove(app.packageName)
                        else if (favoritePackages.size < 6) favoritePackages.add(app.packageName)
                        else Toast.makeText(this, "The Inner Circle is full (Max 6)", Toast.LENGTH_SHORT).show()
                        saveUserData()
                        renderFavorites()
                    }
                    1 -> {
                        val names = folders.map { "${it.glyph} ${it.name}" }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Select Archive Tome")
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
            .setTitle("${folder.glyph} ${folder.name}")
            .apply {
                if (names.isEmpty()) setMessage("Archive empty. Long-press apps to bind them here.")
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
            Toast.makeText(this, "Portal severed: Unable to launch", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createShadowTile(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(1, BORDER_SUBTLE)
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
