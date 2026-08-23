package com.example.starlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_CODE = 4321

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.parseColor("#121214"))
        }

        val title = TextView(this).apply {
            text = "Pixel Edge Strip"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)

        val desc = TextView(this).apply {
            text = "Enables a thumb-accessible alphabet wave on the right bezel while keeping your stock Pixel launcher, widgets, and layout intact."
            textSize = 14f
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(0, 0, 0, 48)
            gravity = Gravity.CENTER
        }
        root.addView(desc)

        val btnStart = Button(this).apply {
            text = "Activate Edge Strip"
            setOnClickListener { checkAndStart() }
        }
        root.addView(btnStart)

        val btnStop = Button(this).apply {
            text = "Stop Edge Strip"
            setOnClickListener {
                stopService(Intent(this@MainActivity, EdgeScrubberService::class.java))
                Toast.makeText(this@MainActivity, "Edge Strip Stopped", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(btnStop)

        setContentView(root)
    }

    private fun checkAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQ_CODE)
        } else {
            startServiceIntent()
        }
    }

    private fun startServiceIntent() {
        val intent = Intent(this, EdgeScrubberService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Pixel Edge Active", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startServiceIntent()
            } else {
                Toast.makeText(this, "Permission required to draw overlay", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
