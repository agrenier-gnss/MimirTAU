package com.mobilewizards.logging_app

import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout


class SettingsActivity: AppCompatActivity(),
    // split settings view for Phone specific settings and watch settings on phone
    WatchSettingPage.SettingsFragmentListener, PhoneSettingPage.SettingsFragmentListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        supportActionBar?.hide()
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        val viewPager = findViewById<ViewPager>(R.id.pager)
        val goBackButton = findViewById<FrameLayout>(R.id.button_back)
        val adapter = MyPagerAdapter(supportFragmentManager)
        viewPager.adapter = adapter

        tabLayout.setupWithViewPager(viewPager)
        goBackButton.setOnClickListener { onBackPressedDispatcher.onBackPressed(); }

    }

    override fun onSaveSettings() {
        setResult(RESULT_OK)
        finish()
    }


}