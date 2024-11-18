package com.mobilewizards.logging_app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mimir.sensors.LoggingService
import com.mimir.sensors.SensorType
import java.io.Serializable
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// =================================================================================================

class MainActivity : AppCompatActivity() {

    private val durationHandler = Handler()
    private var startTime = SystemClock.elapsedRealtime()
    private lateinit var timeText : TextView

    private lateinit var loggingButton: Button
    private lateinit var settingsBtn: Button
    private lateinit var dataButton: Button
    private lateinit var loggingText: TextView

    lateinit var loggingIntent : Intent

    private lateinit var sharedPreferences: SharedPreferences

    private var sensorTextViewList = mutableMapOf<SensorType, TextView>()

    // ---------------------------------------------------------------------------------------------

    private val sensorCheckReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SENSOR_CHECK_UPDATE") {

                sensorTextViewList.forEach{ entry ->
                    if(!intent.hasExtra("${entry.key}")){
                        return@forEach
                    }
                    val sensorCheck = intent.getBooleanExtra("${entry.key}", false)
                    if(sensorCheck){
                        val colorID = ContextCompat.getColor(applicationContext,
                            android.R.color.holo_green_light)
                        entry.value.text = "\u2714"
                        entry.value.setTextColor(colorID)
                    }else{
                        val colorID = ContextCompat.getColor(applicationContext,
                            android.R.color.holo_red_light)
                        entry.value.text = "\u2716"
                        entry.value.setTextColor(colorID)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()

        timeText = findViewById(R.id.logging_time_text_view)

        // Prevent logging button from going to unintended locations
        if(ActivityHandler.isLogging()) {

            dataButton.visibility = View.GONE
            loggingButton.text = "Stop logging"

            loggingButton.translationY = 250f

            Handler().postDelayed({
                loggingText.text = "Surveying..."
                timeText.text = "Started ${ActivityHandler.getSurveyStartTime()}"
            }, 300)
        } else {

            val layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )

            layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID

            loggingButton.layoutParams = layoutParams
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.hide()

        // Create communication with the watch
        val channelClient = Wearable.getChannelClient(applicationContext)
        channelClient.registerChannelCallback(object : ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {

                val receiveTask = channelClient.receiveFile(channel, ("file:///storage/emulated/0/Download/log_watch_received_${
                    LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"))}.csv").toUri(), false)
                receiveTask.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("channel", "File successfully stored")
                    } else {
                        Log.e("channel", "File receival/saving failed: ${task.exception}")
                    }
                }
            }
        })
        this.checkPermissions()

        loggingButton = findViewById(R.id.logging_button)
        settingsBtn   = findViewById(R.id.settings_button)
        dataButton    = findViewById(R.id.download_data_button)
        loggingText   = findViewById(R.id.logging_text_view)

        sensorTextViewList = mutableMapOf(
            SensorType.TYPE_GNSS_MEASUREMENTS           to findViewById(R.id.tv_gnss_raw_check),
            SensorType.TYPE_GNSS_LOCATION               to findViewById(R.id.tv_gnss_pos_check),
            SensorType.TYPE_GNSS_MESSAGES               to findViewById(R.id.tv_gnss_nav_check),
            SensorType.TYPE_ACCELEROMETER               to findViewById(R.id.tv_imu_acc_check),
            SensorType.TYPE_ACCELEROMETER_UNCALIBRATED  to findViewById(R.id.tv_imu_acc_check),
            SensorType.TYPE_GYROSCOPE                   to findViewById(R.id.tv_imu_gyr_check),
            SensorType.TYPE_GYROSCOPE_UNCALIBRATED      to findViewById(R.id.tv_imu_gyr_check),
            SensorType.TYPE_MAGNETIC_FIELD              to findViewById(R.id.tv_imu_mag_check),
            SensorType.TYPE_MAGNETIC_FIELD_UNCALIBRATED to findViewById(R.id.tv_imu_mag_check),
            SensorType.TYPE_PRESSURE                    to findViewById(R.id.tv_baro_check),
            SensorType.TYPE_STEP_DETECTOR               to findViewById(R.id.tv_steps_detect_check),
            SensorType.TYPE_STEP_COUNTER                to findViewById(R.id.tv_steps_counter_check)
        )

        var isInitialLoad = true

        // Set service
        loggingIntent = Intent(this, LoggingService::class.java)

        //if logging button is toggled in other activities, it is also toggled in here.
        loggingButton.setOnClickListener {
            ActivityHandler.toggleButton(this)
        }

        dataButton.setOnClickListener {
            val intent = Intent(this, SurveyHistoryActivity::class.java)
            startActivity(intent)
        }

        ActivityHandler.getButtonState().observe(this) { isPressed ->
            loggingButton.isSelected = isPressed

            // Check if app has just started and skip toggled off code
            if (isInitialLoad) {
                isInitialLoad = false
                return@observe
            }

            if(isPressed) {
                startLogging(this)
            } else {
                stopLogging()
            }
        }

        // Set settings button
        settingsBtn.setOnClickListener{
            val openSettings = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(openSettings)
        }

        ActivityHandler.sensorsSelected = mutableMapOf()
        sharedPreferences = getSharedPreferences("DefaultSettings", Context.MODE_PRIVATE)
        if (sharedPreferences.contains("GNSS")) {
            var mparam = loadMutableList("GNSS")
            ActivityHandler.sensorsSelected[SensorType.TYPE_GNSS] = Pair(
                mparam[0] as Boolean, (mparam[1] as Double).toInt())
            mparam = loadMutableList("IMU")
            ActivityHandler.sensorsSelected[SensorType.TYPE_IMU] = Pair(
                mparam[0] as Boolean, (mparam[1] as Double).toInt())
            mparam = loadMutableList("PSR")
            ActivityHandler.sensorsSelected[SensorType.TYPE_PRESSURE] = Pair(
                mparam[0] as Boolean, (mparam[1] as Double).toInt())
            mparam = loadMutableList("STEPS")
            ActivityHandler.sensorsSelected[SensorType.TYPE_STEPS] = Pair(
                mparam[0] as Boolean, (mparam[1] as Double).toInt())
        }

        // Register broadcoaster
        registerReceiver(sensorCheckReceiver, IntentFilter("SENSOR_CHECK_UPDATE"), RECEIVER_NOT_EXPORTED)
    }

    private fun loadMutableList(key:String): MutableList<String> {
        val jsonString = sharedPreferences.getString(key, "")
        val type: Type = object : TypeToken<MutableList<Any>>() {}.type

        return Gson().fromJson(jsonString, type) ?: mutableListOf()
    }

    // ---------------------------------------------------------------------------------------------

    fun startLogging(context: Context){

        startTime = SystemClock.elapsedRealtime()
        findViewById<Button>(R.id.logging_button).text = "Stop logging"
        dataButton.visibility = View.GONE
        settingsBtn.visibility = View.GONE
        loggingButton.animate()
            .translationYBy(250f)
            .setDuration(500)
            .start()

        loggingText.text = "Surveying..."

        // Set duration timer
        startTime = SystemClock.elapsedRealtime()
        updateDurationText()
        durationHandler.postDelayed(updateRunnableDuration, 1000)

        // Set the data to be sent to service
        loggingIntent.putExtra("settings", ActivityHandler.sensorsSelected as Serializable)

        // Start service
        ContextCompat.startForegroundService(this, loggingIntent)
    }

    // ---------------------------------------------------------------------------------------------

    fun stopLogging(){

        // Stop logging
        settingsBtn.visibility = View.VISIBLE
        findViewById<Button>(R.id.logging_button).text = "Start logging"
        loggingText.text = ""
        timeText.text = ""
        durationHandler.removeCallbacks(updateRunnableDuration)
        loggingButton.animate()
            .translationYBy(-250f)
            .setDuration(200)
            .start()

        Handler().postDelayed({ dataButton.visibility = View.VISIBLE }, 100)

        // Stop logging service
        stopService(loggingIntent)
    }

    // ---------------------------------------------------------------------------------------------

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_LOCATION
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

    // ---------------------------------------------------------------------------------------------

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {

            //location permission
            225 -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission is granted. Continue the action or workflow
                    // in your app.
                } else {
                    // Explain to the user that the feature is unavailable
                    AlertDialog.Builder(this)
                        .setTitle("Location permission denied")
                        .setMessage("Permission is denied.")
                        .setPositiveButton("OK",null)
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private val updateRunnableDuration = object : Runnable {
        override fun run() {
            // Update the duration text every second
            updateDurationText()

            // Schedule the next update
            durationHandler.postDelayed(this, 1000)
        }
    }

    private fun updateDurationText() {
        // Calculate the elapsed time since the button was clicked
        val currentTime = SystemClock.elapsedRealtime()
        val elapsedTime = currentTime - startTime

        // Format the duration as HH:MM:SS
        val hours = (elapsedTime / 3600000).toInt()
        val minutes = ((elapsedTime % 3600000) / 60000).toInt()
        val seconds = ((elapsedTime % 60000) / 1000).toInt()

        // Display the formatted duration in the TextView
        val durationText = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        timeText.text = "$durationText"
    }

    override fun onDestroy() {
        // Remove the updateRunnable when the activity is destroyed to prevent memory leaks
        unregisterReceiver(sensorCheckReceiver)
        durationHandler.removeCallbacks(updateRunnableDuration)
        super.onDestroy()
    }
}

// =================================================================================================