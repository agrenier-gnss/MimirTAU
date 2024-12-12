package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.widget.Switch
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mimir.sensors.SensorType
import java.lang.reflect.Type

const val infinitySymbol = "\u221E"
const val IDX_SWITCH = 0
const val IDX_SEEKBAR = 1
const val IDX_TEXTVIEW = 2

class PhoneSettingPage: Fragment() {

    private lateinit var sensorsComponents: MutableMap<SensorType, MutableList<Any?>>

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
        return inflater.inflate(R.layout.activity_settings_phone, container, false)
    }

    override fun onViewCreated(
        view: View, savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        PhoneSensorSettingsHandler.initializePreferences(requireContext())

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



        initializeSensorComponents()

        loadSharedPreferencesToUi()

        saveSettings() // Save default settings
    }

    // ---------------------------------------------------------------------------------------------

    private fun initializeSensorComponents() {
        //create a layout for each sensor in sensorList
        sensorsComponents = mutableMapOf()

        val parentView = requireView().findViewById<ViewGroup>(R.id.square_layout)

        PhoneSensorSettingsHandler.sensors.forEach {

            val sensorString = PhoneSensorSettingsHandler.SensorToString[it]!!
            val sensorParameters = PhoneSensorSettingsHandler.getSetting(sensorString, mutableListOf(false, 0))

            // Inflate the layout file that contains the TableLayout
            val tableLayout = layoutInflater.inflate(R.layout.layout_presets, parentView, false)
                .findViewById<TableLayout>(R.id.sensorSquarePreset)

            val row = tableLayout.getChildAt(0) as TableRow
            val sensorTitleTextView = row.findViewById<TextView>(R.id.sensorTitle)
            sensorTitleTextView.text = sensorString

            var sensorSwitch = row.findViewById<SwitchCompat>(R.id.sensorSwitch)
            sensorSwitch.isChecked = sensorParameters[0] as Boolean
            sensorSwitch.isEnabled = !ActivityHandler.isLogging() // Disable toggling sensor if logging is ongoing

            var sensorStateTextView = row.findViewById<TextView>(R.id.sensorState)
            setStateTextview(sensorSwitch.isChecked, sensorStateTextView)

            val row2 = tableLayout.getChildAt(1) as TableRow
            val description = row2.findViewById<TextView>(R.id.description)

            sensorSwitch.setOnCheckedChangeListener(createSwitchListener(sensorStateTextView, sensorString))

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

                slider.max = PhoneSensorSettingsHandler.progressToFrequency.size - 1
                slider.progress = (sensorParameters[1] as Double).toInt() //set slider value to slider
                slider.isEnabled = !ActivityHandler.isLogging() // Disable changing slider if logging is ongoing

                val sensorProgressIndex = (sensorParameters[1] as Double).toInt()

                val sliderValue = row3.findViewById<TextView>(R.id.sliderValue)

                updateTextView(sliderValue, sensorProgressIndex)

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

        Log.d("SettingsActivity", "UI updated from shared preferences.")

        // Load Initialisation values from sharedPreferences to the sensor types
        val sensorsInit = PhoneSensorSettingsHandler.loadSensorValues()

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

    private fun createSwitchListener(
        sensorStateTextView: TextView, sensorString: String
    ): CompoundButton.OnCheckedChangeListener {
        return CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            setStateTextview(isChecked, sensorStateTextView) // Update the state text view
            ActivityHandler.setToggle(sensorString) // Toggle the status in singleton

            // enable / disable the bar based on the switch being checked or not
            val seekBar =
                sensorsComponents.entries.find { it.value[IDX_SWITCH] == buttonView }?.value?.get(1) as? SeekBar
            seekBar?.isEnabled = isChecked
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun updateTextView(textView: TextView, progressIndex: Int) {
        val progressHz = PhoneSensorSettingsHandler.progressToFrequency[progressIndex]
        textView.text = if (progressHz == 0) infinitySymbol else "$progressHz Hz"
    }

    // ---------------------------------------------------------------------------------------------


    fun saveSettings() {
        sensorsComponents.forEach { entry ->

            val sensorString = PhoneSensorSettingsHandler.SensorToString[entry.key]
            if (entry.key == SensorType.TYPE_GNSS) {
                ActivityHandler.sensorsSelected[entry.key] = Pair(
                    (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean, 1
                )
            } else {
                ActivityHandler.sensorsSelected[entry.key] = Pair(
                    (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean,
                    PhoneSensorSettingsHandler.progressToFrequency[(entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int]
                )
            }
            // Added health sensor for LoggingService
            ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_ECG] = Pair(false, 0)
            ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_PPG] = Pair(false, 0)
            ActivityHandler.sensorsSelected[SensorType.TYPE_SPECIFIC_GSR] = Pair(false, 0)
            Log.d(
                "SettingsActivity",
                "Settings for $sensorString changed to ${ActivityHandler.sensorsSelected[entry.key].toString()}."
            )
        }
        Log.d("SettingsActivity", "Settings saved.")
    }

    // ---------------------------------------------------------------------------------------------

    fun saveDefaultSettings() {
        val editor: SharedPreferences.Editor = PhoneSensorSettingsHandler.sharedPreferences.edit()

        sensorsComponents.forEach { entry ->
            val sensorString = PhoneSensorSettingsHandler.SensorToString[entry.key]

            val isChecked = (entry.value[IDX_SWITCH] as? SwitchCompat)?.isChecked as Boolean

            val progress = if (entry.key == SensorType.TYPE_GNSS) {
                0
            } else {
                (entry.value[IDX_SEEKBAR] as? SeekBar)?.progress as Int
            }

            PhoneSensorSettingsHandler.saveSetting(entry.key, Pair(isChecked, progress))

            Log.d("SettingsActivity", "Default settings for $sensorString changed to ($isChecked , $progress)")
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

}