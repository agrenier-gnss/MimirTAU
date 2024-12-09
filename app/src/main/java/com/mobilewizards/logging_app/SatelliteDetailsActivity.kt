package com.mobilewizards.logging_app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SatelliteDetailsActivity : AppCompatActivity() {

    private lateinit var satelliteDetailsTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_satellite_details)

        satelliteDetailsTextView = findViewById(R.id.satelliteDetailsTextView)

        // Retrieve the constellation type and satellite list
        val constellationType = intent.getStringExtra("CONSTELLATION_TYPE")
        val satelliteList = intent.getParcelableArrayListExtra<MainActivity.Satellite>("SATELLITE_LIST")

        if (satelliteList != null) {
            // Build the details string for all satellites in the constellation
            val detailsBuilder = StringBuilder()
            detailsBuilder.append("Constellation: $constellationType\n\n")
            satelliteList.forEach { satellite ->
                detailsBuilder.append("Satellite ID: ${satellite.svid}\n")
                detailsBuilder.append("Constellation: ${satellite.constellationType}\n")
                detailsBuilder.append("Azimuth: ${satellite.azimuth}°\n")
                detailsBuilder.append("Elevation: ${satellite.elevation}°\n")
                detailsBuilder.append("Tracking: ${if (satellite.tracking) "Yes" else "No"}\n")
                detailsBuilder.append("\n")
            }

            // Set the details in the TextView
            satelliteDetailsTextView.text = detailsBuilder.toString()
        } else {
            satelliteDetailsTextView.text = "No satellite data available for $constellationType."
        }
    }
}




