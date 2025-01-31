package com.mobilewizards.logging_app

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.mimir.sensors.SensorType

object PhoneSensorSettingsHandler {
    // handler for loading and saving phone specific settings from the settings file
    // used whenever setting parameters are required or when setting parameters are changed
    private const val SHARED_PREF_NAME = "PhoneDefaultSettings"


    const val IDX_BOOLEAN = 0
    const val IDX_VALUE = 1

    lateinit var sharedPreferences: SharedPreferences

    // maybe add magnetometer and bluetooth in the future
    var sensors = arrayOf(
        SensorType.TYPE_GNSS,
        SensorType.TYPE_IMU,
        SensorType.TYPE_PRESSURE,
        SensorType.TYPE_STEPS,
    )

    // maybe add magnetometer and bluetooth in the future
    var sensorStrings = arrayOf(
        "GNSS", "IMU", "PSR", "STEPS"
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
            editor.apply()
        }

    }

    fun loadSensorValues(): MutableMap<SensorType, Pair<Boolean, Int>> {
        // load all sensor values from the file and return them as a map
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
        // save a particular sensor setting to file
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
        // get setting parameters as a pair of (bool, int) in string form
        val jsonString = sharedPreferences.getString(key, null) ?: return default
        val type: Type = object: TypeToken<T>() {}.type
        return Gson().fromJson(jsonString, type) ?: default
    }


    fun contains(key: String): Boolean = sharedPreferences.contains(key)
}