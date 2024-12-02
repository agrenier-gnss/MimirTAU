package com.mobilewizards.logging_app

import android.annotation.SuppressLint

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.gson.JsonParser
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.JsonParser.parseString
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val IDX_SWITCH = 0
const val IDX_SEEKBAR = 1
const val IDX_TEXTVIEW = 2

class SettingsActivity: Activity() {
    private lateinit var binding: ActivitySettingsBinding

    private val sharedPrefName = "DefaultSettings"
    private lateinit var sharedPreferences: SharedPreferences
    private val progressToFrequency = arrayOf(1, 5, 10, 50, 100, 200, 0)
    private lateinit var sensorsComponents: MutableMap<SensorType, MutableList<Any?>>
    private val frequencyToProgress: Map<Int, Int> = progressToFrequency
        .mapIndexed { index, frequency -> frequency to index }
        .toMap()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialisation values
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

        // Gathering components
        sensorsComponents = mutableMapOf()

        sensorsIDs.forEach { entry ->
            sensorsComponents[entry.key] = mutableListOf(
                findViewById<Switch>(entry.value[IDX_SWITCH]),
                if (entry.key != SensorType.TYPE_GNSS) findViewById<SeekBar>(entry.value[IDX_SEEKBAR]) else null,
                findViewById<TextView>(entry.value[IDX_TEXTVIEW])
            )
        }

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
            (currentSensor[IDX_TEXTVIEW] as TextView).text = "${progressToFrequency[sensorFrequencyIndex]} Hz"
        }

        // Define a common seekbar listener
        val seekBarChangeListener = object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val infinitySymbol = "\u221E"
                val textView: TextView? =
                    sensorsComponents.entries.find { it.value[IDX_SEEKBAR] == seekBar }?.value?.get(
                        2
                    ) as? TextView
                if (progress < 6) {
                    textView?.text = "${progressToFrequency[progress]} Hz"
                } else {
                    textView?.text = infinitySymbol
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Your common implementation for onStartTrackingTouch
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Your common implementation for onStopTrackingTouch
            }
        }

        // Define a common switch listener
        val switchCheckedChangeListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView is Switch) {
                val seekBar: SeekBar? =
                    sensorsComponents.entries.find { it.value[0] == buttonView }?.value?.get(1) as? SeekBar
                seekBar?.isEnabled = isChecked
            }
        }


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

        val channelClient = Wearable.getChannelClient(applicationContext)
        channelClient.registerChannelCallback(object : ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                val shortPath = "/storage/emulated/0/Download/setting_app_received_${
                    LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS")
                    )
                }"

                val filePath = "file://${shortPath}"

                val receiveTask = channelClient.receiveFile(channel, filePath.toUri(), false)
                receiveTask.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("channel", shortPath)
                        Toast.makeText(applicationContext, "succeed", Toast.LENGTH_SHORT).show()
                        // Add a slight delay before reading
                        Handler(Looper.getMainLooper()).postDelayed({
                            extractJsonDataFromCsv(shortPath)
                        }, 500)

                    }
                }
            }
        })
    }


    fun reflectJsonDataToWatch(jsonData: JSONObject) {
        val keys = jsonData.keys();
        while (keys.hasNext()) {
            val key: String = keys.next()
            var senorKey:SensorType = SensorType.TYPE_GNSS
            when (key) {
                "GNSS" -> sensorsComponents[SensorType.TYPE_GNSS]?.let {
                    (it[0] as Switch).isChecked = jsonData.getJSONObject(key).getBoolean("switch")
                }
                "IMU" -> senorKey = SensorType.TYPE_IMU
                "PSR" -> senorKey = SensorType.TYPE_PRESSURE
                "STEPS" -> senorKey = SensorType.TYPE_STEPS
                "ECG" -> senorKey = SensorType.TYPE_SPECIFIC_ECG
                "PPG" -> senorKey = SensorType.TYPE_SPECIFIC_PPG
                "GSR" -> senorKey = SensorType.TYPE_SPECIFIC_GSR
            }
            if(senorKey != SensorType.TYPE_GNSS){
                sensorsComponents[senorKey]?.let {
                    val processValue = jsonData.getJSONObject(key).getInt("value")
                    Log.d("shared1",processValue.toString())
                    Log.d("shared1",frequencyToProgress[processValue].toString())
                    (it[0] as Switch).isChecked = jsonData.getJSONObject(key).getBoolean("switch")
                    (it[1] as SeekBar).isEnabled = jsonData.getJSONObject(key).getBoolean("switch")
                    (it[1] as SeekBar).progress = frequencyToProgress[processValue]?:0
                    (it[2] as TextView).text = "${processValue.toString()} Hz"
                }
            }


        }

    }

    fun extractJsonDataFromCsv(filePath: String): JSONObject? {
        Thread.sleep(100)
        val jsonTag = "JSON_DATA_START"
        val file = File(filePath)
        var jsonData: JSONObject? = null

        if (!file.exists()) {
            Log.e("channel", "File does not exist")
            return null
        }


        val fileContents = try {
            BufferedReader(FileReader(file)).use { it.readText() }
        } catch (e: IOException) {
            Log.e("channel", "Error reading file: ${e.message}")
            e.printStackTrace()
            return null

        }

        Log.d("channel", "File contents: $fileContents")

        if (fileContents.isBlank()) {
            Log.e("channel", "File is empty or could not be read")

        } else {
            val jsonStartIndex = fileContents.indexOf(jsonTag)
            if (jsonStartIndex == -1) {
                Log.e("channel", "JSON_DATA_START tag not found in file")

            } else {
                val jsonDataString =
                    fileContents.substring(jsonStartIndex + "JSON_DATA_START".length)
                Log.e("channel", jsonDataString)
                try {
                    jsonData = JSONObject(jsonDataString)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (file.delete()) {
            Log.d("channel", "File deleted successfully")
        } else {
            Log.e("channel", "Failed to delete file")
        }
        jsonData?.let { reflectJsonDataToWatch(it) }
        return jsonData
    }








    // ---------------------------------------------------------------------------------------------
    private fun loadSharedPreference(key: String): MutableList<String> {
        val jsonString = sharedPreferences.getString(key, "")
        val type: Type = object: TypeToken<MutableList<Any>>() {}.type

        return Gson().fromJson(jsonString, type) ?: mutableListOf()
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
                            (entry.value[IDX_SWITCH] as? Switch)?.isChecked as Boolean, 0)
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
}