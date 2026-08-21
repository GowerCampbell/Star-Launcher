package com.example.starlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0D14"))
            setPadding(48, 80, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val header = TextView(this).apply {
            text = "★ STAR LAUNCHER"
            setTextColor(Color.parseColor("#10B988"))
            textSize = 20f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 24)
        }
        root.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val appContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val apps = loadInstalledApps()
        for (app in apps) {
            val tv = TextView(this).apply {
                text = ">  ${app.name}"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 15f
                typeface = Typeface.MONOSPACE
                setPadding(0, 20, 0, 20)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    try {
                        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        } else {
                            Toast.makeText(this@MainActivity, "Cannot open app", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Launch failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            appContainer.addView(tv)
        }

        scroll.addView(appContainer)
        root.addView(scroll)

        setContentView(root)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    data class AppItem(val name: String, val packageName: String)

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
            list.add(AppItem(label, pName))
        }
        return list.sortedBy { it.name.lowercase() }
    }
}
