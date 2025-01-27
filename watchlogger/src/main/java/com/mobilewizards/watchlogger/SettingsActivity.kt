package com.mobilewizards.logging_app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.mimir.sensors.SensorType
import com.mobilewizards.logging_app.databinding.ActivitySettingsBinding
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.WorkManager
import com.mobilewizards.watchlogger.SchedulerManager
import org.json.JSONObject
import com.mobilewizards.watchlogger.SensorSettingsHandler

// ---------------------------------------------------------------------------------------------

// ui element indexes and global symbols
const val IDX_SWITCH = 0
const val IDX_SEEKBAR = 1
const val IDX_TEXTVIEW = 2
const val infinitySymbol = "\u221E"

// ---------------------------------------------------------------------------------------------

class SettingsActivity: Activity() {
    private lateinit var binding: ActivitySettingsBinding

    private lateinit var sensorsComponents: MutableMap<SensorType, MutableList<Any?>>

    private lateinit var seekBarChangeListener: SeekBar.OnSeekBarChangeListener
    private lateinit var switchCheckedChangeListener: CompoundButton.OnCheckedChangeListener

    // ---------------------------------------------------------------------------------------------


    private val updateReceiver = object: BroadcastReceiver() {
        // receiver for settings sent from phone
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "ACTION_SETTINGS_UPDATED") {
                // refresh UI whenever settings are received from the phone
                Log.d("SettingsActivity", "Broadcast received. Refreshing UI.")
                loadSharedPreferencesToUi()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Listener for updates uploaded from phone to watch
        LocalBroadcastManager.getInstance(this).registerReceiver(
            updateReceiver, IntentFilter("ACTION_SETTINGS_UPDATED")
        )

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Saving settings
        val btnSave = findViewById<Button>(R.id.button_save)
        // find switch_periodic_recording


        // Currently commented out in the layout XML
        // TODO: this is commented out only because the feature is not fully implemented,
        // Also a block is commented out in the activity_setting.xml for turning the feature on

        // Work in progress background recording
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

        // TODO: remove the code from comment block when working on background recording again


        //val togglePeriodicRecording = findViewById<Switch>(R.id.switch_periodic_recording)

        // recover toggle
        //          val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        //          togglePeriodicRecording.isChecked = sharedPreferences.getBoolean("isPeriodicRecordingEnabled", false)

        btnSave.setOnClickListener {
            saveSettings()
            //          turn on/off based on the status of toggle
            //          if (togglePeriodicRecording.isChecked) {
            //              SchedulerManager.schedulePeriodicLogging(this)
            //              Toast.makeText(this, "Background recording enabled.", Toast.LENGTH_SHORT).show()
            //          } else {
            //              WorkManager.getInstance(this).cancelUniqueWork("SensorLoggingWork")
            //              Toast.makeText(this, "Background recording disabled.", Toast.LENGTH_SHORT).show()
            //          }
            //          // save toggle status to SharedPreferences
            //          val editor = sharedPreferences.edit()
            //          editor.putBoolean("isPeriodicRecordingEnabled", togglePeriodicRecording.isChecked)
            //          editor.apply()

            setResult(RESULT_OK)
            finish() // Close activity
        }

        // initialize the settings handler
        SensorSettingsHandler.initializePreferences(this)

        // initialize the UI component map for sensors
        initializeSensorComponents()

        loadSharedPreferencesToUi()

        createSeekBarListener()
        createSwitchListener()

        setupListeners()

    }

