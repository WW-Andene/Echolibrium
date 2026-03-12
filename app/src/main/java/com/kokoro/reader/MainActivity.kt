package com.kokoro.reader

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private val tabIds = intArrayOf(R.id.nav_home, R.id.nav_profiles, R.id.nav_apps, R.id.nav_rules)
    private var selectedTabId = R.id.nav_home

    // Cached fragments — show/hide instead of recreating on every tab switch
    private val fragments = mutableMapOf<Int, Fragment>()
    private var activeFragmentTag: String? = null

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for pending crash report from previous session
        if (savedInstanceState == null) {
            val crashIntent = (application as? ReaderApplication)?.consumePendingCrashReport()
            if (crashIntent != null) {
                startActivity(crashIntent)
                // Don't finish — CrashReportActivity will navigate back here
            }
        }

        setContentView(R.layout.activity_main)

        // If rapid HWUI/GPU crashes were detected, switch to software rendering.
        // Breaks the crash loop on Xiaomi/MediaTek where Mali GPU mutex gets corrupted
        // under memory pressure and stays dirty across process restarts.
        if (ReaderApplication.hwuiSafeMode) {
            Log.w("MainActivity", "HWUI safe mode — using software rendering")
            findViewById<View>(R.id.fragment_container)
                .setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        // Restore existing fragments after configuration change
        if (savedInstanceState != null) {
            for (id in tabIds) {
                val tag = tagForTab(id)
                supportFragmentManager.findFragmentByTag(tag)?.let { fragments[id] = it }
            }
        }

        for (id in tabIds) {
            findViewById<View>(id).setOnClickListener { selectTab(id) }
        }
        if (savedInstanceState == null) {
            selectTab(R.id.nav_home)
        } else {
            val restoredTab = savedInstanceState.getInt(KEY_SELECTED_TAB, R.id.nav_home)
            selectTab(restoredTab)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTabId)
    }

    private fun selectTab(id: Int) {
        try {
            if (isFinishing || isDestroyed) return
            selectedTabId = id
            val activeColor = androidx.core.content.ContextCompat.getColor(this, R.color.green)
            val inactiveColor = androidx.core.content.ContextCompat.getColor(this, R.color.nav_inactive)
            for (tabId in tabIds) {
                val tab = findViewById<LinearLayout>(tabId)
                val color = if (tabId == id) activeColor else inactiveColor
                for (i in 0 until tab.childCount) {
                    when (val child = tab.getChildAt(i)) {
                        is ImageView -> child.setColorFilter(color)
                        is TextView  -> child.setTextColor(color)
                    }
                }
            }
            showFragment(id)
        } catch (e: Exception) {
            val tabName = tabName(id)
            android.util.Log.e("MainActivity", "Error switching to tab '$tabName'", e)
            try { android.widget.Toast.makeText(this, "Failed to open $tabName", android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
        }
    }

    private fun tagForTab(id: Int): String = "tab_$id"

    private fun createFragmentForTab(id: Int): Fragment = when (id) {
        R.id.nav_home     -> HomeFragment()
        R.id.nav_profiles -> ProfilesFragment()
        R.id.nav_apps     -> AppsFragment()
        R.id.nav_rules    -> RulesFragment()
        else -> HomeFragment()
    }

    private fun showFragment(tabId: Int) {
        try {
            if (isFinishing || isDestroyed) return
            val tag = tagForTab(tabId)
            val fm = supportFragmentManager
            val tx = fm.beginTransaction()

            // Hide the currently active fragment
            activeFragmentTag?.let { activeTag ->
                fm.findFragmentByTag(activeTag)?.let { tx.hide(it) }
            }

            // Show or create the target fragment
            var target = fragments[tabId]
            if (target == null) {
                target = createFragmentForTab(tabId)
                fragments[tabId] = target
                tx.add(R.id.fragment_container, target, tag)
            } else {
                tx.show(target)
            }

            activeFragmentTag = tag
            tx.commitAllowingStateLoss()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error showing fragment for tab ${tabName(tabId)}", e)
            try { android.widget.Toast.makeText(this, "Failed to load page", android.widget.Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
        }
    }

    private fun tabName(id: Int): String = when (id) {
        R.id.nav_home     -> "Home"
        R.id.nav_profiles -> "Voice"
        R.id.nav_apps     -> "Apps"
        R.id.nav_rules    -> "Words"
        else              -> "Unknown"
    }

    fun isNotificationAccessGranted(): Boolean {
        return try {
            val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
            enabled.contains(packageName)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking notification access", e)
            false
        }
    }
}
