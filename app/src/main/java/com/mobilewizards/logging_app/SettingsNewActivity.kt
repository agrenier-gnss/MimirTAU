package com.mobilewizards.logging_app
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout


class SettingsNewActivity : AppCompatActivity(),
    FirstSettingPage.SettingsFragmentListener,SecondSettingPage.SettingsFragmentListener {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_setting)
        supportActionBar?.hide()
        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        val viewPager = findViewById<ViewPager>(R.id.pager)
        val goBackButton = findViewById<Button>(R.id.button_back)
        val adapter = MyPagerAdapter(supportFragmentManager)
        viewPager.adapter = adapter

        tabLayout.setupWithViewPager(viewPager)
        goBackButton.setOnClickListener {onBackPressedDispatcher.onBackPressed();}


    }
    override fun onSaveSettings() {
        setResult(RESULT_OK)
        finish()
    }




}