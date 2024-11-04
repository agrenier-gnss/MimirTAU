package com.mobilewizards.logging_app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.core.app.ActivityCompat
import com.mobilewizards.logging_app.databinding.ActivitySelectionBinding

class SelectionActivity: Activity() {

    private lateinit var binding: ActivitySelectionBinding

    private val SETTINGS_REQUEST_CODE = 1 // This is to wait for settings to be done before starting

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        this.checkPermissions()

        val startSurveyBtn = findViewById<Button>(R.id.startSurveyBtn)
        val settingsBtn = findViewById<Button>(R.id.settingsBtn)
        val sendSurveysBtn = findViewById<Button>(R.id.sendSurveysBtn)

        startSurveyBtn.setOnClickListener {
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivityForResult(settingsIntent, SETTINGS_REQUEST_CODE)
        }

        settingsBtn.setOnClickListener {
            val openSettings = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(openSettings)
        }

        sendSurveysBtn.setOnClickListener {
            val openSendSurveys = Intent(this, SendSurveysActivity::class.java)
            startActivity(openSendSurveys)
        }


    }

    // =============================================================================================

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SETTINGS_REQUEST_CODE) {
            // Handle the result from the SettingsActivity
            if (resultCode == RESULT_OK) {
                // The user successfully changed settings
                val loggingIntent = Intent(this, LoggingActivity::class.java)
                startActivity(loggingIntent)
            } else {
                // The user canceled or there was an issue with settings
                // Handle accordingly or take appropriate action
            }
        }
    }

    // =============================================================================================

    // Makes sure all permissions are granted
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.BODY_SENSORS,
        )

        var allPermissionsGranted = true
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false
                break
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, 225)
        }
    }
}