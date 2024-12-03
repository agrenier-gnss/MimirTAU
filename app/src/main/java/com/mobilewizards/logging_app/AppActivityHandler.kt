package com.mobilewizards.logging_app

import java.io.File
import com.mimir.sensors.SensorType

object AppActivityHandler {

    // Variable to save the files for sending
    private var filepaths = mutableListOf<File>()
    var fileSendOk : Boolean = false
    var sensorsSelected = mutableMapOf<SensorType, Pair<Boolean, Int>>()

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

}