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
        if (settingsMap[SensorType.TYPE_IMU]?.first as Boolean) {
            val frequency = (1.0 / (settingsMap[SensorType.TYPE_IMU]?.second as Int) * 1e6).toInt()
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

        if (settingsMap[SensorType.TYPE_PRESSURE]?.first as Boolean) {
            val frequency = (1.0 / (settingsMap[SensorType.TYPE_PRESSURE]?.second as Int) * 1e6).toInt()
            sensorsHandler.addSensor(SensorType.TYPE_PRESSURE, frequency)
        }

        if (settingsMap[SensorType.TYPE_STEPS]?.first as Boolean) {
            val frequency = (1.0 / (settingsMap[SensorType.TYPE_STEPS]?.second as Int) * 1e6).toInt()
            sensorsHandler.addSensor(SensorType.TYPE_STEP_DETECTOR, frequency)
            sensorsHandler.addSensor(SensorType.TYPE_STEP_COUNTER, frequency)
        }

        // Health sensors
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            if (settingsMap[SensorType.TYPE_SPECIFIC_ECG]?.first as Boolean) {
                val frequency = (1.0 / (settingsMap[SensorType.TYPE_SPECIFIC_ECG]?.second as Int) * 1e6).toInt()
                sensorsHandler.addSensor(SensorType.TYPE_SPECIFIC_ECG, frequency)
            }

            if (settingsMap[SensorType.TYPE_SPECIFIC_PPG]?.first as Boolean) {
                val frequency = (1.0 / (settingsMap[SensorType.TYPE_SPECIFIC_PPG]?.second as Int) * 1e6).toInt()
                sensorsHandler.addSensor(SensorType.TYPE_SPECIFIC_PPG, frequency)
            }

            if (settingsMap[SensorType.TYPE_SPECIFIC_GSR]?.first as Boolean) {
                val frequency = (1.0 / (settingsMap[SensorType.TYPE_SPECIFIC_GSR]?.second as Int) * 1e6).toInt()
                sensorsHandler.addSensor(SensorType.TYPE_SPECIFIC_GSR, frequency)
            }
        }

        sensorsHandler.startLogging()

        // For checking sensor status and showing on display
        sensorCheckHandler.postDelayed(checkSensorsRunnable, 1000)
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
