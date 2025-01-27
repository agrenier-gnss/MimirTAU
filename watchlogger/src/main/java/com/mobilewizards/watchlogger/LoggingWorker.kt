package com.mobilewizards.watchlogger

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.mimir.sensors.BackgroundLoggingService
import com.mimir.sensors.SensorType
import java.io.Serializable


// Currently hidden in the layout XML
// TODO: this is commented out only because the feature is not fully implemented,

// Work in progress background recording / periodic recording
// Disabled since it still has few issues to solve
// it doesn't truly work in the background yet, so when the app is closed it doesn't record.
//
// It lacks some configurations, Currently it's hard coded to log for 60s every 15min
// but in the state it is now, it doesn't repeat, it only records for the 60s and does not
// go to new recording cycle.
//
// Also the feature is obscure enough to need documentation for the user, it's not as
// self explanatory as  the rest of the features.
//
// Background logging tasks are largely in the LoggingWorker and ScheduleManager, look into them
// for more info

class LoggingWorker(
    context: Context, workerParams: WorkerParameters
): Worker(context, workerParams) {
    //private val loggingIntent = Intent(context.applicationContext, LoggingService::class.java)
    // define static Sensor Settings
    private val staticSensorSettings: Map<SensorType, Pair<Boolean, Int>> = mapOf(
        SensorType.TYPE_GNSS to Pair(true, 100),
        SensorType.TYPE_IMU to Pair(true, 50),
        SensorType.TYPE_PRESSURE to Pair(false, 0),
        SensorType.TYPE_STEPS to Pair(true, 10),
        SensorType.TYPE_SPECIFIC_ECG to Pair(false, 0),
        SensorType.TYPE_SPECIFIC_PPG to Pair(false, 0),
        SensorType.TYPE_SPECIFIC_GSR to Pair(false, 0)
    )

    override fun doWork(): Result {
        Log.d("LoggingWorker", "Starting LoggingWorker...")

        // Step 1: Start LoggingService
        try {
            startLoggingService()
        } catch (e: Exception) {
            Log.e("LoggingWorker", "Error starting service: ${e.message}")
            return Result.failure()
        }

        // Step 2: Delay for 60 seconds (handled in background by WorkManager)
        Thread.sleep(60000)

        // Step 3: Stop LoggingService
        stopLoggingService()

        Log.d("LoggingWorker", "LoggingWorker finished execution.")
        return Result.success()
    }

    private fun startLoggingService() {
        Log.d("LoggingWorker", "Starting LoggingService...")
        val loggingIntent = Intent(applicationContext, BackgroundLoggingService::class.java)

        loggingIntent.putExtra("settings", staticSensorSettings as Serializable)

        // Start the service in the foreground
        ContextCompat.startForegroundService(applicationContext, loggingIntent)
    }

    private fun stopLoggingService() {
        Log.d("LoggingWorker", "Stopping LoggingService...")

        // Prepare the intent to stop LoggingService
        val loggingIntent = Intent(applicationContext, BackgroundLoggingService::class.java)
        applicationContext.stopService(loggingIntent)
    }
}