package com.mobilewizards.logging_app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mimir.sensors.SensorType
import java.text.SimpleDateFormat
import java.util.*

import com.mimir.sensors.SensorsHandler
import java.io.Serializable

//this class handles logging data and log events all from one class
object ActivityHandler{

    var sensorsSelected = mutableMapOf<SensorType, Pair<Boolean, Int>>()

    private var isLogging: Boolean = false

    private var IMUFrequency: Int = 10
    private var barometerFrequency: Int = 1
    private var magnetometerFrequency: Int = 1

    //Boolean values to enable or disable sensors.
    private var IMUToggle: Boolean = true
    private var GNSSToggle: Boolean = true
    private var barometerToggle: Boolean = true
    private var magnetometerToggle: Boolean = true
    private var BLEToggle: Boolean = true

    //Lists where sensors will be put when logging.
    var gnssSensor = mutableListOf<GnssHandler>()
    var imuSensor = mutableListOf<MotionSensorsHandler>()
    var bleSensor = mutableListOf<BLEHandler>()

    lateinit var sensorsHandler : SensorsHandler

    // Amount of logged events
    private var IMULogs: Int = 0
    private var GNSSLogs: Int = 0
    private var barometerLogs: Int = 0
    private var magnetometerLogs: Int = 0
    private var BLELogs: Int = 0

    // Survey start time
    private var surveyStartTime: String = "Time not set"

    // Keeps track of logging_button state in MauveActivity
    private val buttonState = MutableLiveData<Boolean>(false)

    // ---------------------------------------------------------------------------------------------

    fun getButtonState(): LiveData<Boolean> {
        return buttonState
    }

    // ---------------------------------------------------------------------------------------------

    fun toggleButton(context: Context) {
        buttonState.value = !(buttonState.value ?: false)

//        if(buttonState.value==true){
//            startLogging(context)
//        }
//        else{
//            stopLogging(context)
//        }
    }

    // ---------------------------------------------------------------------------------------------

    fun isLogging(): Boolean{
        return isLogging
    }

    // ---------------------------------------------------------------------------------------------

    fun startLogging(context: Context){

//        val motionSensors = MotionSensorsHandler(context)
//        val gnss = GnssHandler(context)
//       val ble =  BLEHandler(context)
//
//        gnssSensor.add(gnss)
//        imuSensor.add(motionSensors)
//        bleSensor.add(ble)
//
//        if(IMUToggle || getToggle("Magnetometer") || getToggle("Barometer")){
//            motionSensors.setUpSensors(IMUFrequency, magnetometerFrequency, barometerFrequency)}
//        if (GNSSToggle) {gnss.setUpLogging()}
//        if(BLEToggle){ble.setUpLogging()}
//
        isLogging = true
        setSurveyStartTime()

        // Register sensors
        sensorsHandler = SensorsHandler(context)

        // Motion sensors
        if(getToggle("IMU")) {
            sensorsHandler.addSensor(SensorType.TYPE_ACCELEROMETER, (1.0/IMUFrequency * 1e6).toInt())
            sensorsHandler.addSensor(SensorType.TYPE_GYROSCOPE, (1.0/IMUFrequency * 1e6).toInt())
            sensorsHandler.addSensor(SensorType.TYPE_ACCELEROMETER_UNCALIBRATED,(1.0/IMUFrequency * 1e6).toInt())
            sensorsHandler.addSensor(SensorType.TYPE_GYROSCOPE_UNCALIBRATED, (1.0/IMUFrequency * 1e6).toInt())
        }
        if(getToggle("Magnetometer")) {
            sensorsHandler.addSensor(SensorType.TYPE_MAGNETIC_FIELD, (1.0/magnetometerFrequency * 1e6).toInt())
            sensorsHandler.addSensor(SensorType.TYPE_MAGNETIC_FIELD_UNCALIBRATED, (1.0/magnetometerFrequency * 1e6).toInt())
        }
        if(getToggle("Barometer")){
            sensorsHandler.addSensor(SensorType.TYPE_PRESSURE, (1.0/barometerFrequency * 1e6).toInt())
        }

        // GNSS Sensor
        if(getToggle("GNSS")){
            sensorsHandler.addSensor(SensorType.TYPE_GNSS_LOCATION)
            sensorsHandler.addSensor(SensorType.TYPE_GNSS_MEASUREMENTS)
            sensorsHandler.addSensor(SensorType.TYPE_GNSS_MESSAGES)
            sensorsHandler.addSensor(SensorType.TYPE_FUSED_LOCATION)
        }

        // BLE Sensor
        // TODO review BLE logging
//        if(getToggle("Bluetooth")){
//            sensorsHandler.addSensor(SensorType.TYPE_BLUETOOTH)
//        }

        sensorsHandler.startLogging()
    }

