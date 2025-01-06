package com.mobilewizards.watchlogger

import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

object SchedulerManager {

    // entrance of schedule Periodic Logging
    fun schedulePeriodicLogging(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<LoggingWorker>(
            1, TimeUnit.MINUTES // set periodic intervals,minimum 15 minutes
        ).setInitialDelay(0, TimeUnit.SECONDS).build()

        /*
                val workRequest = PeriodicWorkRequestBuilder<LoggingWorker>(
                    15, TimeUnit.MINUTES
                ).build()
        */
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "SensorLoggingWork", ExistingPeriodicWorkPolicy.KEEP, // ensure that the present task won't be covered
            workRequest
        )
    }
}