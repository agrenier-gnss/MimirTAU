package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TableLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.io.File

class SurveyHistoryActivity : AppCompatActivity() {
    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surveyhistory)
        supportActionBar?.hide()

        // Get the parent view to add the TableLayout to
        val parentView = findViewById<ViewGroup>(R.id.container_layout)

        //create a layout for each survey that has been made
        populateView(parentView)

        // Switch views by swiping
        var x1 = 0f
        var y1 = 0f
        var x2 = 0f
        var y2 = 0f
        findViewById<View>(R.id.scroll_id).setOnTouchListener { _, touchEvent ->
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
                            if (deltaX > 0) {
                                // left swipe
                                val intent = Intent(this, MainActivity::class.java)
                                startActivity(intent)
                                true
                            }
                        }
                    }
                    false
                }
                else -> false
            }
        }
        val goBackButton = findViewById<FrameLayout>(R.id.button_back)
        goBackButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed();}
    }

    @SuppressLint("SetTextI18n", "QueryPermissionsNeeded")
    fun populateView(parentView: ViewGroup) {
        parentView.removeAllViews()

        val path = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
        val folder = File(path.toString())

        folder.listFiles()?.forEach { file ->
            // Inflate the layout file that contains the TableLayout
            val tableLayout = layoutInflater.inflate(R.layout.layout_presets, parentView, false).findViewById<TableLayout>(R.id.surveySquarePreset)

            // Remove the tableLayout's parent, if it has one
            (tableLayout.parent as? ViewGroup)?.removeView(tableLayout)

            // Set file info into view
            val surveyTitle = tableLayout.findViewById<TextView>(R.id.surveyTitle)
            surveyTitle.text = file.name
            val fileSize = tableLayout.findViewById<TextView>(R.id.fileSize)
            fileSize.text = "${file.length()} bytes"
            val fileLocation = tableLayout.findViewById<TextView>(R.id.surveyLocation)
            fileLocation.text = file.canonicalPath.toString()

            tableLayout.setOnClickListener {


                try {
                    val builder = StrictMode.VmPolicy.Builder()
                    StrictMode.setVmPolicy(builder.build())

                    val intent = Intent(Intent.ACTION_VIEW)
                    val uri = Uri.parse("content://" + file.canonicalPath.toString() )
                    intent.setDataAndType(uri, "*/*")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } catch (e: Exception) {
                    val inflater = LayoutInflater.from(this)
                    val dialogView = inflater.inflate(R.layout.custom_dialog, null)

                    val builder = AlertDialog.Builder(this)
                    builder.setView(dialogView as View)

                    val dialog = builder.create()
                    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    val title = dialogView.findViewById<TextView>(R.id.dialog_title)
                    val message = dialogView.findViewById<TextView>(R.id.dialog_message)
                    val okBtn = dialogView.findViewById<Button>(R.id.positiveButton)
                    val cancelBtn = dialogView.findViewById<Button>(R.id.negativeButton)
                    okBtn.setVisibility(View.GONE)
                    cancelBtn.setOnClickListener {
                        dialog.dismiss()
                    }
                    title.text = "File Corruption"
                    message.text = "Error in opening of file: ${file.name}"
                    dialog.show()
                }
            }

            // Add the TableLayout to the parent view
            parentView.addView(tableLayout)

            var declineButton: AppCompatImageButton = tableLayout.findViewById(R.id.decline_button)
            declineButton.setOnClickListener {
                try {
                    file.delete()
                    // Refresh the view after deleting the file
                    populateView(parentView)
                } catch (e: Exception) {
                    Log.e("Error in deletion of file", e.toString())
                    val view = findViewById<View>(android.R.id.content)
                    val snackbar = Snackbar.make(view, "Error in deletion of file", Snackbar.LENGTH_LONG)
                    snackbar.setAction("Close") {
                        snackbar.dismiss()
                    }
                    snackbar.view.setBackgroundColor(ContextCompat.getColor(view.context, R.color.red))
                    snackbar.show()
                }
            }
        }
    }
}