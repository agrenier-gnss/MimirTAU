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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mimir.sensors.SensorType
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Type


class WatchSettingPage: Fragment() {

    val IDX_SWITCH = 0
    val IDX_SEEKBAR = 1
    val IDX_TEXTVIEW = 2
    private val TAG = "connect: "
    private val sharedPrefName = "DefaultSettings_watch"
    private lateinit var sharedPreferences: SharedPreferences
    private val progressToFrequency = arrayOf(1, 5, 10, 50, 100, 200, 0)
    private var filePaths = mutableListOf<File>()
    private lateinit var sensorsComponents: MutableMap<String, MutableList<Any?>>
    private lateinit var bleHandler: BLEHandler
    private var fileSendOk: Boolean = true

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


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_settings_watch, container, false)
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val parentView = view.findViewById<ViewGroup>(R.id.square_layout)

        // Initialisation values
        sharedPreferences = requireActivity().getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("ECG")) {
            val editor: SharedPreferences.Editor = sharedPreferences.edit()
            editor.putString("GNSS", Gson().toJson(mutableListOf(true, 0)))
            editor.putString("IMU", Gson().toJson(mutableListOf(false, 2)))
            editor.putString("PSR", Gson().toJson(mutableListOf(false, 0)))
            editor.putString("STEPS", Gson().toJson(mutableListOf(false, 1)))
            editor.putString("ECG", Gson().toJson(mutableListOf(false, 4)))
            editor.putString("PPG", Gson().toJson(mutableListOf(false, 4)))
            editor.putString("GSR", Gson().toJson(mutableListOf(false, 4)))
            editor.apply()
        }
        checkAndRequestBluetoothPermissions()

        AppActivityHandler.getFilePaths().forEach { path ->
            filePaths.add(path)
        }
        // Load from shared preferences
        val sensorsInit = arrayOf("GNSS", "IMU", "PSR", "STEPS", "ECG", "PPG", "GSR")

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
                if ((it == "ECG" || it == "GSR") && isChecked) {
                    val opponent: String = if (it == "ECG") "GSR" else "ECG";
                    var GSREnable: Boolean = false;
                    sensorsComponents[opponent]?.forEach { component ->
                        when (component) {
                            is SwitchCompat -> {
                                GSREnable = component.isChecked
                                component.isChecked = false

                            }
                        }
                    }
                    if (GSREnable) {
                        ActivityHandler.setToggle(opponent)
                    }
                }

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
                slider.progress = (sensorParameters[1] as Double).toInt() //set slider value to slider
                slider.isEnabled = !ActivityHandler.isLogging() // Disable changing slider if logging is ongoing

                val sliderValue = row3.findViewById<TextView>(R.id.sliderValue)
                sliderValue.text =
                    "${progressToFrequency[(sensorParameters[1] as Double).toInt()]} Hz" //set slider value to a text view

                slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
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


        // Save current settings as default
        val btnDefault = view.findViewById<Button>(R.id.button_default)
        btnDefault.setOnClickListener {
            saveDefaultSettings()
        }


        //control button
        val btnControl = view.findViewById<Button>(R.id.button_control)
        btnControl.setOnClickListener {
            SendSettingToWatch()
        }

    }

    @SuppressLint("SimpleDateFormat")
    fun SendSettingToWatch() {
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


    private fun getWatchNodeId(callback: (ArrayList<String>) -> Unit) {
        val nodeIds = ArrayList<String>()
        Wearable.getNodeClient(requireActivity()).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Log.d(TAG, "connected node in getWatchId " + node.id)
                nodeIds.add(node.id)
            }
            callback(nodeIds)
        }
    }


    private fun generateSettingsJson(): String {
        val jsonData = JSONObject()
        sensorsComponents.forEach { entry ->
            val mkey: SensorType = SensorType.TYPE_GNSS
            when (entry.key) {

                "GNSS" -> jsonData.put("GNSS", JSONObject().apply {
                    put("switch", (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean)
                })

                else -> jsonData.put(entry.key, JSONObject().apply {
                    put("switch", (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean)
                    put("value", progressToFrequency[(entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int])
                })
            }/*
                                        // Added health sensor for LoggingService
                                        ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_ECG] = Pair(false, 0)
                                        ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_PPG] = Pair(false, 0)
                                        ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_GSR] = Pair(false, 0)*/
        }

        return jsonData.toString()
    }

    private fun sendSettingsJson(nodeId: String, context: Context) {
        val messageClient = Wearable.getMessageClient(context)
        val watchSettingsPath = "/watch_settings" // Message identifier tag
        val settingsJsonString = generateSettingsJson()

        messageClient.sendMessage(nodeId, watchSettingsPath, settingsJsonString.toByteArray())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("SendSettings", "Settings JSON sent successfully. Value: $settingsJsonString")
                    Toast.makeText(context, "Settings sent successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("SendSettings", "Error sending settings JSON: ${task.exception}")
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

    fun saveSettings() {
        sensorsComponents.forEach { entry ->
            var mkey: SensorType = SensorType.TYPE_GNSS
            when (entry.key) {
                "GNSS" -> mkey = SensorType.TYPE_GNSS
                "IMU" -> mkey = SensorType.TYPE_IMU
                "PSR" -> mkey = SensorType.TYPE_PRESSURE
                "STEPS" -> mkey = SensorType.TYPE_STEPS
                "ECG" -> mkey = SensorType.TYPE_SPECIFIC_ECG
                "PPG" -> mkey = SensorType.TYPE_SPECIFIC_PPG
                "GSR" -> mkey = SensorType.TYPE_SPECIFIC_GSR

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

            Log.d(
                "SettingsActivity",
                "Settings for ${entry.key} changed to ${ActivityHandler.sensorsSelected[mkey].toString()}."
            )
        }
        Log.d("SettingsActivity", "Settings saved.")
    }

    // ---------------------------------------------------------------------------------------------

    fun saveDefaultSettings() {
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        sensorsComponents.forEach { entry ->
            var mkey: SensorType = SensorType.TYPE_GNSS
            when (entry.key) {
                "GNSS" -> mkey = SensorType.TYPE_GNSS
                "IMU" -> mkey = SensorType.TYPE_IMU
                "PSR" -> mkey = SensorType.TYPE_PRESSURE
                "STEPS" -> mkey = SensorType.TYPE_STEPS
                "ECG" -> mkey = SensorType.TYPE_SPECIFIC_ECG
                "PPG" -> mkey = SensorType.TYPE_SPECIFIC_PPG
                "GSR" -> mkey = SensorType.TYPE_SPECIFIC_GSR
            }
            if (entry.key == "GNSS") {
                editor.putString(
                    entry.key, Gson().toJson(
                        mutableListOf(
                            (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean, 0
                        )
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
                "Default settings for ${entry.key} changed to ${ActivityHandler.sensorsSelected[mkey].toString()}."
            )
        }
        editor.apply()
        Log.d("SettingsActivity", "Default settings saved.")
        Toast.makeText(requireContext(), "Default settings saved.", Toast.LENGTH_SHORT).show()
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

    fun setStateTextview(enabled: Boolean, textview: TextView) {
        if (enabled) {
            textview.text = "Enabled"
        } else {
            textview.text = "Disabled"
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun loadMutableList(key: String): MutableList<String> {
        val jsonString = sharedPreferences.getString(key, "")
        val type: Type = object: TypeToken<MutableList<Any>>() {}.type
        return Gson().fromJson(jsonString, type) ?: mutableListOf()
    }
}