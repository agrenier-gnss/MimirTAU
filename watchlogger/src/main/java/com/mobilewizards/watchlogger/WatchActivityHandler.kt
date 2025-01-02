package com.mobilewizards.watchlogger

object WatchActivityHandler {
    // keeps track of the most recently sent file status
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