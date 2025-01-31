package com.mimir.sensors

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class LoggingService: Service() {

    private val TAG = "LoggingService"
    private val channelId = "SensorLoggingChannelId"
    private val notificationId = 1
    private lateinit var sensorsHandler: SensorsHandler
    private lateinit var settingsMap: Map<SensorType, Pair<Boolean, Int>>

    private val sensorCheckHandler = Handler()
    private val checkSensorsRunnable = object: Runnable {
        override fun run() {
            // Perform the check of the sensor list every second
            checkSensorList()
            sensorCheckHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // ---------------------------------------------------------------------------------------------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start your sensor data logging logic here
        // This method will be called when the service is started

        // Build the notification

        val notification = buildNotification()

        // Recover the settings from intent
        settingsMap = intent?.getSerializableExtra("settings") as Map<SensorType, Pair<Boolean, Int>>
        Log.d(TAG, "Logging started, settings received: $settingsMap")

        // Start the service in the foreground
        startForeground(notificationId, notification)

        // Start logging
        startLogging(this)

        return START_STICKY
    }

    // ---------------------------------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy() {
        stopLogging(this)
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId, "Sensor Logging Channel", NotificationManager.IMPORTANCE_DEFAULT
        )

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    // ---------------------------------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, channelId).setContentTitle("Sensor Logging Service")
            .setContentText("Logging sensor data...")
            //.setSmallIcon(R.drawable.mimirlogo) // Replace with your own icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        return builder.build()
    }

    // ---------------------------------------------------------------------------------------------

    fun startLogging(context: Context) {

        // Register sensors
        sensorsHandler = SensorsHandler(context)

        // GNSS Sensor
        if (settingsMap[SensorType.TYPE_GNSS]?.first as Boolean) {
            sensorsHandler.addSensor(SensorType.TYPE_GNSS_LOCATION)
            sensorsHandler.addSensor(SensorType.TYPE_GNSS_MEASUREMENTS)
            sensorsHandler.addSensor(SensorType.TYPE_GNSS_MESSAGES)
            sensorsHandler.addSensor(SensorType.TYPE_FUSED_LOCATION)
        }

        // Motion sensors
        if (sensorEnabled(SensorType.TYPE_IMU)) {
            val frequency = settingFrequency(SensorType.TYPE_IMU)
            // Try to register uncalibrated sensors first, otherwise skip to standard version
            // Accelerometer
            sensorsHandler.addSensor(SensorType.TYPE_ACCELEROMETER_UNCALIBRATED, frequency)
            if (!sensorsHandler.mSensors.last().isAvailable) {
                sensorsHandler.mSensors.removeLast()
                sensorsHandler.addSensor(SensorType.TYPE_ACCELEROMETER, frequency)
            }
            // Gyroscope
            sensorsHandler.addSensor(SensorType.TYPE_GYROSCOPE_UNCALIBRATED, frequency)
            if (!sensorsHandler.mSensors.last().isAvailable) {
                sensorsHandler.mSensors.removeLast()
                sensorsHandler.addSensor(SensorType.TYPE_GYROSCOPE, frequency)
            }
            // Magnetometer
            sensorsHandler.addSensor(SensorType.TYPE_MAGNETIC_FIELD_UNCALIBRATED, frequency)
            if (!sensorsHandler.mSensors.last().isAvailable) {
                sensorsHandler.mSensors.removeLast()
                sensorsHandler.addSensor(SensorType.TYPE_MAGNETIC_FIELD, frequency)
            }
        }

        if (sensorEnabled(SensorType.TYPE_PRESSURE)) {
            val frequency = settingFrequency(SensorType.TYPE_PRESSURE)
            sensorsHandler.addSensor(SensorType.TYPE_PRESSURE, frequency)
        }

        if (sensorEnabled(SensorType.TYPE_STEPS)) {
            val frequency = settingFrequency(SensorType.TYPE_STEPS)
            sensorsHandler.addSensor(SensorType.TYPE_STEP_DETECTOR, frequency)
            sensorsHandler.addSensor(SensorType.TYPE_STEP_COUNTER, frequency)
        }

        // Health sensors
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            if (sensorEnabled(SensorType.TYPE_SPECIFIC_ECG)) {
                val frequency = settingFrequency(SensorType.TYPE_SPECIFIC_ECG)
                sensorsHandler.addSensor(SensorType.TYPE_SPECIFIC_ECG, frequency)
            }

            if (sensorEnabled(SensorType.TYPE_SPECIFIC_PPG)) {
                val frequency = settingFrequency(SensorType.TYPE_SPECIFIC_PPG)
                sensorsHandler.addSensor(SensorType.TYPE_SPECIFIC_PPG, frequency)
            }

            if (sensorEnabled(SensorType.TYPE_SPECIFIC_GSR)) {
                val frequency = settingFrequency(SensorType.TYPE_SPECIFIC_GSR)
                sensorsHandler.addSensor(SensorType.TYPE_SPECIFIC_GSR, frequency)
            }
        }

        sensorsHandler.startLogging()

        // For checking sensor status and showing on display
        sensorCheckHandler.postDelayed(checkSensorsRunnable, 1000)
    }

    // ---------------------------------------------------------------------------------------------

    private fun sensorEnabled(sensor: SensorType): Boolean {
        return settingsMap[sensor]?.first as Boolean
    }


    // ---------------------------------------------------------------------------------------------

    private fun settingFrequency(sensor: SensorType): Int {
        return (1.0 / (settingsMap[sensor]?.second as Int) * 1e6).toInt()
    }


    // ---------------------------------------------------------------------------------------------

    fun stopLogging(context: Context) {
        sensorsHandler.stopLogging()
        sensorCheckHandler.removeCallbacks(checkSensorsRunnable)
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    // ---------------------------------------------------------------------------------------------

    private fun checkSensorList() {
        val intent = Intent("SENSOR_CHECK_UPDATE")
        sensorsHandler.mSensors.forEach {
            intent.putExtra("${it.type}", it.isReceived)
        }
        sendBroadcast(intent)
    }
}
