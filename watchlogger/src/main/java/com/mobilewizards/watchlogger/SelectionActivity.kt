package com.mobilewizards.logging_app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.core.app.ActivityCompat
import com.google.android.gms.wearable.Wearable
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
            //TODO: move to separate Class file/own function for clean code
            val nodeClient = Wearable.getNodeClient(applicationContext)
            //val dialogView = LayoutInflater.from(this).inflate(R.layout.send_survey_to_phone_pair_checker, null)

            // Disable the button to prevent multiple clicks while checking for nodes
            sendSurveysBtn.isEnabled = false

            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                val validWearableNodes = nodes.filter { node ->
                    node.isNearby
                }

                if (validWearableNodes.isEmpty()) {
                    Log.e("pairing", "No paired device found.")
                    //TODO: proper layout for dialog
                    AlertDialog.Builder(this@SelectionActivity)
                        //.setView(dialogView)
                        .setTitle("Pairing Error")
                        .setMessage("No paired smartphone found. Please pair before " +
                                "attempting file transfer")
                        //boot leg version of button before proper xml version is implemented
                        .setPositiveButton("            OK (clickhere)") { dialog, _ ->
                            dialog.dismiss()

                            sendSurveysBtn.isEnabled = true
                            val openSendSurveys = Intent(this, SendSurveysActivity::class.java)
                            startActivity(openSendSurveys)
                        }
                        .show()

                } else {
                    Log.d("pairing", "Device is paired and connected.")
                    val openSendSurveys = Intent(this, SendSurveysActivity::class.java)
                    startActivity(openSendSurveys)
                }
            }.addOnFailureListener { exception ->
                Log.e("pairing", "Failed to get connected nodes: ${exception.message}")
                sendSurveysBtn.isEnabled = true
            }
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