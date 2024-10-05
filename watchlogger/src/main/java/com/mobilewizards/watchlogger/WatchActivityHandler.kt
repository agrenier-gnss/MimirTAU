package com.mobilewizards.watchlogger

import java.io.File
import com.mimir.sensors.SensorType

object WatchActivityHandler {

    // Variable to save the files for sending
    private var filepaths = mutableListOf<File>()
    private var fileSendOk: Boolean = false
    var sensorsSelected = mutableMapOf<SensorType, Pair<Boolean, Int>>()

    // ---------------------------------------------------------------------------------------------

    fun fileSendStatus(fileSend: Boolean) {
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