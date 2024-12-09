package com.mobilewizards.logging_app

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mimir.sensors.LoggingService
import com.mimir.sensors.SensorType
import com.mobilewizards.logging_app.databinding.ActivityLoggingBinding
import com.mobilewizards.watchlogger.SensorSettingsHandler
import java.io.Serializable
import java.util.concurrent.TimeUnit

var startTime: Long = 0

// =================================================================================================

// Class for main screen UI and activity when logging data
class LoggingActivity: Activity() {

    private lateinit var binding: ActivityLoggingBinding

    private val durationHandler = Handler()

    // Logging service
    private lateinit var loggingIntent: Intent

    // Components
    private lateinit var startLogBtn: Button
    private lateinit var stopLogBtn: Button
    private lateinit var logText: TextView
    private lateinit var logTimeText: TextView

    private var sensorTextViewList = mutableMapOf<SensorType, TextView>()

    // ---------------------------------------------------------------------------------------------

    private val sensorCheckReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SENSOR_CHECK_UPDATE") {

                // Lists all the sensors in the survey screen and adds enabled / disabled symbols to them
                sensorTextViewList.forEach { entry ->
                    if (!intent.hasExtra("${entry.key}")) {
                        return@forEach
                    }

                    // gets what sensors are enabled
                    val sensorCheck = intent.getBooleanExtra("${entry.key}", false)
                    val checkmarkSymbol = "\u2714"
                    val crossSymbol = "\u2716"

                    // set the sensor green check if enabled or red cross if disabled
                    if (sensorCheck) {
                        val colorID = ContextCompat.getColor(
                            applicationContext, android.R.color.holo_green_light
                        )
                        entry.value.text = checkmarkSymbol
                        entry.value.setTextColor(colorID)
                    } else {
                        val colorID = ContextCompat.getColor(
                            applicationContext, android.R.color.holo_red_light
                        )
                        entry.value.text = crossSymbol
                        entry.value.setTextColor(colorID)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoggingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        this.checkPermissions()

        // Set views
        startLogBtn = findViewById(R.id.startLogBtn)
        stopLogBtn = findViewById(R.id.stopLogBtn)
        logText = findViewById(R.id.logInfoText)
        logTimeText = findViewById(R.id.logTimeText)
        startLogBtn.visibility = View.VISIBLE
        stopLogBtn.visibility = View.GONE
        logText.visibility = View.GONE
        logTimeText.visibility = View.GONE

        sensorTextViewList = mutableMapOf(
            SensorType.TYPE_GNSS_MEASUREMENTS to findViewById(R.id.tv_gnss_raw_check),
            SensorType.TYPE_GNSS_LOCATION to findViewById(R.id.tv_gnss_pos_check),
            SensorType.TYPE_GNSS_MESSAGES to findViewById(R.id.tv_gnss_nav_check),
            SensorType.TYPE_ACCELEROMETER to findViewById(R.id.tv_imu_acc_check),
            SensorType.TYPE_ACCELEROMETER_UNCALIBRATED to findViewById(R.id.tv_imu_acc_check),
            SensorType.TYPE_GYROSCOPE to findViewById(R.id.tv_imu_gyr_check),
            SensorType.TYPE_GYROSCOPE_UNCALIBRATED to findViewById(R.id.tv_imu_gyr_check),
            SensorType.TYPE_MAGNETIC_FIELD to findViewById(R.id.tv_imu_mag_check),
            SensorType.TYPE_MAGNETIC_FIELD_UNCALIBRATED to findViewById(R.id.tv_imu_mag_check),
            SensorType.TYPE_PRESSURE to findViewById(R.id.tv_baro_check),
            SensorType.TYPE_STEP_DETECTOR to findViewById(R.id.tv_steps_detect_check),
            SensorType.TYPE_STEP_COUNTER to findViewById(R.id.tv_steps_counter_check),
            SensorType.TYPE_SPECIFIC_ECG to findViewById(R.id.tv_ecg_check),
            SensorType.TYPE_SPECIFIC_PPG to findViewById(R.id.tv_ppg_check),
            SensorType.TYPE_SPECIFIC_GSR to findViewById(R.id.tv_gsr_check)
        )

        // Set service
        loggingIntent = Intent(this, LoggingService::class.java)

        // starts logging if start button is clicked
        startLogBtn.setOnClickListener {
            startLogging(this)
        }

        // stops logging if stop button is clicked
        stopLogBtn.setOnClickListener {
            stopLogging()
        }

        // Register broadcaster
        registerReceiver(sensorCheckReceiver, IntentFilter("SENSOR_CHECK_UPDATE"), RECEIVER_NOT_EXPORTED)

//        // Initialize the variable sensorManager
//        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
//        // getSensorList(Sensor.TYPE_ALL) lists all the sensors present in the device
//        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
//
//        for (sensors in deviceSensors) {
//            Log.d("sensors", sensors.toString() + "\n")
//        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun startLogging(context: Context) {

        // Set duration timer
        startTime = SystemClock.elapsedRealtime()
        updateDurationText()
        durationHandler.postDelayed(updateRunnableDuration, 1000)

        // Set buttons
        startLogBtn.visibility = View.GONE
        stopLogBtn.visibility = View.VISIBLE
        logText.visibility = View.VISIBLE
        logText.text = "Surveying..."
        logTimeText.visibility = View.VISIBLE


        val sensorsSelected = SensorSettingsHandler.loadSensorValues()

        Log.d("sensors", "$sensorsSelected")

        // Set the data to be sent to service
        loggingIntent.putExtra("settings", sensorsSelected as Serializable)

        // Start service
        ContextCompat.startForegroundService(this, loggingIntent)
    }

    // ---------------------------------------------------------------------------------------------

    private fun stopLogging() {

        stopLogBtn.visibility = View.GONE
        logTimeText.visibility = View.GONE
        logText.text = "Survey ended"
        // Stop logging service
        stopService(loggingIntent)

        // Wait 2 seconds an go back to main screen
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 2000)
    }

    // ---------------------------------------------------------------------------------------------

    // Makes sure all permissions are granted
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
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

    // Calls the clock text to be updated every second
    private val updateRunnableDuration = object: Runnable {
        override fun run() {
            // Update the duration text every second
            updateDurationText()

            // Schedule the next update
            durationHandler.postDelayed(this, 1000)
        }
    }

    // updates the text on the clock
    private fun updateDurationText() {
        // Calculate the elapsed time since the button was clicked
        val elapsedTime = SystemClock.elapsedRealtime() - startTime

        // Format the duration as HH:MM:SS
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60

        // Display the formatted duration in the TextView
        val durationText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        logTimeText.text = durationText
    }

    override fun onDestroy() {
        // Remove the updateRunnable when the activity is destroyed to prevent memory leaks
        unregisterReceiver(sensorCheckReceiver)
        durationHandler.removeCallbacks(updateRunnableDuration)
        super.onDestroy()
    }
}