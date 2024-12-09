package com.mobilewizards.watchlogger

object WatchActivityHandler {

    private var fileSendOk: Boolean = false

    // ---------------------------------------------------------------------------------------------

    fun fileSendStatus(fileSend: Boolean) {
        fileSendOk = fileSend
    }

    // ---------------------------------------------------------------------------------------------

    fun checkFileSend(): Boolean {
        return fileSendOk
    }

}