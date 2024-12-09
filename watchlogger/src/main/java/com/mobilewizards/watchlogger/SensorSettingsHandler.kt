package com.mobilewizards.watchlogger


import android.content.Context
import android.content.SharedPreferences
import android.widget.Switch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.mimir.sensors.SensorType
import com.mobilewizards.logging_app.IDX_SWITCH


object SensorSettingsHandler {
    private const val SHARED_PREF_NAME = "DefaultSettings"

    const val IDX_BOOLEAN = 0
    const val IDX_VALUE = 1

    private lateinit var sharedPreferences: SharedPreferences
    var sensors = arrayOf(
        SensorType.TYPE_GNSS,
        SensorType.TYPE_IMU,
        SensorType.TYPE_PRESSURE,
        SensorType.TYPE_STEPS,
        SensorType.TYPE_SPECIFIC_ECG,
        SensorType.TYPE_SPECIFIC_PPG,
        SensorType.TYPE_SPECIFIC_GSR
    )

    var sensorStrings = arrayOf(
        "GNSS", "IMU", "PSR", "STEPS", "ECG", "PPG", "GSR"
    )

    var progressToFrequency = arrayOf(1, 5, 10, 50, 100, 200, 0)
    var frequencyToProgress: Map<Int, Int> =
        progressToFrequency.mapIndexed { index, frequency -> frequency to index }.toMap()

    val SensorToString: Map<SensorType, String> = sensors.zip(sensorStrings).toMap()
    val StringToSensor: Map<String, SensorType> = sensorStrings.zip(sensors).toMap()


    fun initializePreferences(context: Context) {
        sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)

        // Initialisation values if they are not present
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

    fun loadSensorValues(): MutableMap<SensorType, Pair<Boolean, Int>> {
        val sensorValues = mutableMapOf<SensorType, Pair<Boolean, Int>>()

        sensors.forEach { sensor ->
            val key = SensorToString[sensor] ?: return@forEach
            val default = mutableListOf(false, 0)
            val values = getSetting(key, default)

            val isEnabled = values[IDX_BOOLEAN] as Boolean
            val frequencyIndex = (values[IDX_VALUE] as Double).toInt()
            sensorValues[sensor] = Pair(isEnabled, frequencyIndex)
        }

        return sensorValues
    }


    fun saveSetting(key: SensorType, valuePair: Pair<Boolean, Int>) {
        val editor = sharedPreferences.edit()

        var enabled = valuePair.first
        var sensorValue = valuePair.second

        if (key == SensorType.TYPE_GNSS) {
            sensorValue = 0
        }

        val keyString = SensorToString[key]
        val jsonString = Gson().toJson(
            mutableListOf(
                enabled, sensorValue
            )
        )
        editor.putString(keyString, jsonString)
        editor.apply()
    }

    fun <T> getSetting(key: String, default: T): T {
        val jsonString = sharedPreferences.getString(key, null) ?: return default
        val type: Type = object: TypeToken<T>() {}.type
        return Gson().fromJson(jsonString, type) ?: default
    }


    fun contains(key: String): Boolean = sharedPreferences.contains(key)
}