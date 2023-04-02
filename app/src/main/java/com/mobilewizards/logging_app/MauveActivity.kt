package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView

class MauveActivity : AppCompatActivity() {

    private var isToggledOn: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mauve)
        supportActionBar?.hide()

        val motionSensors = MotionSensorsHandler(this)
        var gnssToggle = true
        val gnss = GnssHandler(this)
        val BLE = BLEHandler(this)

        val loggingButton = findViewById<Button>(R.id.loggingButton)
        val dataButton = findViewById<Button>(R.id.downloadDataButton)
        val loggingText = findViewById<TextView>(R.id.loggingTextView)

        loggingButton.setOnClickListener {
            if (isToggledOn) {
                //Stop logging
                motionSensors.stopLogging()
                if (gnssToggle) {gnss.stopLogging(this)}
                BLE.stopLogging()

                findViewById<Button>(R.id.loggingButton).text = "Start logging"

                loggingText.text = ""

                loggingButton.animate()
                    .translationYBy(-250f)
                    .setDuration(200)
                    .start()

                Handler().postDelayed({
                    dataButton.visibility = View.VISIBLE
                }, 100)

            } else {
                //Start logging
                motionSensors.setUpSensors()
                if (gnssToggle) {gnss.setUpLogging()}
                BLE.setUpLogging()

                findViewById<Button>(R.id.loggingButton).text = "Stop logging"

                dataButton.visibility = View.GONE

                loggingButton.animate()
                    .translationYBy(250f)
                    .setDuration(500)
                    .start()

                Handler().postDelayed({
                    loggingText.text = "Placeholder text ..."
                }, 300)

            }
            isToggledOn = !isToggledOn
        }

        val downloadButton = findViewById<Button>(R.id.downloadDataButton)
        downloadButton.setOnClickListener {

        }

        //change to another activity by sweeping
        var x1 = 0f
        var y1 = 0f
        var x2 = 0f
        var y2 = 0f

        findViewById<View>(R.id.activity_mauve_layout).setOnTouchListener { _, touchEvent ->
            when (touchEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    x1 = touchEvent.x
                    y1 = touchEvent.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    x2 = touchEvent.x
                    y2 = touchEvent.y
                    val deltaX = x2 - x1
                    val deltaY = y2 - y1
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        // swipe horizontal
                        if (Math.abs(deltaX) > 100) {
                            // left or right
                            if (deltaX < 0) {
                                // left swipe
                                val intent = Intent(this, LogEventActivity::class.java)
                                startActivity(intent)
                                true
                            } else if (deltaX > 0) {
                                // right swipe
                                val intent = Intent(this, MainActivity::class.java)
                                startActivity(intent)
                                true
                            }
                        }
                    }
                    // add a default return value of false here
                    false
                }
                else -> false
            }
        }

    }

}