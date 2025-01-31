package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import java.io.File
import kotlin.math.abs

class SurveyHistoryActivity: AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var surveyAdapter: SurveyAdapter

    private lateinit var gestureDetector: GestureDetector
    private var isSwiping = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surveyhistory)
        supportActionBar?.hide()

        // gesture detector for detecting swipes
        gestureDetector = GestureDetector(this, SwipeGestureListener())

        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadSurveys()

        findViewById<FrameLayout>(R.id.button_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // listen to the gestures from the screen and the recycler view
        recyclerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            if (isSwiping) true else recyclerView.onTouchEvent(event)
        }

    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private inner class SwipeGestureListener: GestureDetector.SimpleOnGestureListener() {

        // when swiping right, we want to return to main activity
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (abs(diffX) > abs(diffY)) {
                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    isSwiping = true
                    if (diffX > 0) { // Right swipe
                        onBackPressedDispatcher.onBackPressed()
                    }
                    return true
                }
            }
            isSwiping = false
            return false

        }

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }
    }

    private fun loadSurveys() {
        // load survey files from storage
        val path = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
        val folder = File(path.toString())
        val surveyFiles = folder.listFiles()?.sortedByDescending { it.lastModified() } // newest first
            ?: emptyList()

        surveyAdapter = SurveyAdapter(surveyFiles, ::isSwiping, gestureDetector) { file ->
            deleteSurvey(file)
        }

        recyclerView.adapter = surveyAdapter

    }

    private fun deleteSurvey(file: File) {
        // delete the file and reload surveys
        try {
            file.delete()
            loadSurveys()
        } catch (e: Exception) {
            Log.e("SurveyHistoryActivity", "Error deleting file: ${e.message}")
            val snackbar = Snackbar.make(
                findViewById(android.R.id.content), "Error in deletion of file", Snackbar.LENGTH_LONG
            )
            snackbar.setAction("Close") { snackbar.dismiss() }
            snackbar.view.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
            snackbar.show()
        }
    }
}


// Adapter class for the items in the survey history that are contained in the RecyclerView
// and the actions that are taken with them
class SurveyAdapter(
    private val surveys: List<File>,
    private val isSwiping: () -> Boolean,
    private val gestureDetector: GestureDetector,
    private val onDelete: (File) -> Unit,

    ): RecyclerView.Adapter<SurveyAdapter.SurveyViewHolder>() {

    inner class SurveyViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val surveyTitle: TextView = itemView.findViewById(R.id.surveyTitle)
        val fileSize: TextView = itemView.findViewById(R.id.fileSize)
        val fileLocation: TextView = itemView.findViewById(R.id.surveyLocation)
        val declineButton: AppCompatImageButton = itemView.findViewById(R.id.decline_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SurveyViewHolder {
        val rootView = LayoutInflater.from(parent.context).inflate(R.layout.layout_presets, parent, false)

        val surveyView = rootView.findViewById<View>(R.id.surveySquarePreset)

        (surveyView.parent as? ViewGroup)?.removeView(surveyView)
        return SurveyViewHolder(surveyView)
    }


    override fun onBindViewHolder(holder: SurveyViewHolder, position: Int) {
        val surveyFile = surveys[position]

        holder.surveyTitle.text = surveyFile.name
        holder.fileSize.text = formatFileSize(surveyFile.length())
        holder.fileLocation.text = surveyFile.canonicalPath


        holder.declineButton.setOnClickListener {
            onDelete(surveyFile)
        }

        // item click action
        holder.itemView.setOnClickListener {
            // only open the file if it's not a swipe
            if (!isSwiping()) {
                openSurveyFile(surveyFile, holder.itemView.context)
            }
        }

        holder.itemView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }

    }

    override fun getItemCount(): Int = surveys.size

    private fun formatFileSize(fileSizeInBytes: Long): String {
        // formatted file size from bytes
        val kb = 1024
        val mb = kb * 1024
        val gb = mb * 1024

        return when {

            fileSizeInBytes >= gb -> String.format("%.2f GB", fileSizeInBytes.toDouble() / gb)
            fileSizeInBytes >= mb -> String.format("%.2f MB", fileSizeInBytes.toDouble() / mb)
            fileSizeInBytes >= kb -> String.format("%.2f KB", fileSizeInBytes.toDouble() / kb)
            else -> String.format("%d bytes", fileSizeInBytes)
        }
    }

    private fun openSurveyFile(file: File, context: Context) {
        // try to open the survey when clicked
        try {
            val builder = StrictMode.VmPolicy.Builder()
            StrictMode.setVmPolicy(builder.build())

            val intent = Intent(Intent.ACTION_VIEW)
            val uri = Uri.fromFile(file)
            intent.setDataAndType(uri, "*/*")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening file: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }
}

