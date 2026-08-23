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
import android.view.animation.PathInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class EdgeScrubberService : Service() {

    private lateinit var windowManager: WindowManager
    private var edgeRailView: NativePixelWaveRailView? = null
    private var modalContainer: FrameLayout? = null
    private var sheetCard: LinearLayout? = null
    private var appListView: LinearLayout? = null
    private var appListScrollView: ScrollView? = null
    private var bubbleIndicator: TextView? = null

    // System Material 3 Charcoal Tokens
    private val COLOR_SCRIM = Color.parseColor("#80000000")              // 50% Soft Dim
    private val COLOR_SURFACE = Color.parseColor("#202024")            // Pixel Surface Container High
    private val COLOR_SURFACE_VARIANT = Color.parseColor("#2E2E34")    // Pixel Chip Fill
    private val COLOR_ON_SURFACE = Color.parseColor("#E6E1E5")         // Material 3 Bone White
    private val COLOR_TEXT_MUTED = Color.parseColor("#938F99")         // Material 3 Muted Slate
    private val COLOR_ACCENT_PILL = Color.parseColor("#4A4458")        // Active Letter Pill

    data class AppInfo(val name: String, val packageName: String, val icon: Drawable)
    private var installedApps: List<AppInfo> = emptyList()
    private var vibrator: Vibrator? = null

    private val pixelInterpolator = PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)

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
        buildSystemModalSheet()
        buildEdgeRail()
    }

    private fun startForegroundNotification() {
        val channelId = "pixel_edge_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pixel Edge Engine",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pixel Edge Running")
            .setContentText("Edge gesture active")
            .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1001, notification)
    }

    private fun triggerPixelHaptic() {
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

    @SuppressLint("RtlHardcoded")
    private fun buildSystemModalSheet() {
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
            setOnClickListener { hideSystemSheet() }
        }

        sheetCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE)
                cornerRadii = floatArrayOf(36f, 36f, 36f, 36f, 0f, 0f, 0f, 0f)
            }
            elevation = 28f
            setPadding(32, 20, 84, 32)
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.70).toInt(),
                Gravity.BOTTOM
            )
            layoutParams = lp
            isClickable = true
        }

        val handle = View(this).apply {
            background = GradientDrawable().apply {
                setColor(COLOR_SURFACE_VARIANT)
                cornerRadius = 8f
            }
            val lp = LinearLayout.LayoutParams(72, 8).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 18)
            }
            layoutParams = lp
        }
        sheetCard?.addView(handle)

        val header = TextView(this).apply {
            text = "Apps"
            setTextColor(COLOR_ON_SURFACE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(8, 0, 0, 16)
        }
        sheetCard?.addView(header)

        appListScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        appListView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        appListScrollView?.addView(appListView)
        sheetCard?.addView(appListScrollView)

        modalContainer?.addView(sheetCard)

        bubbleIndicator = TextView(this).apply {
            visibility = View.GONE
            setTextColor(COLOR_ON_SURFACE)
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(COLOR_ACCENT_PILL)
                cornerRadius = 32f
            }
            elevation = 32f
            layoutParams = FrameLayout.LayoutParams(108, 108, Gravity.END or Gravity.TOP).apply {
                marginEnd = 80
            }
        }
        modalContainer?.addView(bubbleIndicator)

        windowManager.addView(modalContainer, params)
    }

    private fun buildEdgeRail() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val railHeightPx = (density * 380).toInt()
        val railWidthPx = (density * 28).toInt()

        val params = WindowManager.LayoutParams(
            railWidthPx,
            railHeightPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            y = (density * 60).toInt()
        }

        edgeRailView = NativePixelWaveRailView(this) { char, rawY ->
            onLetterSelected(char, rawY)
        }

        windowManager.addView(edgeRailView, params)
    }

    private fun onLetterSelected(char: Char?, rawY: Float) {
        if (char == null) {
            bubbleIndicator?.visibility = View.GONE
            return
        }

        triggerPixelHaptic()
        showSystemSheet(char, rawY)
    }

    private fun showSystemSheet(filterChar: Char, rawY: Float) {
        if (modalContainer?.visibility != View.VISIBLE) {
            modalContainer?.visibility = View.VISIBLE
            modalContainer?.alpha = 0f
            sheetCard?.translationY = 300f
            modalContainer?.animate()?.alpha(1f)?.setDuration(150)?.setInterpolator(pixelInterpolator)?.start()
            sheetCard?.animate()?.translationY(0f)?.setDuration(220)?.setInterpolator(pixelInterpolator)?.start()
        }

        bubbleIndicator?.visibility = View.VISIBLE
        bubbleIndicator?.y = (rawY - 140).coerceIn(120f, (resources.displayMetrics.heightPixels - 260).toFloat())
        bubbleIndicator?.text = filterChar.toString()

        appListView?.removeAllViews()
        val filtered = if (filterChar == '•') installedApps else installedApps.filter { it.name.startsWith(filterChar, ignoreCase = true) }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No apps starting with '$filterChar'"
                setTextColor(COLOR_TEXT_MUTED)
                textSize = 14f
                setPadding(12, 28, 0, 0)
            }
            appListView?.addView(emptyTv)
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
                val size = 88
                layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(0, 0, 18, 0) }
            }
            row.addView(icon)

            val name = TextView(this).apply {
                text = app.name
                setTextColor(COLOR_ON_SURFACE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            }
            row.addView(name)

            row.setOnClickListener {
                triggerPixelHaptic()
                hideSystemSheet()
                val intent = packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                if (intent != null) startActivity(intent)
            }

            appListView?.addView(row)
        }
        appListScrollView?.scrollTo(0, 0)
    }

    private fun hideSystemSheet() {
        if (modalContainer?.visibility == View.VISIBLE) {
            sheetCard?.animate()?.translationY(300f)?.setDuration(160)?.setInterpolator(pixelInterpolator)?.start()
            modalContainer?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction {
                modalContainer?.visibility = View.GONE
                bubbleIndicator?.visibility = View.GONE
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

    inner class NativePixelWaveRailView(
        context: Context,
        private val onLetterScrubbed: (Char?, Float) -> Unit
    ) : View(context) {

        private val alphabet = listOf('•') + ('A'..'Z').toList()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        private var activeIdx = -1
        private var touchY = -1f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val totalItems = alphabet.size
            val stepY = height.toFloat() / totalItems

            for (i in alphabet.indices) {
                val baseY = (stepY * i) + (stepY / 2)
                val dist = if (touchY >= 0) abs(baseY - touchY) else 1000f
                val waveFactor = (1.0f - (dist / (height * 0.28f))).coerceIn(0f, 1f)

                val xOffset = - (waveFactor * 22f * resources.displayMetrics.density)
                val textSizeSp = 8.5f + (waveFactor * 5.5f)
                paint.textSize = textSizeSp * resources.displayMetrics.density

                if (i == activeIdx) {
                    paint.color = COLOR_ON_SURFACE
                    paint.alpha = 255
                } else {
                    paint.color = COLOR_TEXT_MUTED
                    paint.alpha = (90 + (waveFactor * 130)).toInt()
                }

                val centerX = (width * 0.65f) + xOffset
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

