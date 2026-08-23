package com.example.starlauncher

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class EdgeScrubberService : Service() {

    private lateinit var windowManager: WindowManager
    private var edgeRailView: SketchbookAlphabetRailView? = null
    private var modalContainer: FrameLayout? = null
    private var centerFloatingCard: LinearLayout? = null
    private var appListView: LinearLayout? = null
    private var appListScrollView: ScrollView? = null
    private var floatingBubble: TextView? = null

    // --- PRISTINE WHITE SKETCHBOOK PALETTE ---
    private val COLOR_SCRIM = Color.parseColor("#4D000000")              // 30% Soft Dim Backdrop
    private val COLOR_SKETCH_WHITE = Color.parseColor("#FAFBFD")       // Pristine Paper White
    private val COLOR_INK_BLACK = Color.parseColor("#111113")          // Drafting Ink Black
    private val COLOR_INK_MUTED = Color.parseColor("#71717A")          // Graphite Gray
    private val COLOR_CARD_STROKE = Color.parseColor("#1F000000")        // 12% Hairline Ink Border
    private val COLOR_RAIL_BG = Color.parseColor("#1AFFFFFF")            // Subtle Frosted Rail Pill
    private val COLOR_RAIL_ACTIVE = Color.parseColor("#FFFFFF")         // Crisp Highlight

    data class AppInfo(val name: String, val packageName: String, val icon: Drawable)
    private var installedApps: List<AppInfo> = emptyList()
    private var vibrator: Vibrator? = null

    private val springInterpolator = OvershootInterpolator(0.85f)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        startForegroundNotification()
        loadApps()
        buildFloatingCenterCardOverlay()
        buildSketchbookRail()
    }

    private fun startForegroundNotification() {
        val channelId = "sketchbook_edge_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sketchbook Edge Engine",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sketchbook Edge Active")
            .setContentText("Slide right edge to open tools")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1001, notification)
    }

    private fun triggerSketchHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TEXTURE_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(6)
            }
        } catch (_: Exception) {}
    }

    // ================= FLOATING PRISTINE WHITE CENTER CARD =================
    @SuppressLint("RtlHardcoded")
    private fun buildFloatingCenterCardOverlay() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        modalContainer = FrameLayout(this).apply {
            setBackgroundColor(COLOR_SCRIM)
            visibility = View.GONE
            setOnClickListener { hideFloatingCenterCard() }
        }

        val displayMetrics = resources.displayMetrics
        val cardWidth = (displayMetrics.widthPixels * 0.84).toInt()
        val cardHeight = (displayMetrics.heightPixels * 0.56).toInt()

        centerFloatingCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_SKETCH_WHITE)
                cornerRadius = 36f
                setStroke(2, COLOR_CARD_STROKE)
            }
            elevation = 42f
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

        val cardTitle = TextView(this).apply {
            text = "SKETCHBOOK CODEX"
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
        cardHeaderRow.addView(cardTitle)
        cardHeaderRow.addView(closePrompt)
        centerFloatingCard?.addView(cardHeaderRow)

        appListScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        appListView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appListScrollView?.addView(appListView)
        centerFloatingCard?.addView(appListScrollView)

        modalContainer?.addView(centerFloatingCard)

        floatingBubble = TextView(this).apply {
            visibility = View.GONE
            setTextColor(COLOR_INK_BLACK)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(COLOR_SKETCH_WHITE)
                cornerRadius = 32f
                setStroke(2, COLOR_CARD_STROKE)
            }
            elevation = 50f
            layoutParams = FrameLayout.LayoutParams(110, 110, Gravity.END or Gravity.TOP).apply {
                marginEnd = 68
            }
        }
        modalContainer?.addView(floatingBubble)

        windowManager.addView(modalContainer, params)
    }

    // ================= WIDGET-LIKE EDGE RAIL =================
    private fun buildSketchbookRail() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val railHeightPx = (density * 360).toInt()
        val railWidthPx = (density * 32).toInt()

        val params = WindowManager.LayoutParams(
            railWidthPx,
            railHeightPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            y = (density * 100).toInt()
        }

        edgeRailView = SketchbookAlphabetRailView(this) { char, rawY ->
            onLetterSelected(char, rawY)
        }

        windowManager.addView(edgeRailView, params)
    }

    private fun onLetterSelected(char: Char?, rawY: Float) {
        if (char == null) {
            floatingBubble?.visibility = View.GONE
            return
        }

        triggerSketchHaptic()
        showFloatingCenterCard(char, rawY)
    }

    private fun showFloatingCenterCard(filterChar: Char, rawY: Float) {
        if (modalContainer?.visibility != View.VISIBLE) {
            modalContainer?.visibility = View.VISIBLE
            modalContainer?.alpha = 0f
            centerFloatingCard?.scaleX = 0.82f
            centerFloatingCard?.scaleY = 0.82f
            modalContainer?.animate()?.alpha(1f)?.setDuration(120)?.start()
            centerFloatingCard?.animate()
                ?.scaleX(1.0f)
                ?.scaleY(1.0f)
                ?.setDuration(220)
                ?.setInterpolator(springInterpolator)
                ?.start()
        }

        floatingBubble?.visibility = View.VISIBLE
        floatingBubble?.y = (rawY - 140).coerceIn(120f, (resources.displayMetrics.heightPixels - 260).toFloat())
        floatingBubble?.text = filterChar.toString()

        appListView?.removeAllViews()
        val filtered = if (filterChar == '•') installedApps else installedApps.filter { it.name.startsWith(filterChar, ignoreCase = true) }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No tools starting with '$filterChar'"
                setTextColor(COLOR_INK_MUTED)
                textSize = 13f
                typeface = Typeface.MONOSPACE
                setPadding(12, 32, 0, 0)
            }
            appListView?.addView(emptyTv)
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
                triggerSketchHaptic()
                hideFloatingCenterCard()
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                if (intent != null) startActivity(intent)
            }

            appListView?.addView(row)
        }
        appListScrollView?.scrollTo(0, 0)
    }

    private fun hideFloatingCenterCard() {
        if (modalContainer?.visibility == View.VISIBLE) {
            centerFloatingCard?.animate()?.scaleX(0.85f)?.scaleY(0.85f)?.setDuration(140)?.start()
            modalContainer?.animate()?.alpha(0f)?.setDuration(140)?.withEndAction {
                modalContainer?.visibility = View.GONE
                floatingBubble?.visibility = View.GONE
            }?.start()
        }
    }

    private fun loadApps() {
        val list = mutableListOf<AppInfo>()
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
            list.add(AppInfo(label, pName, icon))
        }
        installedApps = list.sortedBy { it.name.lowercase() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (edgeRailView != null) windowManager.removeView(edgeRailView)
        if (modalContainer != null) windowManager.removeView(modalContainer)
    }

    // ================= SKETCHBOOK PARABOLIC WAVE CANVAS =================
    inner class SketchbookAlphabetRailView(
        context: Context,
        private val onLetterScrubbed: (Char?, Float) -> Unit
    ) : View(context) {

        private val alphabet = listOf('•') + ('A'..'Z').toList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        private val railBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_RAIL_BG
        }

        private var activeIdx = -1
        private var touchY = -1f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 16f, 16f, railBgPaint)

            val totalItems = alphabet.size
            val stepY = height.toFloat() / totalItems

            for (i in alphabet.indices) {
                val baseY = (stepY * i) + (stepY / 2)

                val dist = if (touchY >= 0) abs(baseY - touchY) else 1000f
                val waveFactor = (1.0f - (dist / (height * 0.26f))).coerceIn(0f, 1f)

                val xOffset = - (waveFactor * 24f * resources.displayMetrics.density)
                val textSizeSp = 8.5f + (waveFactor * 6.0f)
                paint.textSize = textSizeSp * resources.displayMetrics.density

                if (i == activeIdx) {
                    paint.color = COLOR_RAIL_ACTIVE
                    paint.alpha = 255
                } else {
                    paint.color = Color.parseColor("#D4D4D8")
                    paint.alpha = (90 + (waveFactor * 130)).toInt()
                }

                val centerX = (width * 0.5f) + xOffset
                val textY = baseY + (paint.textSize / 3f)
                canvas.drawText(alphabet[i].toString(), centerX, textY, paint)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchY = event.y.coerceIn(0f, height.toFloat() - 1)
                    val idx = ((touchY / height) * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                    if (idx != activeIdx) {
                        activeIdx = idx
                        onLetterScrubbed(alphabet[idx], event.rawY)
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeIdx = -1
                    touchY = -1f
                    invalidate()
                    onLetterScrubbed(null, 0f)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
