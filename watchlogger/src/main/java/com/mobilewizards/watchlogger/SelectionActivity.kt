package com.mobilewizards.logging_app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import androidx.core.app.ActivityCompat
import com.mobilewizards.logging_app.databinding.ActivitySelectionBinding
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.Toast
import com.mobilewizards.watchlogger.SensorSettingsHandler
import org.json.JSONObject


class SelectionActivity: Activity() {

    private lateinit var binding: ActivitySelectionBinding

    private val SETTINGS_REQUEST_CODE = 1 // This is to wait for settings to be done before starting

    // ---------------------------------------------------------------------------------------------

    //BroadcastReceiver to handle settings JSON sent from the phone
    private val settingsJsonReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_RECEIVE_SETTINGS_JSON") {
                val settingsJson = intent.getStringExtra("settings_json")

                Toast.makeText(context, "Settings received", Toast.LENGTH_SHORT).show()

                settingsJson?.let {
                    val jsonObject = JSONObject(it)
                    Log.d("SelectionActivity", "Sending settings to be processed in settingsActivity")
                    SettingsActivity.processSettingsJson(context, jsonObject)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SensorSettingsHandler.initializePreferences(this)
        binding = ActivitySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register the receiver to listen for settings JSON
        registerReceiver(
            settingsJsonReceiver, IntentFilter("ACTION_RECEIVE_SETTINGS_JSON"), RECEIVER_EXPORTED
        )

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(settingsJsonReceiver)
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

// =============================================================================================

class WatchMessageListenerService: WearableListenerService() {

    private val SETTINGS_PATH = "/watch_settings" // Path to identify settings JSON

    override fun onMessageReceived(messageEvent: MessageEvent) {

        if (messageEvent.path == SETTINGS_PATH) {
            val settingsJson = String(messageEvent.data)
            Log.d("WatchMessageListener", "Received settings JSON: $settingsJson")

            val intent = Intent("ACTION_RECEIVE_SETTINGS_JSON")
            intent.putExtra("settings_json", settingsJson)
            sendBroadcast(intent)

        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}