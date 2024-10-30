package com.mobilewizards.logging_app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.mimir.sensors.SensorType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

const val IDX_SWITCH   = 0
const val IDX_SEEKBAR  = 1
const val IDX_TEXTVIEW = 2
class SettingsActivity : AppCompatActivity() {

    private val sharedPrefName = "DefaultSettings"
    private lateinit var sharedPreferences: SharedPreferences
    private val progressToFrequency = arrayOf(1, 5, 10, 50, 100, 200, 0)
    private lateinit var sensorsComponents : MutableMap<String, MutableList<Any?>>
    private lateinit var bleHandler: BLEHandler
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)
        supportActionBar?.hide()
        val parentView = findViewById<ViewGroup>(R.id.square_layout)

        // Initialisation values
        sharedPreferences = getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("GNSS")) {
            val editor: SharedPreferences.Editor = sharedPreferences.edit()
            editor.putString("GNSS",   Gson().toJson(mutableListOf(true, 0)))
            editor.putString("IMU",   Gson().toJson(mutableListOf(false, 2)))
            editor.putString("PSR",   Gson().toJson(mutableListOf(false, 0)))
            editor.putString("STEPS", Gson().toJson(mutableListOf(false, 1)))
            editor.apply()
        }
        checkAndRequestBluetoothPermissions()

        // Load from shared preferences
        val sensorsInit = arrayOf("GNSS", "IMU", "PSR", "STEPS")

        //create a layout for each sensor in sensorList
        sensorsComponents = mutableMapOf()
        sensorsInit.forEach {
            var sensorParameters = loadMutableList(it)

            // Inflate the layout file that contains the TableLayout
            val tableLayout = layoutInflater.inflate(R.layout.layout_presets, parentView, false)
                .findViewById<TableLayout>(R.id.sensorSquarePreset)

            val row = tableLayout.getChildAt(0) as TableRow
            val sensorTitleTextView = row.findViewById<TextView>(R.id.sensorTitle)
            sensorTitleTextView.text = it

            var sensorSwitch = row.findViewById<SwitchCompat>(R.id.sensorSwitch)
            sensorSwitch.isChecked = sensorParameters[0] as Boolean
            sensorSwitch.isEnabled = !ActivityHandler.isLogging() // Disable toggling sensor if logging is ongoing

            var sensorStateTextView = row.findViewById<TextView>(R.id.sensorState)
            setStateTextview(sensorSwitch.isChecked, sensorStateTextView)

            val row2 = tableLayout.getChildAt(1) as TableRow
            val description = row2.findViewById<TextView>(R.id.description)

            sensorSwitch.setOnCheckedChangeListener { _, isChecked ->
                setStateTextview(sensorSwitch.isChecked, sensorStateTextView)
                ActivityHandler.setToggle(it) //toggle the status in singleton
            }

            // Create the layout for each sensor
            if (it == "GNSS") {
                // Goes here if frequency is can not be changed
                description.text = "1 Hz only" // Change the description text
                tableLayout.removeViewAt(2) // Remove the row with the slider.
                sensorsComponents[it] = mutableListOf(sensorSwitch, null)
            } else {
                // Goes here if frequency can be changed
                description.text = "Sampling frequency"
                val row3 = tableLayout.getChildAt(2) as TableRow
                val slider = row3.findViewById<SeekBar>(R.id.sensorSlider)
                slider.max = progressToFrequency.size - 1
                slider.progress =
                    (sensorParameters[1] as Double).toInt() //set slider value to slider
                slider.isEnabled =
                    !ActivityHandler.isLogging() // Disable changing slider if logging is ongoing

                val sliderValue = row3.findViewById<TextView>(R.id.sliderValue)
                sliderValue.text =
                    "${progressToFrequency[(sensorParameters[1] as Double).toInt()]} Hz" //set slider value to a text view

                slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (progress != 6) {
                            sliderValue.text = "${progressToFrequency[progress]} Hz"
                        } else {
                            sliderValue.text = "\u221E"
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        // Not used
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        // Not used
                    }
                })

                sensorsComponents[it] = mutableListOf(sensorSwitch, slider)
            }

            // Remove the tableLayout's parent, if it has one
            (tableLayout.parent as? ViewGroup)?.removeView(tableLayout)

            // Add the TableLayout to the parent view
            parentView.addView(tableLayout)
        }
        saveSettings() // Save default settings

        // Saving settings
        val btnSave = findViewById<Button>(R.id.button_save)
        btnSave.setOnClickListener {
            saveSettings()
            setResult(RESULT_OK)
            finish() // Close activity
        }

        // Save current settings as default
        val btnDefault = findViewById<Button>(R.id.button_default)
        btnDefault.setOnClickListener {
            saveDefaultSettings()
        }

        //scan button
        val btnScan = findViewById<Button>(R.id.button_scan)
        btnScan.setOnClickListener{
      /*      if(btnScan.text.equals("Scan")){
                btnScan.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
                btnScan.setText("Stop")
                scanBleDevice()
            }else{
                btnScan.setBackgroundColor(ContextCompat.getColor(this, R.color.tropical_indigo))
                btnScan.setText("Scan")
                stopScanBleDevice()
            }*/
            showPairedDevices()

        }
    }


    // ---------------------------------------------------------------------------------------------


    private fun checkAndRequestBluetoothPermissions() {

        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1)
        } else {
            bleHandler = BLEHandler(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val deniedPermissions = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    deniedPermissions.add(permissions[i])
                }
            }

            if (deniedPermissions.isNotEmpty()) {
                // All permissions are granted, proceed with Bluetooth operations
                Toast.makeText(this, "${deniedPermissions.joinToString(", ")}", Toast.LENGTH_LONG).show()
            } else {
                // Permissions are denied, show a message to the user
                bleHandler = BLEHandler(this)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun showPairedDevices(){
        val pairedDevices : Set<BluetoothDevice>? = bleHandler.getPairedDevices()
        val dialogView = layoutInflater.inflate(R.layout.custom_dialog, null)
        val parentLayout: LinearLayout = dialogView.findViewById(R.id.dialog_parent)

        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Paired Devices")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = dialogBuilder.create()
        //add devices layout
        val inflater: LayoutInflater = LayoutInflater.from(this)
        if (pairedDevices != null) {
            for(item in pairedDevices){
                val itemView: View = inflater.inflate(R.layout.dialog_item_layout, parentLayout, false)
                val itemButton: Button  = itemView.findViewById(R.id.dialog_item_button)
                itemButton.text = item.name
                itemButton.setOnClickListener {
                     bleHandler.connectPairedDevice(item.address)
                    alertDialog.dismiss()
                }
                parentLayout.addView(itemView)

            }
        }else{
            val itemView: View = inflater.inflate(R.layout.dialog_item_layout, parentLayout, false)
            val itemButton: Button  = itemView.findViewById(R.id.dialog_item_button)
            itemButton.text = "no devices"
            parentLayout.addView(itemView)
        }


        alertDialog.show()
    }




    // ---------------------------------------------------------------------------------------------

    fun saveSettings(){
        sensorsComponents.forEach { entry ->
            var mkey : SensorType = SensorType.TYPE_GNSS
            when(entry.key){
                "GNSS" -> mkey = SensorType.TYPE_GNSS
                "IMU"  -> mkey = SensorType.TYPE_IMU
                "PSR"  -> mkey = SensorType.TYPE_PRESSURE
                "STEPS"-> mkey = SensorType.TYPE_STEPS
            }
            if (entry.key == "GNSS") {
                ActivityHandler.sensorsSelected[mkey] = Pair(
                    (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean, 1
                )
            } else {
                ActivityHandler.sensorsSelected[mkey] = Pair(
                    (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean,
                    progressToFrequency[(entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int]
                )
            }
            // Added health sensor for LoggingService
            ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_ECG] = Pair(false, 0)
            ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_PPG] = Pair(false, 0)
            ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_GSR] = Pair(false, 0)
            Log.d(
                "SettingsActivity",
                "Settings for ${entry.key} changed to " +
                        "${ActivityHandler.sensorsSelected[mkey].toString()}."
            )
        }
        Log.d("SettingsActivity", "Settings saved.")
    }

    // ---------------------------------------------------------------------------------------------

    fun saveDefaultSettings(){
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        sensorsComponents.forEach { entry ->
            var mkey : SensorType = SensorType.TYPE_GNSS
            when(entry.key){
                "GNSS" -> mkey = SensorType.TYPE_GNSS
                "IMU"  -> mkey = SensorType.TYPE_IMU
                "PSR"  -> mkey = SensorType.TYPE_PRESSURE
                "STEPS"-> mkey = SensorType.TYPE_STEPS
            }
            if(entry.key == "GNSS")
            {
                editor.putString(
                    entry.key, Gson().toJson(
                        mutableListOf(
                            (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean, 0)
                    )
                )
            } else {
                editor.putString(
                    entry.key, Gson().toJson(
                        mutableListOf(
                            (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean,
                            (entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int
                        )
                    )
                )
            }
            Log.d(
                "SettingsActivity",
                "Default settings for ${entry.key} changed to " +
                        "${ActivityHandler.sensorsSelected[mkey].toString()}."
            )
        }
        editor.apply()
        Log.d("SettingsActivity", "Default settings saved.")
        Toast.makeText(applicationContext, "Default settings saved.", Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------------------------------------

    fun scanBleDevice(){
        val filters: List<ScanFilter> = emptyList()
        val settings: ScanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
       // bleHandler.setUpLogging()
        bleHandler.setUpLoggingWithFilter(filters,settings)
    }

    fun stopScanBleDevice(){
        bleHandler.stopScanDevice()
        val scanResult = bleHandler.getBLEValues()
        val scanDeviceResult = bleHandler.getBlEDeviceValues();
        displayScanResults(scanResult, scanDeviceResult)
    }

    @SuppressLint("MissingPermission")
    private fun displayScanResults(scanResults: MutableList<String>, scanDevicesResults:MutableList<BluetoothDevice>) {
        // Display or process the scan results
        val targetMacAddress = "90:F0:52:BD:C1:24"
        scanResults.forEach { result ->
            Log.d("ScanResult", result)
        }
        scanDevicesResults.forEach{ result ->
            Log.d("ScanResultDevice", "${result.getAddress()}:${result.type}:${result.name}")
            if(result.address.equals(targetMacAddress)){
                Log.d("found the target device", "found the target device")
            }
        }
    }

    // Creates main_menu.xml
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    // ---------------------------------------------------------------------------------------------

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.changeParameters -> {
                val setupIntent = Intent(applicationContext, SetupActivity::class.java)
                startActivity(setupIntent)
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------

    fun setStateTextview(enabled: Boolean,textview: TextView) {
        if (enabled) {
            textview.text = "Enabled"
        } else {
            textview.text = "Disabled"
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadMutableList(key:String): MutableList<String> {
        val jsonString = sharedPreferences.getString(key, "")
        val type: Type = object : TypeToken<MutableList<Any>>() {}.type

        return Gson().fromJson(jsonString, type) ?: mutableListOf()
    }
}