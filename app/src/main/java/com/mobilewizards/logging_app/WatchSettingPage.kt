package com.mobilewizards.logging_app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.wearable.Wearable
import com.mimir.sensors.SensorType
import org.json.JSONObject


class WatchSettingPage: Fragment() {
    private val TAG = "PhoneWatchSettings"
    private lateinit var sensorsComponents: MutableMap<SensorType, MutableList<Any?>>
    private lateinit var bleHandler: BLEHandler

    interface SettingsFragmentListener {
        fun onSaveSettings()
    }

    private lateinit var listener: SettingsFragmentListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SettingsFragmentListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement SettingsFragmentListener")
        }
    }

    // ---------------------------------------------------------------------------------------------


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_settings_watch, container, false)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        WatchSensorSettingsHandler.initializePreferences(requireContext())

        checkAndRequestBluetoothPermissions()

        // Save current settings as default
        val btnDefault = view.findViewById<Button>(R.id.button_watch_save)
        btnDefault.setOnClickListener {
            saveSettings()
        }

        //control button
        val btnControl = view.findViewById<Button>(R.id.button_control)
        btnControl.setOnClickListener {
            sendSettingToWatch()
        }

        // initialize the sensor UI components
        initializeSensorComponents()

        // load the settings from the file to the UI
        loadSharedPreferencesToUi()
    }

    // ---------------------------------------------------------------------------------------------

    private fun initializeSensorComponents() {

        // initialize the sensor UI components to a map from sensor type to UI component
        //create a layout for each sensor in sensorList
        sensorsComponents = mutableMapOf()

        val parentView = requireView().findViewById<ViewGroup>(R.id.square_layout)
        WatchSensorSettingsHandler.sensors.forEach {

            // get the parameters for particular sensor from the file
            val sensorString = WatchSensorSettingsHandler.SensorToString[it]!!
            val sensorParameters = WatchSensorSettingsHandler.getSetting(sensorString, mutableListOf(false, 0))

            val sensorEnabled = sensorParameters[0] as Boolean
            val sensorProgressIndex = (sensorParameters[1] as Double).toInt()

            // Inflate the layout file that contains the TableLayout
            val tableLayout = layoutInflater.inflate(R.layout.layout_presets, parentView, false)
                .findViewById<TableLayout>(R.id.sensorSquarePreset)

            val row = tableLayout.getChildAt(0) as TableRow
            val sensorTitleTextView = row.findViewById<TextView>(R.id.sensorTitle)
            sensorTitleTextView.text = sensorString

            val sensorSwitch = row.findViewById<SwitchCompat>(R.id.sensorSwitch)
            sensorSwitch.isChecked = sensorEnabled

            val sensorStateTextView = row.findViewById<TextView>(R.id.sensorState)
            setStateTextview(sensorSwitch.isChecked, sensorStateTextView)

            val row2 = tableLayout.getChildAt(1) as TableRow
            val description = row2.findViewById<TextView>(R.id.description)

            // listener to switch changes
            sensorSwitch.setOnCheckedChangeListener(createSwitchListener(sensorStateTextView, it))

            // Create the layout for each sensor
            if (it == SensorType.TYPE_GNSS) {
                // Goes here if frequency is can not be changed
                description.text = "1 Hz only" // Change the description text
                tableLayout.removeViewAt(2) // Remove the row with the slider.
                sensorsComponents[it] = mutableListOf(sensorSwitch, null, null)
            } else {
                // Goes here if frequency can be changed
                description.text = "Sampling frequency"

                val row3 = tableLayout.getChildAt(2) as TableRow
                val slider = row3.findViewById<SeekBar>(R.id.sensorSlider)

                slider.max = WatchSensorSettingsHandler.progressToFrequency.size - 1
                slider.progress = sensorProgressIndex //set slider value to slider
                slider.isEnabled = sensorEnabled // Disable changing slider if sensor isn't enabled

                val sliderValue = row3.findViewById<TextView>(R.id.sliderValue)
                updateTextView(sliderValue, sensorProgressIndex) //set slider value to a text view

                // listens to slider changes
                slider.setOnSeekBarChangeListener(createSeekBarListener(sliderValue))

                sensorsComponents[it] = mutableListOf(sensorSwitch, slider, sliderValue)
            }

            // Remove the tableLayout's parent, if it has one
            (tableLayout.parent as? ViewGroup)?.removeView(tableLayout)

            // Add the TableLayout to the parent view
            parentView.addView(tableLayout)
        }
    }


    // ---------------------------------------------------------------------------------------------

    private fun loadSharedPreferencesToUi() {

        // Loads shared preferences for sensor settings from the file and updates the UI accordingly
        Log.d(TAG, "UI updated from shared preferences.")

        // Load Initialisation values from sharedPreferences to the sensor types
        val sensorsInit = WatchSensorSettingsHandler.loadSensorValues()

        // Set the initialisation values
        sensorsInit.forEach { entry ->
            val currentSensor = sensorsComponents[entry.key]!! // should never be null

            // The IDE says that the casts below cannot succeed, but they do so ignore that
            val sensorEnabled = entry.value.first
            val sensorFrequencyIndex = entry.value.second

            (currentSensor[IDX_SWITCH] as SwitchCompat).isChecked = sensorEnabled
            if (entry.key != SensorType.TYPE_GNSS) {
                (currentSensor[IDX_SEEKBAR] as SeekBar).isEnabled = sensorEnabled
                (currentSensor[IDX_SEEKBAR] as SeekBar).progress = sensorFrequencyIndex
                val textView = currentSensor[IDX_TEXTVIEW] as TextView
                updateTextView(textView, sensorFrequencyIndex)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun createSwitchListener(
        sensorStateTextView: TextView, sensor: SensorType
    ): CompoundButton.OnCheckedChangeListener {
        return CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            // change text to enabled / disabled
            setStateTextview(isChecked, sensorStateTextView)

            // makes sure that ECG and GSR sensors aren't enabled at the same time
            // if ECG is enabled, GSR is disabled and vice versa
            if (sensor == SensorType.TYPE_SPECIFIC_ECG && isChecked) {
                (sensorsComponents[SensorType.TYPE_SPECIFIC_GSR]!![IDX_SWITCH] as SwitchCompat).isChecked = false

            } else if (sensor == SensorType.TYPE_SPECIFIC_GSR && isChecked) {
                (sensorsComponents[SensorType.TYPE_SPECIFIC_ECG]!![IDX_SWITCH] as SwitchCompat).isChecked = false
            }

            // enable / disable the bar based on the switch being checked or not
            val seekBar =
                sensorsComponents.entries.find { it.value[IDX_SWITCH] == buttonView }?.value?.get(1) as? SeekBar
            seekBar?.isEnabled = isChecked
        }
    }


    // ---------------------------------------------------------------------------------------------

    private fun createSeekBarListener(textView: TextView): SeekBar.OnSeekBarChangeListener {
        // Define a common seekbar listener
        return object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateTextView(textView, progress)

            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Your common implementation for onStartTrackingTouch
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Your common implementation for onStopTrackingTouch
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    @SuppressLint("SimpleDateFormat")
    fun sendSettingToWatch() {
        getWatchNodeId { nodeIds ->
            Log.d(TAG, "Received nodeIds: $nodeIds")
            // Check if there are connected nodes
            val connectedNode: String = if (nodeIds.size > 0) nodeIds[0] else ""

            if (connectedNode.isEmpty()) {
                Log.d(TAG, "no nodes found")
                Toast.makeText(requireContext(), "Watch not connected", Toast.LENGTH_SHORT).show()
            } else {
                sendSettingsJson(connectedNode, requireContext())
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateTextView(textView: TextView, progressIndex: Int) {
        // updates the Hz text based on the slider progress index
        val progressHz = WatchSensorSettingsHandler.progressToFrequency[progressIndex]
        textView.text = if (progressHz == 0) infinitySymbol else "$progressHz Hz"
    }

    // ---------------------------------------------------------------------------------------------


    private fun getWatchNodeId(callback: (ArrayList<String>) -> Unit) {
        // get connected watch io for sending data
        val nodeIds = ArrayList<String>()
        Wearable.getNodeClient(requireActivity()).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Log.d(TAG, "connected node in getWatchId ${node.id}")
                nodeIds.add(node.id)
            }
            callback(nodeIds)
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun generateSettingsJson(): String {
        // generates JSON from the UI components for the sensor settings for sending them to the watch
        val jsonData = JSONObject()
        sensorsComponents.forEach { entry ->
            val sensorString = WatchSensorSettingsHandler.SensorToString[entry.key]!!

            jsonData.put(sensorString, JSONObject().apply {
                put("switch", (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean)
                //  GNSS is always 1hz so we don't send value
                if (entry.key != SensorType.TYPE_GNSS) {
                    val seekbarValue = (entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int
                    put(
                        "value", WatchSensorSettingsHandler.progressToFrequency[seekbarValue]
                    )
                }

            })

        }

        return jsonData.toString()
    }

    // ---------------------------------------------------------------------------------------------

    private fun sendSettingsJson(nodeId: String, context: Context) {
        // Sends settings JSON to connected watch
        val messageClient = Wearable.getMessageClient(context)
        val watchSettingsPath = "/watch_settings" // Message identifier tag
        val settingsJsonString = generateSettingsJson()

        messageClient.sendMessage(nodeId, watchSettingsPath, settingsJsonString.toByteArray())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Settings JSON sent successfully. Value: $settingsJsonString")
                    Toast.makeText(context, "Settings sent successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Error sending settings JSON: ${task.exception}")
                    Toast.makeText(context, "Failure sending settings", Toast.LENGTH_SHORT).show()
                }
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
            ContextCompat.checkSelfPermission(requireActivity(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), permissionsToRequest.toTypedArray(), 1)
        } else {
            bleHandler = BLEHandler(requireContext())
        }
    }

    // ---------------------------------------------------------------------------------------------

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
                Toast.makeText(requireContext(), "${deniedPermissions.joinToString(", ")}", Toast.LENGTH_LONG).show()
            } else {
                // Permissions are denied, show a message to the user
                bleHandler = BLEHandler(requireContext())
            }
        }
    }


    // ---------------------------------------------------------------------------------------------

    private fun saveSettings() {

        // loads the settings from the UI elements and saves them to file through the Settings handler
        
        val editor: SharedPreferences.Editor = WatchSensorSettingsHandler.sharedPreferences.edit()

        sensorsComponents.forEach { entry ->
            val sensorString = WatchSensorSettingsHandler.SensorToString[entry.key]

            val isChecked = (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean

            val progress = if (entry.key == SensorType.TYPE_GNSS) {
                0
            } else {
                (entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int
            }

            WatchSensorSettingsHandler.saveSetting(entry.key, Pair(isChecked, progress))

            Log.d(TAG, "Default settings for $sensorString changed to ($isChecked , $progress)")
        }
        editor.apply()
        Log.d(TAG, "Settings saved.")
        Toast.makeText(requireContext(), "Settings saved.", Toast.LENGTH_SHORT).show()

    }


    // ---------------------------------------------------------------------------------------------


    // Creates main_menu.xml
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.toolbar_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    // ---------------------------------------------------------------------------------------------

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.changeParameters -> {
                val setupIntent = Intent(requireContext(), SetupActivity::class.java)
                startActivity(setupIntent)
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------

    private fun setStateTextview(enabled: Boolean, textview: TextView) {
        if (enabled) {
            textview.text = "Enabled"
        } else {
            textview.text = "Disabled"
        }
    }

    // ---------------------------------------------------------------------------------------------
}