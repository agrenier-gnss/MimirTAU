package com.mobilewizards.watchlogger

import android.util.Log
import java.io.File
import com.mimir.sensors.SensorType

object WatchActivityHandler {

    // Variable to save the files for sending
    private var filepaths = mutableListOf<File>()
    private val TAG = "watchLogger"
    var fileSendOk : Boolean = false
    var collectGnss : Boolean = true
    var collectImu : Boolean = false
    var collectEcg : Boolean = false
    var collectSteps : Boolean = false
    var collectGal : Boolean = false
    var selectedFrequency : Int = 5

    var sensorsSelected = mutableMapOf<SensorType, Pair<Boolean, Int>>()

    // ---------------------------------------------------------------------------------------------

    fun setFilePaths (filePath: File){
        Log.d(TAG, "Adding file")
        filepaths.add(filePath)
    }

    // ---------------------------------------------------------------------------------------------

    fun clearFilfPaths() {
        filepaths.clear()
    }

    // ---------------------------------------------------------------------------------------------

    fun fileSendStatus(fileSend: Boolean){
        fileSendOk = fileSend
    }

    // ---------------------------------------------------------------------------------------------

    fun checkFileSend(): Boolean {
        return fileSendOk
    }

    // ---------------------------------------------------------------------------------------------

    fun getFilePaths(): List<File> {
        return filepaths
    }

    // ---------------------------------------------------------------------------------------------

    fun changeGnssStatus(status: Boolean) {
        collectGnss = status
    }

    // ---------------------------------------------------------------------------------------------


    fun getGnssStatus() : Boolean {
        return collectGnss
    }

    // ---------------------------------------------------------------------------------------------

    fun changeImuStatus(status: Boolean) {
        collectImu = status
    }

    // ---------------------------------------------------------------------------------------------

    fun getImuStatus() : Boolean {
        return collectImu
    }

    // ---------------------------------------------------------------------------------------------

    fun changeEcgStatus(status: Boolean) {
        collectEcg = status
    }

    // ---------------------------------------------------------------------------------------------

    fun getEcgStatus() : Boolean {
        return collectEcg
    }

    // ---------------------------------------------------------------------------------------------

    fun changeGalStatus(status: Boolean) {
        collectGal = status
    }

    // ---------------------------------------------------------------------------------------------

    fun getGalStatus() : Boolean {
        return collectGal
    }

    // ---------------------------------------------------------------------------------------------

    fun changeFrequency(time: Int){
        selectedFrequency = time
    }

    // ---------------------------------------------------------------------------------------------

    fun getFrequency() : Int {
        return selectedFrequency
    }

}