    // ---------------------------------------------------------------------------------------------
    override fun onDestroy() {
        super.onDestroy()

        // destroy
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateReceiver)
    }


    // ---------------------------------------------------------------------------------------------

    private fun initializeSensorComponents() {

        // initialize the UI component map for sensors
        // every sensor has enabled switch, seekbar to set HZ and text field
        sensorsComponents = mutableMapOf()
        // Setting the IDs for each components
        val sensorsIDs = mapOf(
            SensorType.TYPE_GNSS to mutableListOf(R.id.switch_gnss, 0, R.id.settings_tv_gnss),
            SensorType.TYPE_IMU to mutableListOf(R.id.switch_imu, R.id.settings_sb_imu, R.id.settings_tv_imu),
            SensorType.TYPE_PRESSURE to mutableListOf(R.id.switch_baro, R.id.settings_sb_baro, R.id.settings_tv_baro),
            SensorType.TYPE_STEPS to mutableListOf(R.id.switch_steps, R.id.settings_sb_step, R.id.settings_tv_step),
            SensorType.TYPE_SPECIFIC_ECG to mutableListOf(R.id.switch_ecg, R.id.settings_sb_ecg, R.id.settings_tv_ecg),
            SensorType.TYPE_SPECIFIC_PPG to mutableListOf(R.id.switch_ppg, R.id.settings_sb_ppg, R.id.settings_tv_ppg),
            SensorType.TYPE_SPECIFIC_GSR to mutableListOf(R.id.switch_gsr, R.id.settings_sb_gsr, R.id.settings_tv_gsr),
        )

        sensorsIDs.forEach { entry ->
            sensorsComponents[entry.key] = mutableListOf(
                findViewById<Switch>(entry.value[IDX_SWITCH]),
                if (entry.key != SensorType.TYPE_GNSS) findViewById<SeekBar>(entry.value[IDX_SEEKBAR]) else null,
                findViewById<TextView>(entry.value[IDX_TEXTVIEW])
            )
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadSharedPreferencesToUi() {

        // Loads shared preferences for sensor settings from the file and updates the UI accordingly

        Log.d("SettingsActivity", "UI updated from shared preferences.")


        // Load Initialisation values from sharedPreferences to the sensor types
        val sensorsInit = SensorSettingsHandler.loadSensorValues()

        // Set the initialisation values
        sensorsInit.forEach { entry ->
            val currentSensor = sensorsComponents[entry.key]!! // should never be null

            // The IDE says that the casts below cannot succeed, but they do so ignore that
            val sensorEnabled = entry.value.first
            val sensorFrequencyIndex = entry.value.second

            (currentSensor[IDX_SWITCH] as Switch).isChecked = sensorEnabled
            if (entry.key != SensorType.TYPE_GNSS) {
                (currentSensor[IDX_SEEKBAR] as SeekBar).isEnabled = sensorEnabled

                (currentSensor[IDX_SEEKBAR] as SeekBar).progress = sensorFrequencyIndex
            }

            val textView = currentSensor[IDX_TEXTVIEW] as TextView
            updateTextView(textView, sensorFrequencyIndex)
        }

    }

    // ---------------------------------------------------------------------------------------------

    private fun createSeekBarListener() {
        // Define a common seekbar listener
        seekBarChangeListener = object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {

                val textView =
                    sensorsComponents.entries.find { it.value[IDX_SEEKBAR] == seekBar }?.value?.get(2) as TextView
                updateTextView(textView, progress)

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Your common implementation for onStartTrackingTouch
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Your common implementation for onStopTrackingTouch
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun createSwitchListener() {
        // Define a common switch listener
        switchCheckedChangeListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            val seekBar =
                sensorsComponents.entries.find { it.value[IDX_SWITCH] == buttonView }?.value?.get(1) as? SeekBar
            seekBar?.isEnabled = isChecked
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setupListeners() {
        // Setting listeners
        sensorsComponents.forEach { entry ->
            // makes sure that GSR and ECG sensors arent enabled at the same time
            when (entry.key) {
                SensorType.TYPE_SPECIFIC_ECG -> {
                    (entry.value[IDX_SWITCH] as? Switch)?.setOnCheckedChangeListener { buttonView, isChecked ->
                        (entry.value[IDX_SEEKBAR] as SeekBar).isEnabled = isChecked
                        // Disable GSR if ECG is activated
                        val sensorGSR = sensorsComponents[SensorType.TYPE_SPECIFIC_GSR]
                        if (isChecked && sensorGSR != null) {
                            (sensorGSR[IDX_SWITCH] as Switch).isChecked = false
                            (sensorGSR[IDX_SEEKBAR] as SeekBar).isEnabled = false
                        }
                    }
                }

                SensorType.TYPE_SPECIFIC_GSR -> {
                    (entry.value[IDX_SWITCH] as? Switch)?.setOnCheckedChangeListener { buttonView, isChecked ->
                        (entry.value[IDX_SEEKBAR] as SeekBar).isEnabled = isChecked
                        // Disable GSR if ECG is activated
                        val sensorECG = sensorsComponents[SensorType.TYPE_SPECIFIC_ECG]
                        if (isChecked && sensorECG != null) {
                            (sensorECG[IDX_SWITCH] as Switch).isChecked = false
                            (sensorECG[IDX_SEEKBAR] as SeekBar).isEnabled = false
                        }
                    }
                }

                else -> {
                    (entry.value[IDX_SWITCH] as? Switch)?.setOnCheckedChangeListener(
                        switchCheckedChangeListener
                    )
                }
            }
            (entry.value[IDX_SEEKBAR] as? SeekBar)?.setOnSeekBarChangeListener(seekBarChangeListener)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateTextView(textView: TextView, progressIndex: Int) {
        // Updates the Hz value based on the progress bar index
        val progressHz = SensorSettingsHandler.progressToFrequency[progressIndex]
        textView.text = if (progressHz == 0) infinitySymbol else "$progressHz Hz"
    }

    // ---------------------------------------------------------------------------------------------

    private fun saveSettings() {
        // loads the settings from the UI elements and saves them to file through the SensorSettingsHandler
        sensorsComponents.forEach { entry ->

            val isChecked = (entry.value[IDX_SWITCH] as? Switch)?.isChecked as Boolean

            val progress = if (entry.key == SensorType.TYPE_GNSS) {
                0
            } else {
                (entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int
            }

            SensorSettingsHandler.saveSetting(entry.key, Pair(isChecked, progress))

            Log.d(
                "SettingsActivity", "Settings for ${entry.key} changed to ($isChecked, $progress)."
            )
        }
        Log.d("SettingsActivity", "Default settings saved.")
        Toast.makeText(applicationContext, "settings saved.", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------------------------------

    companion object {
        // receives the settings JSON sent from the phone, parses it and saves it to file
        fun processSettingsJson(context: Context, jsonData: JSONObject) {

            // Save Json data to shared preferences file
            jsonData.keys().forEach { keyString ->
                val isSwitchOn = jsonData.getJSONObject(keyString).getBoolean("switch")

                var frequencyIndex = 0
                if (jsonData.getJSONObject(keyString).has("value")) {
                    val frequencyValue = jsonData.getJSONObject(keyString).getInt("value")
                    frequencyIndex = SensorSettingsHandler.frequencyToProgress[frequencyValue] ?: 0
                }

                val sensorValue: Pair<Boolean, Int> = Pair(isSwitchOn, frequencyIndex)
                val keySensor = SensorSettingsHandler.StringToSensor[keyString]!!
                SensorSettingsHandler.saveSetting(keySensor, sensorValue)
            }

            Log.d("SettingsActivity", "Settings successfully processed and saved")

            // broadcasts intent to update the UI to be updated with the settings file
            val broadcastIntent = Intent("ACTION_SETTINGS_UPDATED")

            // broadcast update to make sure UI is up to date
            LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
        }
    }

    // ---------------------------------------------------------------------------------------------
}


