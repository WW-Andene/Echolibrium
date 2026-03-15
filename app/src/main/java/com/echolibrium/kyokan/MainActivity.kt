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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
class MainActivity : AppCompatActivity() {

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab_id"
    }

    private val tabIds = intArrayOf(R.id.nav_home, R.id.nav_profiles, R.id.nav_apps, R.id.nav_rules, R.id.nav_logcat)
    private var selectedTabId = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        // L14: Apply saved theme preference before setContentView
        // I-07: Use repo for typed access
        val darkMode = container.repo.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)

        // Edge-to-edge: draw behind system bars (M25)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        // Light status bar icons in dark mode, dark icons in light mode
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !darkMode
        insetsController.isAppearanceLightNavigationBars = !darkMode

        CrashLogger.install(this)
        setContentView(R.layout.activity_main)
        for (id in tabIds) {
            findViewById<View>(id).setOnClickListener { selectTab(id) }
        }

        // B-02: Restore selected tab after rotation; B-03: re-select to sync nav + fragment
        val restoredTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB, R.id.nav_home) ?: R.id.nav_home
        selectTab(restoredTabId)

        // Edge-to-edge: apply status bar insets to fragment container top padding
        val fragmentContainer = findViewById<View>(R.id.fragment_container)
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Edge-to-edge: apply navigation bar insets to bottom nav
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_nav)
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bars.bottom)
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTabId)
    }

    private fun selectTab(id: Int) {
        // F-01: Check for unsaved changes before switching away from ProfilesFragment
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is UnsavedChangesCheck && currentFragment.hasUnsavedChanges() && id != selectedTabId) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.unsaved_changes_title))
                .setMessage(getString(R.string.unsaved_changes_message))
                .setPositiveButton(getString(R.string.discard)) { _, _ -> doSelectTab(id) }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
            return
        }
        doSelectTab(id)
    }

    private fun doSelectTab(id: Int) {
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

        // B-03: Use FragmentManager tags instead of a manual cache map.
        // findFragmentByTag() returns the FM-restored instance after rotation,
        // preventing duplicate fragments and stale reference leaks.
        val tag = tagForTab(id)
        val fragment = supportFragmentManager.findFragmentByTag(tag) ?: when (id) {
            R.id.nav_home     -> HomeFragment()
            R.id.nav_profiles -> ProfilesFragment()
            R.id.nav_apps     -> AppsFragment()
            R.id.nav_rules    -> RulesFragment()
            R.id.nav_logcat   -> LogcatFragment()
            else -> HomeFragment()
        }
        loadFragment(fragment, tag)
    }

    private fun tagForTab(id: Int): String = when (id) {
        R.id.nav_home     -> "frag_home"
        R.id.nav_profiles -> "frag_profiles"
        R.id.nav_apps     -> "frag_apps"
        R.id.nav_rules    -> "frag_rules"
        R.id.nav_logcat   -> "frag_logcat"
        else -> "frag_home"
    }

    private fun loadFragment(f: Fragment, tag: String) =
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, f, tag)
            .commit()

    fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, NotificationReaderService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
    }
}
