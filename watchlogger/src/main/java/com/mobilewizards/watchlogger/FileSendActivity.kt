package com.mobilewizards.logging_app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.mobilewizards.watchlogger.WatchActivityHandler
import com.mobilewizards.logging_app.databinding.ActivityFileSendBinding

class FileSendActivity: Activity() {

    private lateinit var binding: ActivityFileSendBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFileSendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fileSuccessful = WatchActivityHandler.checkFileSend()
        val logText = findViewById<TextView>(R.id.logInfoText)
        val logOkImage = findViewById<ImageView>(R.id.imageOk)
        logOkImage.visibility = View.GONE

        val logFailedImage = findViewById<ImageView>(R.id.imageFailed)
        logFailedImage.visibility = View.GONE

        if (fileSuccessful) {
            logOkImage.visibility = View.VISIBLE
            logText.text = "Log successfully sent to device"

        } else {
            logFailedImage.visibility = View.VISIBLE
            logText.text = "Log upload failed"
        }
    }
}