package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SatelliteDetailsActivity : AppCompatActivity() {

    private lateinit var satelliteDetailsTable: TableLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_satellite_details)

        satelliteDetailsTable = findViewById(R.id.satelliteDetailsTable)

        // Retrieve the constellation type and satellite list
        val constellationType = intent.getStringExtra("CONSTELLATION_TYPE")
        val satelliteList = intent.getParcelableArrayListExtra<MainActivity.Satellite>("SATELLITE_LIST")

        // Set the activity title
        title = "Satellite details for $constellationType"

        if (satelliteList != null) {
            // Add the header row
            addHeaderRow()

            // Add rows for each satellite
            satelliteList.forEach { satellite ->
                addSatelliteRow(satellite)
            }
        }
    }

    private fun addHeaderRow() {
        val headerRow = TableRow(this)

        val headers = listOf("ID", "C/N0", "Elevation", "Azimuth", "Tracking")
        headers.forEach { header ->
            val textView = TextView(this).apply {
                text = header
                textSize = 16f
                setPadding(16, 8, 16, 8)
                setTextColor(resources.getColor(android.R.color.white, theme))
                setBackgroundResource(android.R.color.darker_gray)
            }
            headerRow.addView(textView)
        }

        satelliteDetailsTable.addView(headerRow)
    }

    private fun addSatelliteRow(satellite: MainActivity.Satellite) {
        val satelliteRow = TableRow(this)

        val details = listOf(
            satellite.svid.toString(),
            "${satellite.signal}",
            "${satellite.elevation}°",
            "${satellite.azimuth}°",
            if (satellite.tracking) "Yes" else "No"
        )

        details.forEach { detail ->
            val textView = TextView(this).apply {
                text = detail
                textSize = 14f
                setPadding(16, 8, 16, 8)
                setTextColor(resources.getColor(android.R.color.white, theme))
                setBackgroundResource(android.R.color.transparent)
            }
            satelliteRow.addView(textView)
        }

        satelliteDetailsTable.addView(satelliteRow)
    }

}
