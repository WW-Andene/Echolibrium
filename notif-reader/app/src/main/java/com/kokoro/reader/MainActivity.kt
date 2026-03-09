package com.kokoro.reader

import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener {
            loadFragment(when (it.itemId) {
                R.id.nav_home     -> HomeFragment()
                R.id.nav_profiles -> ProfilesFragment()
                R.id.nav_apps     -> AppsFragment()
                R.id.nav_rules    -> RulesFragment()
                else -> HomeFragment()
            }); true
        }
        if (savedInstanceState == null) loadFragment(HomeFragment())
    }
    private fun loadFragment(f: Fragment) =
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, f).commit()

    fun isNotificationAccessGranted(): Boolean {
        val cn = ComponentName(this, NotificationReaderService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return !TextUtils.isEmpty(flat) && flat.contains(cn.flattenToString())
    }
}
