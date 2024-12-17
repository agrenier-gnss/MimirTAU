package com.mobilewizards.logging_app

import android.Manifest
import android.app.Activity
import android.app.Application
import android.widget.ProgressBar
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.android.material.snackbar.Snackbar
import com.mimir.sensors.LoggingService
import com.mimir.sensors.SensorType
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val checkMark = "\u2714"
private const val crossMark = "\u2716"


// =================================================================================================

class MainActivity: AppCompatActivity() {

    private val durationHandler = Handler()
    private var startTime = SystemClock.elapsedRealtime()
    private lateinit var timeText: TextView

    private lateinit var loggingButton: Button
    private lateinit var settingsBtn: Button
    private lateinit var dataButton: Button
    private lateinit var loggingText: TextView
    private lateinit var file: File
    private var receivedFileName: String? = null
    private var receivedFileSize: Long? = null

    lateinit var loggingIntent: Intent

    private var sensorTextViewList = mutableMapOf<SensorType, TextView>()

    private val fileAccessLock = Object()

    // ---------------------------------------------------------------------------------------------

    private val sensorCheckReceiver = object: BroadcastReceiver() {
        // receives updates for sensor status every second and updates the sensor status with
        // red X or green checkmark depending on if it's on or not
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "SENSOR_CHECK_UPDATE") {

                sensorTextViewList.forEach { entry ->
                    if (!intent.hasExtra("${entry.key}")) {
                        return@forEach
                    }
                    val sensorCheck = intent.getBooleanExtra("${entry.key}", false)
                    if (sensorCheck) {
                        val colorID = ContextCompat.getColor(
                            applicationContext, android.R.color.holo_green_light
                        )
                        entry.value.text = checkMark
                        entry.value.setTextColor(colorID)
                    } else {
                        val colorID = ContextCompat.getColor(
                            applicationContext, android.R.color.holo_red_light
                        )
                        entry.value.text = crossMark
                        entry.value.setTextColor(colorID)
                    }
                }
            }
        }
    }
    // ---------------------------------------------------------------------------------------------

    private val checksumReceiver = object: BroadcastReceiver() {
        // receives file checksum for the files sent from the watch
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_VERIFY_CHECKSUM") {
                Log.d("verifyChecksum", "verify checksum broadcast")
                val receivedChecksum = intent.getStringExtra("checksum")
                if (receivedChecksum != null) {
                    verifyChecksum(context, file, receivedChecksum)
                } else {
                    Log.w("verifyChecksum", "Null checksum received")
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private val fileNameReceiver = object: BroadcastReceiver() {
        // receives filename for the files sent from the watch
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("fileRenameReceive", "file rename broadcast")
            if (intent.action == "ACTION_RENAME_FILE") {
                receivedFileName = intent.getStringExtra("filename")
                Log.d("fileRenameReceive", "Original file name received: $receivedFileName")
            }
        }
    }
    // ---------------------------------------------------------------------------------------------

    private val fileSizeReceiver = object: BroadcastReceiver() {
        // receives file size for the files sent from the watch
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("fileSizeReceive", "file size broadcast")
            if (intent.action == "ACTION_SET_FILE_SIZE") {
                val fileSizeString = intent.getStringExtra("fileSize")
                receivedFileSize = fileSizeString?.trim()?.toLongOrNull()
                Log.d("fileSizeReceive", "Original file size received: $receivedFileSize")
            }
        }
    }
    // ---------------------------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        timeText = findViewById(R.id.logging_time_text_view)

        // Prevent logging button from going to unintended locations
        if (ActivityHandler.isLogging()) {

            dataButton.visibility = View.GONE
            loggingButton.text = "Stop logging"

            loggingButton.translationY = 250f

            Handler().postDelayed({
                loggingText.text = "Surveying..."
                timeText.text = "Started ${ActivityHandler.getSurveyStartTime()}"
            }, 300)
        } else {

            val layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT
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

        // Register the receiver to listen for checksum broadcasts
        registerReceiver(
            checksumReceiver,
            IntentFilter("ACTION_VERIFY_CHECKSUM"),
            RECEIVER_EXPORTED // doesn't work with RECEIVER_NOT_EXPORTED
        )

        // Register the receiver to listen for filename broadcasts
        registerReceiver(
            fileNameReceiver,
            IntentFilter("ACTION_RENAME_FILE"),
            RECEIVER_EXPORTED // doesn't work with RECEIVER_NOT_EXPORTED
        )

        // Register the receiver to listen for file size broadcasts
        registerReceiver(
            fileSizeReceiver,
            IntentFilter("ACTION_SET_FILE_SIZE"),
            RECEIVER_EXPORTED // doesn't work with RECEIVER_NOT_EXPORTED
        )

        this.checkPermissions()

        // init settings handlers
        PhoneSensorSettingsHandler.initializePreferences(this)
        WatchSensorSettingsHandler.initializePreferences(this)

        // Create communication with the watch
        val channelClient = Wearable.getChannelClient(applicationContext)
        channelClient.registerChannelCallback(object: ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                val downloadsDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"))
                val tempFileName = "log_watch_received_${datetime}.csv"

                file = File(downloadsDir, tempFileName)

                Log.d("fileRenameReceive", "saved file path: :${file.path}")

                val receiveTask = channelClient.receiveFile(channel, file.toUri(), false)
                receiveTask.addOnCompleteListener { task ->

                    if (task.isSuccessful) {
                        Log.d("channel", "File successfully stored")

                    } else {
                        Log.e("channel", "File receival/saving failed: ${task.exception}")
                    }
                }
            }
        })


        loggingButton = findViewById(R.id.logging_button)
        settingsBtn = findViewById(R.id.settings_button)
        dataButton = findViewById(R.id.download_data_button)
        loggingText = findViewById(R.id.logging_text_view)

        // map of sensor to sensor UI components
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
            SensorType.TYPE_STEP_COUNTER to findViewById(R.id.tv_steps_counter_check)
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

        // Set settings button
        settingsBtn.setOnClickListener {
            val openSettings = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(openSettings)
        }

        ActivityHandler.getButtonState().observe(this) { isPressed ->
            loggingButton.isSelected = isPressed

            // Check if app has just started and skip toggled off code
            if (isInitialLoad) {
                isInitialLoad = false
                return@observe
            }

            if (isPressed) {
                startLogging(this)
            } else {
                stopLogging()
            }
        }

        // Register broadcaster
        registerReceiver(sensorCheckReceiver, IntentFilter("SENSOR_CHECK_UPDATE"), RECEIVER_EXPORTED)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onDestroy() {
        // Remove the updateRunnable when the activity is destroyed to prevent memory leaks
        unregisterReceiver(sensorCheckReceiver)
        durationHandler.removeCallbacks(updateRunnableDuration)
        unregisterReceiver(checksumReceiver)
        unregisterReceiver(fileNameReceiver)
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------------

    fun startLogging(context: Context) {

        startTime = SystemClock.elapsedRealtime()
        findViewById<Button>(R.id.logging_button).text = "Stop logging"
        dataButton.visibility = View.GONE
        settingsBtn.visibility = View.GONE
        loggingButton.animate().translationYBy(250f).setDuration(500).start()

        loggingText.text = "Surveying..."

        // Set duration timer
        startTime = SystemClock.elapsedRealtime()
        updateDurationText()
        durationHandler.postDelayed(updateRunnableDuration, 1000)

        // load the sensor settings sent to logging service from file
        val phoneSettings = PhoneSensorSettingsHandler.loadSensorValues()

        // Added health sensor needed for LoggingService
        //  ---- DO NOT REMOVE ----
        phoneSettings[SensorType.TYPE_SPECIFIC_ECG] = Pair(false, 0)
        phoneSettings[SensorType.TYPE_SPECIFIC_PPG] = Pair(false, 0)
        phoneSettings[SensorType.TYPE_SPECIFIC_GSR] = Pair(false, 0)

        Log.d("LoggingStarted", "Settings sent for logging: $phoneSettings")
        loggingIntent.putExtra("settings", phoneSettings as Serializable)

        // Start service
        ContextCompat.startForegroundService(this, loggingIntent)
    }

    // ---------------------------------------------------------------------------------------------

    fun stopLogging() {

        // Stop logging
        settingsBtn.visibility = View.VISIBLE
        findViewById<Button>(R.id.logging_button).text = "Start logging"
        loggingText.text = ""
        timeText.text = ""
        durationHandler.removeCallbacks(updateRunnableDuration)
        loggingButton.animate().translationYBy(-250f).setDuration(200).start()

        Handler().postDelayed({ dataButton.visibility = View.VISIBLE }, 100)

        disableSensorsInUi()

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
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {

            //location permission
            225 -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission is granted. Continue the action or workflow
                    // in your app.
                } else {
                    // Explain to the user that the feature is unavailable
                    AlertDialog.Builder(this).setTitle("Location permission denied").setMessage("Permission is denied.")
                        .setPositiveButton("OK", null).setNegativeButton("Cancel", null).show()
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun disableSensorsInUi() {

        sensorTextViewList.forEach { entry ->
            val colorID = ContextCompat.getColor(
                applicationContext, android.R.color.holo_red_light
            )
            entry.value.text = crossMark
            entry.value.setTextColor(colorID)
        }
    }
    // ---------------------------------------------------------------------------------------------

    private val updateRunnableDuration = object: Runnable {
        override fun run() {
            // Update the duration text every second
            updateDurationText()

            // Schedule the next update
            durationHandler.postDelayed(this, 1000)
        }
    }

    // ---------------------------------------------------------------------------------------------


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


    // ---------------------------------------------------------------------------------------------

    private fun verifyChecksum(context: Context, checkFile: File, expectedChecksum: String) {
        // verifies the checksum for files received from the watch based on the checksum
        // calculated on the watch to check that whole file was sent
        synchronized(fileAccessLock) {
            val rootView = (context as Activity).findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(rootView, "Receiving file... Size: 0 KB", Snackbar.LENGTH_INDEFINITE)
            snackbar.show()

            waitForFileTransfer(checkFile, snackbar, this) { isTransferComplete ->

                if (isTransferComplete) {
                    try {
                        val fileChecksum = generateChecksum(checkFile.inputStream())

                        Log.d("ChecksumListener", "Received log checksum: $fileChecksum")

                        if (fileChecksum == expectedChecksum) {
                            Log.d("verifyChecksum", "Checksum verification successful. File is intact.")
                            Toast.makeText(context, "File integrity verified.", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e("verifyChecksum", "Checksum mismatch. File may be corrupted.")
                            GlobalNotification().showAlertDialog(
                                context, "File Corruption Detected", "The file appears to be corrupted during transfer."
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("verifyChecksum", "Error verifying checksum: ${e.message}")
                        GlobalNotification().showAlertDialog(
                            context, "Error", "An error occurred while verifying the file."
                        )
                    }


                    // NOTE: It's not necessarily optimal for the rename to be called from checksum verification function
                    // but the rename must be done after the verification and transfer is fully complete to get around any
                    // invalid file path errors

                    // it would be optimal to have a way to synchronously wait for the file transfer to be complete
                    // and only after that do the file checksum checking and only after that do the file renaming
                    // all in a specific function
                    renameFile(checkFile)

                } else {
                    Log.w(
                        "verifyChecksum", "File transfer is still in progress; " + "checksum verification skipped."
                    )
                }

                // file size to null so that we cant have wrong file sizes
                receivedFileSize = null
            }

        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun renameFile(renameFile: File) {
        // renames the file to be the same as the file name on the watch
        synchronized(fileAccessLock) {
            if (receivedFileName != null) {
                val downloadsDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val originalFile = File(downloadsDir, receivedFileName!!)
                val success = renameFile.renameTo(originalFile)
                if (success) {
                    Log.d("fileRenameReceive", "rename success! File renamed to $receivedFileName")
                    file = originalFile
                    Log.d("fileRenameReceive", "at ${originalFile.path}")
                    //call received dialog
                    (applicationContext as? GlobalNotification)?.showFileReceivedDialog(originalFile.path)
                } else {
                    Log.d("fileRenameReceive", "rename failure!")
                }

                // setting back to null so that no 2 files are named the same on accident
                receivedFileName = null
            } else {
                Log.w("fileRenameReceive", "No file name received! receivedFileName is null")
            }
        }
    }

    // ---------------------------------------------------------------------------------------------


    private fun waitForFileTransfer(file: File, snackbar: Snackbar, context: Context, callback: (Boolean) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var lastSize = file.length()
        var unchangedSizeCount = 0

        // progress bar for the file receive
        val customView = LayoutInflater.from(context).inflate(R.layout.custom_snackbar_progress, null)

        val progressBar = customView.findViewById<ProgressBar>(R.id.snackbar_progress_bar)
        val progressText = customView.findViewById<TextView>(R.id.snackbar_text)

        val snackbarView = snackbar.view

        val snackbarLayout = snackbarView as? ViewGroup

        if (snackbarLayout == null) {
            Log.e("SnackbarError", "Snackbar view is not a ViewGroup. Cannot proceed with customization.")
            return
        }

        snackbarLayout.removeAllViews()
        snackbarLayout.addView(customView)
        snackbar.show()

        val checkRunnable = object: Runnable {

            override fun run() {
                // if receivedFileSize is null, we default to received file len
                val totalSize: Long = receivedFileSize ?: file.length()

                val currentSize = file.length()
                if (currentSize == lastSize) {
                    unchangedSizeCount++
                    // Confirm the file size hasn't changed for 3 consecutive checks before completing
                    if (unchangedSizeCount >= 3) {
                        snackbar.dismiss()
                        callback(true)
                    } else {
                        handler.postDelayed(this, 500)
                    }
                } else {

                    // progress bar for the file received vs the total file size
                    lastSize = currentSize
                    unchangedSizeCount = 0

                    progressBar.progress = ((currentSize.toDouble() / totalSize) * 100).toInt()
                    val currentSizeMB = currentSize / (1024.0 * 1024.0)
                    val totalSizeMB = totalSize / (1024.0 * 1024.0)

                    progressText.text = String.format("Receiving file... %.1f MB / %.1f MB", currentSizeMB, totalSizeMB)

                    handler.postDelayed(this, 500)
                }
            }
        }

        handler.post(checkRunnable)
    }

    // ---------------------------------------------------------------------------------------------

    fun generateChecksum(inputStream: InputStream): String {
        // gets checksum for the file received from the watch
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192) // Adjust buffer size as needed
        var bytesRead: Int

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } catch (e: IOException) {
            // Handle exception
        } finally {
            inputStream.close()
        }

        val hashBytes = digest.digest()
        return hashBytes.fold("") { str, it -> str + "%02x".format(it) }
    }
}
// =================================================================================================

// Class for showing a notification whenever file transfer from smartwatch is detected. A separate
// class as Application() is needed in order make notification to appear on all activities.
class GlobalNotification: Application() {
    private var currentActivity: AppCompatActivity? = null

    override fun onCreate() {
        super.onCreate()

        // Register activity lifecycle callbacks to keep track of the current activity
        registerActivityLifecycleCallbacks(object: ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is AppCompatActivity) {
                    currentActivity = activity
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity == currentActivity) {
                    currentActivity = null
                }
            }

            // Other lifecycle callback methods are left empty
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    // ---------------------------------------------------------------------------------------------

    fun showFileReceivedDialog(filePath: String) {
        currentActivity?.let {


            val inflater = LayoutInflater.from(it)
            val dialogView = inflater.inflate(R.layout.custom_dialog, null)

            val builder = AlertDialog.Builder(it)
            builder.setView(dialogView as View)

            val dialog = builder.create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val title = dialogView.findViewById<TextView>(R.id.dialog_title)
            val message = dialogView.findViewById<TextView>(R.id.dialog_message)
            val okBtn = dialogView.findViewById<Button>(R.id.positiveButton)
            val cancelBtn = dialogView.findViewById<Button>(R.id.negativeButton)
            okBtn.text = "View";
            okBtn.setOnClickListener {
                dialog.dismiss()
                try {
                    val builder = StrictMode.VmPolicy.Builder()
                    StrictMode.setVmPolicy(builder.build())
                    val intent = Intent(Intent.ACTION_VIEW)
                    val uri = Uri.parse("content://" + filePath)
                    intent.setDataAndType(uri, "*/*")
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    val inflater = LayoutInflater.from(this)
                    val dialogView = inflater.inflate(R.layout.custom_dialog, null)

                    val builder = AlertDialog.Builder(this)
                    builder.setView(dialogView as View)

                    val dialog = builder.create()
                    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    val title = dialogView.findViewById<TextView>(R.id.dialog_title)
                    val message = dialogView.findViewById<TextView>(R.id.dialog_message)
                    val okBtn = dialogView.findViewById<Button>(R.id.positiveButton)
                    val cancelBtn = dialogView.findViewById<Button>(R.id.negativeButton)
                    okBtn.setVisibility(View.GONE)
                    cancelBtn.setOnClickListener {
                        dialog.dismiss()
                    }
                    Log.d("test", e.message.toString())
                    title.text = "File Corruption"
                    message.text = "Error in opening of this file"
                    dialog.show()
                }

            }
            cancelBtn.setOnClickListener {
                dialog.dismiss()
            }
            title.text = "File Received"
            message.text = "The file has been saved at: $filePath"
            dialog.show()

        }
    }

    // ---------------------------------------------------------------------------------------------

    fun showAlertDialog(context: Context, title: String, message: String) {
        AlertDialog.Builder(context).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            create()
            show()
        }
    }
}

class MessageListenerService: WearableListenerService() {
    // listens for messages sent from the watch to the phone
    private val CHECKSUM_PATH = "/checksum"
    private val FILE_NAME_PATH = "/file-name"
    private val FILE_SIZE_PATH = "/file-size"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val messageTag = messageEvent.path
        // Check if the message path tag math

        when (messageTag) {
            CHECKSUM_PATH -> {
                // Convert the byte array back to a String
                val checksum = String(messageEvent.data)
                Log.d("verifyChecksum", "Received checksum: $checksum")
                // Broadcast the checksum to MainActivity
                val intent = Intent("ACTION_VERIFY_CHECKSUM")
                intent.putExtra("checksum", checksum)
                sendBroadcast(intent)
            }

            FILE_NAME_PATH -> {
                // Convert the byte array back to a String
                val filename = String(messageEvent.data)
                Log.d("fileRenameReceive", "Received filename: $filename")
                // Broadcast the file name to MainActivity
                val intent = Intent("ACTION_RENAME_FILE")
                intent.putExtra("filename", filename)
                sendBroadcast(intent)
            }

            FILE_SIZE_PATH -> {
                // Convert the byte array back to a String
                val fileSize = String(messageEvent.data)
                Log.d("fileSizeReceive", "Received file size: $fileSize")
                // Broadcast the file size to MainActivity
                val intent = Intent("ACTION_SET_FILE_SIZE")
                intent.putExtra("fileSize", fileSize)
                sendBroadcast(intent)
            }

            else -> {
                super.onMessageReceived(messageEvent)
            }
        }
    }
}