package com.echolibrium.kyokan

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

class MainActivity : AppCompatActivity() {

    private val tabIds = intArrayOf(R.id.nav_home, R.id.nav_profiles, R.id.nav_apps, R.id.nav_rules, R.id.nav_logcat)
    private var selectedTabId = R.id.nav_home

    // Cache fragment instances to avoid recreation on every tab click (A3)
    private val fragmentCache = mutableMapOf<Int, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        // L14: Apply saved theme preference before setContentView
        val darkMode = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_main)
        for (id in tabIds) {
            findViewById<View>(id).setOnClickListener { selectTab(id) }
        }
        if (savedInstanceState == null) selectTab(R.id.nav_home)

        // Edge-to-edge: apply system bar insets to bottom nav (M25)
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_nav)
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }
    }

    private fun selectTab(id: Int) {
        val previousTabId = selectedTabId
        selectedTabId = id
        val activeColor = AppColors.primary(this)
        val inactiveColor = AppColors.navInactive(this)
        for (tabId in tabIds) {
            val tab = findViewById<LinearLayout>(tabId)
            val fromColor: Int
            val toColor: Int
            when {
                tabId == id && tabId != previousTabId -> { fromColor = inactiveColor; toColor = activeColor }
                tabId == previousTabId && tabId != id -> { fromColor = activeColor; toColor = inactiveColor }
                tabId == id -> { fromColor = activeColor; toColor = activeColor }
                else -> { fromColor = inactiveColor; toColor = inactiveColor }
            }
            if (fromColor != toColor) {
                ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
                    duration = 200
                    addUpdateListener { anim ->
                        val color = anim.animatedValue as Int
                        for (i in 0 until tab.childCount) {
                            when (val child = tab.getChildAt(i)) {
                                is ImageView -> child.setColorFilter(color)
                                is TextView -> child.setTextColor(color)
                            }
                        }
                    }
                    start()
                }
            } else {
                for (i in 0 until tab.childCount) {
                    when (val child = tab.getChildAt(i)) {
                        is ImageView -> child.setColorFilter(toColor)
                        is TextView -> child.setTextColor(toColor)
                    }
                }
            }
        }
        val fragment = fragmentCache.getOrPut(id) {
            when (id) {
                R.id.nav_home     -> HomeFragment()
                R.id.nav_profiles -> ProfilesFragment()
                R.id.nav_apps     -> AppsFragment()
                R.id.nav_rules    -> RulesFragment()
                R.id.nav_logcat   -> LogcatFragment()
                else -> HomeFragment()
            }
        }
        loadFragment(fragment)
    }

    private fun loadFragment(f: Fragment) =
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, f)
            .commit()

    fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, NotificationReaderService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
    }
}
