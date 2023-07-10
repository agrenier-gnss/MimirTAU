package com.mimir.sensors

import android.content.Context
import android.hardware.Sensor
import android.os.HandlerThread
import android.util.Log
import java.util.Collections.synchronizedList



class SensorsHandler(val context: Context) {

    var mSensors = mutableListOf<CustomSensor>()
    val mSensorsResults = synchronizedList(mutableListOf<Any>())

    // ---------------------------------------------------------------------------------------------

    private var fileHandler: FileHandler
    private var handlerThread: HandlerThread

    init {
        // Setup file
        handlerThread = HandlerThread("").apply {
            start()
            fileHandler = FileHandler(context, looper)
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun addSensor(_type : SensorType, _samplingFrequency : Int = 1000){
        when(_type){
            SensorType.TYPE_ACCELEROMETER ->
                mSensors.add(MotionSensor(this.context, fileHandler, _type, "ACC", _samplingFrequency, mSensorsResults))
            SensorType.TYPE_GYROSCOPE ->
                mSensors.add(MotionSensor(this.context, fileHandler, _type, "GYRO", _samplingFrequency, mSensorsResults))
            SensorType.TYPE_MAGNETIC_FIELD ->
                mSensors.add(MotionSensor(this.context, fileHandler, _type, "MAG", _samplingFrequency, mSensorsResults))
            SensorType.TYPE_ACCELEROMETER_UNCALIBRATED ->
                mSensors.add(UncalibratedMotionSensor(this.context, fileHandler, _type, "ACC_UNCAL", _samplingFrequency, mSensorsResults))
            SensorType.TYPE_GYROSCOPE_UNCALIBRATED ->
                mSensors.add(UncalibratedMotionSensor(this.context, fileHandler, _type, "GYRO_UNCAL", _samplingFrequency, mSensorsResults))
            SensorType.TYPE_MAGNETIC_FIELD_UNCALIBRATED ->
                mSensors.add(UncalibratedMotionSensor(this.context, fileHandler, _type, "MAG_UNCAL", _samplingFrequency, mSensorsResults))

            SensorType.TYPE_PRESSURE ->
                mSensors.add(EnvironmentSensor(this.context, fileHandler, _type, "PRESSURE", _samplingFrequency, mSensorsResults))

            SensorType.TYPE_GNSS_LOCATION ->
                mSensors.add(GnssLocationSensor(this.context, fileHandler, mSensorsResults))
            SensorType.TYPE_GNSS_MEASUREMENTS ->
                mSensors.add(GnssMeasurementSensor(this.context, fileHandler, mSensorsResults))
            SensorType.TYPE_GNSS_MESSAGES ->
                mSensors.add(GnssNavigationMessageSensor(this.context, fileHandler, mSensorsResults))

            SensorType.TYPE_BLUETOOTH ->
                mSensors.add(BluetoothSensor(this.context, fileHandler, mSensorsResults))

            else -> {Log.w("SensorsHandler", "Sensor type $_type not supported.")}
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun startLogging(){

        // Enable logging in sensors
        for (_sensor in mSensors){
            _sensor.registerSensor()
        }

        Log.i("SensorsHandler", "Logging started")
    }

    // ---------------------------------------------------------------------------------------------

    fun stopLogging(){

        // disable logging in sensors
        for (_sensor in mSensors){
            _sensor.unregisterSensor()
        }

        // Write to file
        fileHandler.closeFile()

        Log.i("SensorsHandler", "Logging stopped")
    }

}