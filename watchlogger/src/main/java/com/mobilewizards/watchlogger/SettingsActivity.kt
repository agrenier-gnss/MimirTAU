package com.mobilewizards.logging_app

import android.annotation.SuppressLint

import android.app.Activity
import android.content.Context
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
import androidx.core.net.toUri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val IDX_SWITCH   = 0
const val IDX_SEEKBAR  = 1
const val IDX_TEXTVIEW = 2

class SettingsActivity : Activity() {
    private lateinit var binding: ActivitySettingsBinding

    private val sharedPrefName = "DefaultSettings"
    private lateinit var sharedPreferences: SharedPreferences
    private val progressToFrequency = arrayOf(1, 5, 10, 50, 100, 200, 0)
    private lateinit var sensorsComponents : MutableMap<SensorType, MutableList<Any?>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialisation values
        sharedPreferences = getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("GNSS")) {
            val editor: SharedPreferences.Editor = sharedPreferences.edit()
            editor.putString("GNSS",  Gson().toJson(mutableListOf(true,  0)))
            editor.putString("IMU",   Gson().toJson(mutableListOf(false, 2)))
            editor.putString("PSR",   Gson().toJson(mutableListOf(false, 0)))
            editor.putString("STEPS", Gson().toJson(mutableListOf(false, 1)))
            editor.putString("ECG",   Gson().toJson(mutableListOf(false, 4)))
            editor.putString("PPG",   Gson().toJson(mutableListOf(false, 4)))
            editor.putString("GSR",   Gson().toJson(mutableListOf(false, 4)))
            editor.apply()
        }

        // Load from shared preferences
        val sensorsInit = mapOf(
            SensorType.TYPE_GNSS         to loadMutableList("GNSS"),
            SensorType.TYPE_IMU          to loadMutableList("IMU"),
            SensorType.TYPE_PRESSURE     to loadMutableList("PSR"),
            SensorType.TYPE_STEPS        to loadMutableList("STEPS"),
            SensorType.TYPE_SPECIFIC_ECG to loadMutableList("ECG"),
            SensorType.TYPE_SPECIFIC_PPG to loadMutableList("PPG"),
            SensorType.TYPE_SPECIFIC_GSR to loadMutableList("GSR")
        )

        // Setting the IDs for each components
        val sensorsIDs = mapOf(
            SensorType.TYPE_GNSS to
                    mutableListOf(R.id.switch_gnss, 0, R.id.settings_tv_gnss),
            SensorType.TYPE_IMU to
                    mutableListOf(R.id.switch_imu, R.id.settings_sb_imu, R.id.settings_tv_imu),
            SensorType.TYPE_PRESSURE to
                    mutableListOf(R.id.switch_baro, R.id.settings_sb_baro, R.id.settings_tv_baro),
            SensorType.TYPE_STEPS to
                    mutableListOf(R.id.switch_steps, R.id.settings_sb_step, R.id.settings_tv_step),
            SensorType.TYPE_SPECIFIC_ECG to
                    mutableListOf(R.id.switch_ecg, R.id.settings_sb_ecg, R.id.settings_tv_ecg),
            SensorType.TYPE_SPECIFIC_PPG to
                    mutableListOf(R.id.switch_ppg, R.id.settings_sb_ppg, R.id.settings_tv_ppg),
            SensorType.TYPE_SPECIFIC_GSR to
                    mutableListOf(R.id.switch_gsr, R.id.settings_sb_gsr, R.id.settings_tv_gsr),
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
            (sensorsComponents[entry.key]?.get(IDX_SWITCH) as Switch).isChecked =
                entry.value[0] as Boolean
            if (entry.key != SensorType.TYPE_GNSS) {
                (sensorsComponents[entry.key]?.get(IDX_SEEKBAR) as SeekBar).isEnabled =
                    entry.value[0] as Boolean
                (sensorsComponents[entry.key]?.get(IDX_SEEKBAR) as SeekBar).progress =
                    (entry.value[1] as Double).toInt()
            }
            (sensorsComponents[entry.key]?.get(IDX_TEXTVIEW) as TextView).text =
                "${progressToFrequency[(entry.value[1] as Double).toInt()]} Hz"
        }

        // Define a common seekbar listener
        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val textView: TextView? =
                    sensorsComponents.entries.find { it.value[IDX_SEEKBAR] == seekBar }?.value?.get(
                        2
                    ) as? TextView
                if (progress != 6) {
                    textView?.text = "${progressToFrequency[progress]} Hz"
                } else {
                    textView?.text = "\u221E"
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
        val switchCheckedChangeListener =
            CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
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
                        if (isChecked) {
                            (sensorsComponents[SensorType.TYPE_SPECIFIC_GSR]?.get(IDX_SWITCH) as Switch).isChecked =
                                false
                            (sensorsComponents[SensorType.TYPE_SPECIFIC_GSR]?.get(IDX_SEEKBAR) as SeekBar).isEnabled =
                                false
                        }
                    }
                }

                SensorType.TYPE_SPECIFIC_GSR -> {
                    (entry.value[IDX_SWITCH] as? Switch)?.setOnCheckedChangeListener { buttonView, isChecked ->
                        (entry.value[IDX_SEEKBAR] as SeekBar).isEnabled = isChecked
                        // Disable GSR if ECG is activated
                        if (isChecked) {
                            (sensorsComponents[SensorType.TYPE_SPECIFIC_ECG]?.get(IDX_SWITCH) as Switch).isChecked =
                                false
                            (sensorsComponents[SensorType.TYPE_SPECIFIC_ECG]?.get(IDX_SEEKBAR) as SeekBar).isEnabled =
                                false
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
                val filePath = "file:///storage/emulated/0/Download/setting_app_received_${
                    LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"))}.csv"
                val receiveTask = channelClient.receiveFile(channel, filePath.toUri(), false)
                receiveTask.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("channel", "File successfully stored")
                        Toast.makeText(applicationContext, "succeed", Toast.LENGTH_SHORT).show()
                        extractJsonDataFromCsv(filePath)

                    } else {
                        Log.e("channel", "File receival/saving failed: ${task.exception}")
                    }
                }
            }
        })
    }

    fun reflectJsonDataToWatch(jsonData:JSONObject) {
        val keys = jsonData.keys();
        while (keys.hasNext()) {
            val key: String = keys.next()
            when (key) {
                "GNSS" -> sensorsComponents[SensorType.TYPE_GNSS]?.let {
                    (it[0] as Switch).isChecked = jsonData.getJSONObject(key).getInt("switch") == 1
                }

                "IMU" -> sensorsComponents[SensorType.TYPE_IMU]?.let {
                    val processValue = jsonData.getJSONObject(key).getInt("value")
                    (it[0] as Switch).isChecked = jsonData.getJSONObject(key).getInt("switch") == 1
                    (it[1] as SeekBar).isEnabled = jsonData.getJSONObject(key).getInt("switch") == 1
                    (it[1] as SeekBar).progress = processValue
                    (it[2] as TextView).text = "IMU frequency: $processValue Hz"
                }

                "PSR" -> sensorsComponents[SensorType.TYPE_PRESSURE]?.let {
                    val processValue = jsonData.getJSONObject(key).getInt("value")
                    (it[0] as Switch).isChecked = jsonData.getJSONObject(key).getInt("switch") == 1
                    (it[1] as SeekBar).isEnabled = jsonData.getJSONObject(key).getInt("switch") == 1
                    (it[1] as SeekBar).progress = processValue
                    (it[2] as TextView).text = "Pressure frequency: $processValue Hz"
                }

                "STEPS" -> sensorsComponents[SensorType.TYPE_STEPS]?.let {
                    val processValue = jsonData.getJSONObject(key).getInt("value")
                    (it[0] as Switch).isChecked = jsonData.getJSONObject(key).getInt("switch") == 1
                    (it[1] as SeekBar).isEnabled = jsonData.getJSONObject(key).getInt("switch") == 1
                    (it[1] as SeekBar).progress = processValue
                    (it[2] as TextView).text = "PTYPE STEPS: $processValue Hz"
                }
            }

        }

    }
    fun extractJsonDataFromCsv(filePath: String) {
        val jsonTag = "JSON_DATA_START"
        val file = File(filePath)
        var jsonData: JSONObject? = null

        if (file.exists()) {
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                var isJsonData = false
                val jsonStringBuilder = StringBuilder()

                while (reader.readLine().also { line = it } != null) {
                    if (line == jsonTag) {
                        isJsonData = true
                        continue
                    }
                    if (isJsonData) {
                        jsonStringBuilder.append(line)
                    }
                }

                if (jsonStringBuilder.isNotEmpty()) {
                    jsonData = JSONObject(jsonStringBuilder.toString())
                }
            }
        }

        jsonData?.let { reflectJsonDataToWatch(it) }
    }

    // ---------------------------------------------------------------------------------------------
    fun loadMutableList(key:String): MutableList<String> {
        val jsonString = sharedPreferences.getString(key, "")
        val type: Type = object : TypeToken<MutableList<Any>>() {}.type

        return Gson().fromJson(jsonString, type) ?: mutableListOf()
    }

    // ---------------------------------------------------------------------------------------------

    fun saveSettings(){
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
                "Settings for ${entry.key} changed to " +
                        "${WatchActivityHandler.sensorsSelected[entry.key].toString()}."
            )
        }
        Log.d("SettingsActivity", "Settings saved.")
    }

    // ---------------------------------------------------------------------------------------------

    fun saveDefaultSettings(){
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
            if(spkey == "GNSS")
            {
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
                "Default settings for ${entry.key} changed to " +
                        "${WatchActivityHandler.sensorsSelected[entry.key].toString()}."
            )
        }
        editor.apply()
        Log.d("SettingsActivity", "Default settings saved.")
        Toast.makeText(applicationContext, "Default settings saved.", Toast.LENGTH_SHORT).show()
    }
}