package com.mobilewizards.logging_app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.mimir.sensors.SensorType
import com.mobilewizards.watchlogger.WatchActivityHandler
import com.mobilewizards.logging_app.databinding.ActivitySettingsBinding
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.lang.reflect.Type


// ---------------------------------------------------------------------------------------------

const val IDX_SWITCH = 0
const val IDX_SEEKBAR = 1
const val IDX_TEXTVIEW = 2
const val infinitySymbol = "\u221E"
const val sharedPrefName = "DefaultSettings"

val progressToFrequency = arrayOf(1, 5, 10, 50, 100, 200, 0)
val frequencyToProgress: Map<Int, Int> =
    progressToFrequency.mapIndexed { index, frequency -> frequency to index }.toMap()

// ---------------------------------------------------------------------------------------------

class SettingsActivity: Activity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var sensorsComponents: MutableMap<SensorType, MutableList<Any?>>

    private lateinit var seekBarChangeListener: SeekBar.OnSeekBarChangeListener
    private lateinit var switchCheckedChangeListener: CompoundButton.OnCheckedChangeListener

    // ---------------------------------------------------------------------------------------------

    private val updateReceiver = object: BroadcastReceiver() {
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
        btnSave.setOnClickListener {
            saveSettings()
            setResult(RESULT_OK)
            finish() // Close activity
        }

        // Save current settings as default
        val btnDefault = findViewById<Button>(R.id.button_default)
        btnDefault.setOnClickListener {
            saveDefaultSettings()
        }

        initializeSharedPreference()

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

    private fun initializeSharedPreference() {
        // Initialisation values if they are not present
        sharedPreferences = getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("GNSS")) {
            val editor: SharedPreferences.Editor = sharedPreferences.edit()
            editor.putString("GNSS", Gson().toJson(mutableListOf(true, 0)))
            editor.putString("IMU", Gson().toJson(mutableListOf(false, 2)))
            editor.putString("PSR", Gson().toJson(mutableListOf(false, 0)))
            editor.putString("STEPS", Gson().toJson(mutableListOf(false, 1)))
            editor.putString("ECG", Gson().toJson(mutableListOf(false, 4)))
            editor.putString("PPG", Gson().toJson(mutableListOf(false, 4)))
            editor.putString("GSR", Gson().toJson(mutableListOf(false, 4)))
            editor.apply()
        }
    }


    // ---------------------------------------------------------------------------------------------

    private fun initializeSensorComponents() {

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

        Log.d("SettingsActivity", "UI updated from shared preferences.")


        // Load Initialisation values from sharedPreferences to the sensor types
        val sensorsInit: Map<SensorType, MutableList<String>> = mapOf(
            SensorType.TYPE_GNSS to loadSharedPreference("GNSS"),
            SensorType.TYPE_IMU to loadSharedPreference("IMU"),
            SensorType.TYPE_PRESSURE to loadSharedPreference("PSR"),
            SensorType.TYPE_STEPS to loadSharedPreference("STEPS"),
            SensorType.TYPE_SPECIFIC_ECG to loadSharedPreference("ECG"),
            SensorType.TYPE_SPECIFIC_PPG to loadSharedPreference("PPG"),
            SensorType.TYPE_SPECIFIC_GSR to loadSharedPreference("GSR")
        )

        // Set the initialisation values
        sensorsInit.forEach { entry ->
            val currentSensor = sensorsComponents[entry.key]!! // should never be null

            // The IDE says that the casts below cannot succeed, but they do so ignore that
            val sensorEnabled = entry.value[0] as Boolean
            val sensorFrequencyIndex = (entry.value[1] as Double).toInt()

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

                val textView: TextView =
                    sensorsComponents.entries.find { it.value[IDX_SEEKBAR] == seekBar }?.value?.get(
                        2
                    ) as TextView

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
            if (buttonView is Switch) {
                val seekBar: SeekBar? =
                    sensorsComponents.entries.find { it.value[0] == buttonView }?.value?.get(1) as? SeekBar
                seekBar?.isEnabled = isChecked
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun setupListeners() {
        // Setting listeners
        sensorsComponents.forEach { entry ->
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


    private fun loadSharedPreference(key: String): MutableList<String> {
        val jsonString = sharedPreferences.getString(key, "")
        val type: Type = object: TypeToken<MutableList<Any>>() {}.type

        return Gson().fromJson(jsonString, type) ?: mutableListOf()
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateTextView(textView: TextView, progressIndex: Int) {
        val progressHz = progressToFrequency[progressIndex]
        textView.text = if (progressHz == 0) infinitySymbol else "$progressHz Hz"
    }

    // ---------------------------------------------------------------------------------------------

    private fun saveSettings() {
        sensorsComponents.forEach { entry ->
            if (entry.key == SensorType.TYPE_GNSS) {
                WatchActivityHandler.sensorsSelected[entry.key] = Pair(
                    (entry.value[IDX_SWITCH] as? Switch)?.isChecked as Boolean, 1
                )
            } else {
                WatchActivityHandler.sensorsSelected[entry.key] = Pair(
                    (entry.value[IDX_SWITCH] as? Switch)?.isChecked as Boolean,
                    progressToFrequency[(entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int]
                )
            }

            Log.d(
                "SettingsActivity",
                "Settings for ${entry.key} changed to " + "${WatchActivityHandler.sensorsSelected[entry.key].toString()}."
            )
        }
        Log.d("SettingsActivity", "Settings saved.")
    }

    // ---------------------------------------------------------------------------------------------

    private fun saveDefaultSettings() {
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        sensorsComponents.forEach { entry ->
            var spkey = ""
            spkey = when (entry.key) {
                SensorType.TYPE_GNSS -> "GNSS"
                SensorType.TYPE_IMU -> "IMU"
                SensorType.TYPE_PRESSURE -> "PSR"
                SensorType.TYPE_STEPS -> "STEPS"
                SensorType.TYPE_SPECIFIC_ECG -> "ECG"
                SensorType.TYPE_SPECIFIC_PPG -> "PPG"
                SensorType.TYPE_SPECIFIC_GSR -> "GSR"
                else -> {
                    return@forEach
                }
            }
            if (spkey == "GNSS") {
                editor.putString(
                    spkey, Gson().toJson(
                        mutableListOf(
                            (entry.value[IDX_SWITCH] as? Switch)?.isChecked as Boolean, 0
                        )
                    )
                )
            } else {
                editor.putString(
                    spkey, Gson().toJson(
                        mutableListOf(
                            (entry.value[IDX_SWITCH] as? Switch)?.isChecked as Boolean,
                            (entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int
                        )
                    )
                )
            }
            Log.d(
                "SettingsActivity",
                "Default settings for ${entry.key} changed to " + "${WatchActivityHandler.sensorsSelected[entry.key].toString()}."
            )
        }
        editor.apply()
        Log.d("SettingsActivity", "Default settings saved.")
        Toast.makeText(applicationContext, "Default settings saved.", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------------------------------

    companion object {
        fun processSettingsJson(context: Context, jsonData: JSONObject) {
            val sharedPreferences = context.getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
            val editor: SharedPreferences.Editor = sharedPreferences.edit()

            jsonData.keys().forEach { key ->
                val sensorValue: MutableList<Any?> = mutableListOf()
                val isSwitchOn = jsonData.getJSONObject(key).getBoolean("switch")

                var frequencyIndex = 0
                if (jsonData.getJSONObject(key).has("value")) {
                    val frequencyValue = jsonData.getJSONObject(key).getInt("value")
                    frequencyIndex = frequencyToProgress[frequencyValue] ?: 1
                }

                sensorValue.add(isSwitchOn)
                sensorValue.add(frequencyIndex)

                editor.putString(key, Gson().toJson(sensorValue))
            }

            editor.apply()

            Log.d("SettingsActivity", "Settings successfully processed")

            val broadcastIntent = Intent("ACTION_SETTINGS_UPDATED")

            // broadcast update to make sure UI is up to date
            LocalBroadcastManager.getInstance(context).sendBroadcast(broadcastIntent)
        }
    }

    // ---------------------------------------------------------------------------------------------
}


