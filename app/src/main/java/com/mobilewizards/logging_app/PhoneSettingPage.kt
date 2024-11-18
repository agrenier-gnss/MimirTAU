package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.mimir.sensors.SensorType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type



class PhoneSettingPage : Fragment() {

    val IDX_SWITCH   = 0
    val IDX_SEEKBAR  = 1

    private val sharedPrefName = "DefaultSettings"
    private lateinit var sharedPreferences: SharedPreferences
    private val progressToFrequency = arrayOf(1, 5, 10, 50, 100, 200, 0)
    private var filePaths = mutableListOf<File>()
    private lateinit var sensorsComponents : MutableMap<String, MutableList<Any?>>


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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_settings_phone, container, false)
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        val parentView = view.findViewById<ViewGroup>(R.id.square_layout)

        // Initialisation values
        sharedPreferences = requireActivity().getSharedPreferences(sharedPrefName, Context.MODE_PRIVATE)
        if (!sharedPreferences.contains("GNSS")) {
            val editor: SharedPreferences.Editor = sharedPreferences.edit()
            editor.putString("GNSS",   Gson().toJson(mutableListOf(true, 0)))
            editor.putString("IMU",   Gson().toJson(mutableListOf(false, 2)))
            editor.putString("PSR",   Gson().toJson(mutableListOf(false, 0)))
            editor.putString("STEPS", Gson().toJson(mutableListOf(false, 1)))
            editor.apply()
        }


        AppActivityHandler.getFilePaths().forEach{ path ->
            filePaths.add(path)
        }
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
        val btnSave = view.findViewById<Button>(R.id.button_save)
        btnSave.setOnClickListener {
            saveSettings()
            listener.onSaveSettings() // Close activity
        }

        // Save current settings as default
        val btnDefault = view.findViewById<Button>(R.id.button_default)
        btnDefault.setOnClickListener {
            saveDefaultSettings()
        }


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