    // ---------------------------------------------------------------------------------------------

    fun stopLogging(context: Context){

//        if (GNSSToggle) {
//            gnssSensor[0].stopLogging(context)}
//        if(IMUToggle || getToggle("Magnetometer") || getToggle("Barometer")){
//            imuSensor[0].stopLogging()
//        }
        if(BLEToggle){
            bleSensor[0].stopLogging()
        }
//
//        gnssSensor.clear()
//        imuSensor.clear()
        bleSensor.clear()
        isLogging = false

        sensorsHandler.stopLogging()
    }

    // ---------------------------------------------------------------------------------------------

    // Get info on whether the sensor will be logged or not
    fun getToggle(type: String): Boolean{
        when(type)
        {
            "IMU",
                -> return IMUToggle
           "Magnetometer"
                -> return magnetometerToggle
            "Barometer"
                -> return barometerToggle
            "GNSS"
                -> return GNSSToggle
            else -> return false
        }
    }

    // ---------------------------------------------------------------------------------------------

    // Toggle sensor status between true and false whether it will be logged or not
    fun setToggle(tag: String){
        if(tag.equals("GNSS")){
             GNSSToggle = !GNSSToggle
        }
        else if(tag.equals("IMU")){
            IMUToggle = !IMUToggle
        }
        else if(tag.equals("Barometer")){
            barometerToggle = !barometerToggle
        }
        else if(tag.equals("Magnetometer")){
            magnetometerToggle = !magnetometerToggle
        }
        else if(tag.equals("Bluetooth")){
            BLEToggle = !BLEToggle
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getFrequency(tag: String): Int{
        if(tag.equals("IMU")){
            return IMUFrequency
        }
        else if(tag.equals("Barometer")){
            return barometerFrequency
        }
        else if(tag.equals("Magnetometer")){
            return magnetometerFrequency
        }
        return 0
    }

    // ---------------------------------------------------------------------------------------------

    fun setFrequency(tag: String, value: Int){

        if(tag.equals("IMU")){
            IMUFrequency = value
        }
        else if(tag.equals("Barometer")){
            barometerFrequency = value
        }
        else if(tag.equals("Magnetometer")){
            magnetometerFrequency = value
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun getLogData(tag: String): String {
        if(tag.equals("GNSS")) {
            return GNSSLogs.toString()
        } else if(tag.equals("IMU")){
            return IMULogs.toString()
        } else if(tag.equals("Barometer")){
            return barometerLogs.toString()
        } else if(tag.equals("Magnetometer")){
            return magnetometerLogs.toString()
        } else if(tag.equals("Bluetooth")) {
            return BLELogs.toString()
        }
        return 0.toString()
    }

    // ---------------------------------------------------------------------------------------------

    fun setSurveyStartTime() {
        val currentTime = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("HH:mm")
        surveyStartTime = dateFormat.format(currentTime)
    }

    // ---------------------------------------------------------------------------------------------

    fun getSurveyStartTime(): String {
        return surveyStartTime
    }

    // ---------------------------------------------------------------------------------------------

}