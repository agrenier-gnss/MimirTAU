package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SatelliteDetailsActivity : AppCompatActivity() {

    private lateinit var satelliteDetailsTable: TableLayout
    private var constellationType: String? = null
    private var satelliteList: MutableList<MainActivity.Satellite> = mutableListOf()

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSatelliteData()
            updateHandler.postDelayed(this, 1000) // Update every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_satellite_details)

        satelliteDetailsTable = findViewById(R.id.satelliteDetailsTable)

        // Retrieve the constellation type and satellite list
        constellationType = intent.getStringExtra("CONSTELLATION_TYPE")
        satelliteList = intent.getParcelableArrayListExtra("SATELLITE_LIST") ?: mutableListOf()

        title = "Satellite details for $constellationType"

        if (satelliteList.isNotEmpty()) {
            // Add the header row
            addHeaderRow()

            // Populate the initial table rows
            satelliteList.forEach { satellite ->
                addSatelliteRow(satellite)
            }

            // Start updating the table
            updateHandler.post(updateRunnable)
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

    @SuppressLint("DefaultLocale")
    private fun addSatelliteRow(satellite: MainActivity.Satellite) {
        val satelliteRow = TableRow(this).apply { tag = satellite.svid }

        val details = listOf(
            satellite.svid.toString(),
            String.format("%.1f", satellite.signal),
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
            }
            satelliteRow.addView(textView)
        }

        satelliteDetailsTable.addView(satelliteRow)
    }

    @SuppressLint("DefaultLocale")
    private fun updateSatelliteData() {
        // Fetch the latest satellite data from MainActivity
        val updatedSatellites = MainActivity.currentSatellites
            .filter { it.constellationType == constellationType }
            .sortedBy { it.svid }

        // Get the current rows' tags
        val existingRows = mutableMapOf<Int, TableRow>()
        for (i in 1 until satelliteDetailsTable.childCount) { // Skip the header row
            val row = satelliteDetailsTable.getChildAt(i) as TableRow
            val svid = row.tag as Int
            existingRows[svid] = row
        }
        satelliteDetailsTable.removeViews(1, satelliteDetailsTable.childCount - 1)

        // Update or add rows
        updatedSatellites.forEach { updatedSatellite ->
            addSatelliteRow(updatedSatellite)
            val satelliteRow = existingRows[updatedSatellite.svid]
            if (satelliteRow != null) {
                // Update existing row
                val updatedDetails = listOf(
                    String.format("%.1f", updatedSatellite.signal),
                    "${updatedSatellite.elevation}°",
                    "${updatedSatellite.azimuth}°",
                    if (updatedSatellite.tracking) "Yes" else "No"
                )
                for (i in 1..3) { // Update only signal, elevation, and azimuth
                    (satelliteRow.getChildAt(i) as TextView).text = updatedDetails[i - 1]
                }
                existingRows.remove(updatedSatellite.svid)
            } else {
                // Add a new row
                addSatelliteRow(updatedSatellite)
            }
        }

        // Remove rows for satellites that disappeared
        existingRows.values.forEach { row ->
            satelliteDetailsTable.removeView(row)
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacks(updateRunnable)
    }
}