package com.echolibrium.kyokan

import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private val tabIds = intArrayOf(R.id.nav_home, R.id.nav_profiles, R.id.nav_apps, R.id.nav_rules, R.id.nav_logcat)
    private var selectedTabId = R.id.nav_home

    // Cache fragment instances to avoid recreation on every tab click (A3)
    private val fragmentCache = mutableMapOf<Int, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_main)
        for (id in tabIds) {
            findViewById<View>(id).setOnClickListener { selectTab(id) }
        }
        if (savedInstanceState == null) selectTab(R.id.nav_home)
    }

    private fun selectTab(id: Int) {
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
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, f).commit()

    fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, NotificationReaderService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
    }
}
