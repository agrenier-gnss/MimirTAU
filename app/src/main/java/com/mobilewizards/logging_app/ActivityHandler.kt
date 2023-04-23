package com.mobilewizards.logging_app

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.*

//this class handles logging data and log events all from one class
object ActivityHandler{


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

    private var gnssSensor = mutableListOf<GnssHandler>()
    private var imuSensor = mutableListOf<MotionSensorsHandler>()
    private var bleSensor = mutableListOf<BLEHandler>()

    //keeps track of the button state and synchronises them between activities
    private val buttonState = MutableLiveData<Boolean>(false)
    fun getButtonState(): LiveData<Boolean> {
        return buttonState
    }

    fun toggleButton(context: Context) {
        buttonState.value = !(buttonState.value ?: false)

        if(buttonState.value==true){
            startLogging(context)
        }
        else{
            stopLogging(context)
        }
    }

    fun getIsLogging(): Boolean{
        return isLogging
    }

    //Functions to both logging and stopping it.
    fun startLogging(context: Context){
        val motionSensors = MotionSensorsHandler(context)
        val gnss= GnssHandler(context)
        val ble =  BLEHandler(context)
        gnssSensor.add(gnss)
        imuSensor.add(motionSensors)
        bleSensor.add(ble)
        if(IMUToggle){motionSensors.setUpSensors()}
        if (GNSSToggle) {gnss.setUpLogging()}
        if(BLEToggle){ble.setUpLogging()}
    }

    fun stopLogging(context: Context){
        if (GNSSToggle) {
            gnssSensor[0].stopLogging(context)}
        if(IMUToggle){
            imuSensor[0].stopLogging()
        }
        if(BLEToggle){
            bleSensor[0].stopLogging()
        }
        gnssSensor.clear()
        imuSensor.clear()
        bleSensor.clear()

    }

    fun getToggle(tag: String): Boolean{
        if(tag.equals("GNSS")){
            return GNSSToggle
        }
        else if(tag.equals("IMU")){
            return IMUToggle
        }
        else if(tag.equals("Barometer")){
            return barometerToggle
        }
        else if(tag.equals("Magnetometer")){
            return magnetometerToggle
        }
        else if(tag.equals("Bluetooth")){
            return BLEToggle
        }
        return false
    }

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

    //counter for keeping time on logging
    var counterThread : CounterThread? = null

    // Check if thread is alive to rightfully enable/disable buttons
    fun startCounterThread() {
        if (counterThread?.isAlive == true) {
            // Implementation of code that require concurrent threads to be running
        }
        counterThread = CounterThread()
        counterThread?.start()
    }

    fun stopCounterThread() {
        if (counterThread != null) {
            counterThread?.cancel()
            counterThread = null
        }
    }

    class MyTimer {
        private val timer = Timer()

        private var currentTime: Long = 0

        fun startTimer(callback: () -> Unit) {
            timer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    updateTime()
                    callback()
                }
            }, 0, 1000) // update every second
        }

        fun stopTimer() {
            timer.cancel()
        }

        fun getCurrentTime(): String {
            return currentTime.toString()
        }

        private fun updateTime() {
            currentTime = System.currentTimeMillis()
        }
    }

    fun getLogData(tag: String) {
        if(tag.equals("Time")) {

        } else if(tag.equals("GNSS")) {

        } else if(tag.equals("IMU")){

        } else if(tag.equals("Barometer")){

        } else if(tag.equals("Magnetometer")){

        } else if(tag.equals("Bluetooth")) {

        }
    }

}