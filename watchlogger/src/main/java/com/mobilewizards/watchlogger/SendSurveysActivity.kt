package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.net.toUri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.mobilewizards.logging_app.databinding.ActivitySendSurveysBinding
import com.mobilewizards.watchlogger.WatchActivityHandler
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import android.widget.ImageButton


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


private const val COMMENT_START = "#"


class SendSurveysActivity: Activity() {

    private lateinit var binding: ActivitySendSurveysBinding
    private val TAG = "watchLogger"
    private val CSV_FILE_CHANNEL_PATH = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    private var filePaths = mutableListOf<File>()
    private var fileSendOk: Boolean = false

    // =============================================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_send_surveys)

        binding = ActivitySendSurveysBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toPhoneBtn = findViewById<Button>(R.id.SendToPhoneBtn)
        val surveyToMenuBtn = findViewById<Button>(R.id.surveyToMenuBtn)

        Log.d(TAG, "toPhoneBtn: $toPhoneBtn, surveyToMenuBtn: $surveyToMenuBtn")

        WatchActivityHandler.getFilePaths().forEach { path ->
            filePaths.add(path)
        }

        toPhoneBtn.setOnClickListener {
            // back to the menu screen
            //sendFiles()
        }

        surveyToMenuBtn.setOnClickListener {
            // back to the menu screen
            finish()
        }


        // possibly add a "toDriveBtn" for sending survey to drive in the future
        //toDriveBtn.setOnClickListener {
        //    //
        //}


        // ================================== NEW ===============================================


        val filesRecyclerView = findViewById<RecyclerView>(R.id.filesRecyclerView)

        val fileList = getSurveyFiles()

        // Button functionality for the buttons in RecyclerView that send or delete files
        filesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SendSurveysActivity)
            adapter = FilesAdapter(fileList) { fileItem ->

                //Toast.makeText(
                //    this@SendSurveysActivity, "Clicked: ${fileItem.nameWithoutExtension}", Toast.LENGTH_SHORT
                //).show()

                sendFiles(fileItem)

            }
        }

    }

    // =============================================================================================

    private fun getSurveyFiles(): List<File> {
        // app file directory
        Log.d(TAG, filesDir.absolutePath)
        val appFilesDir = File(filesDir.absolutePath)

        // All the files in the app file directory with all the non txt files filtered out
        val filesList = appFilesDir.listFiles()?.toList() ?: emptyList()

        val txtFilesList = filesList.filter { file ->
            file.extension == "txt"
        }

        return txtFilesList
    }


    // =============================================================================================

    private fun fileSendSuccessful() {
        fileSendOk = true
        WatchActivityHandler.fileSendStatus(fileSendOk)
        val openSendInfo = Intent(applicationContext, FileSendActivity::class.java)
        startActivity(openSendInfo)
    }

    // =============================================================================================

    private fun fileSendTerminated() {
        fileSendOk = false
        WatchActivityHandler.fileSendStatus(fileSendOk)
        val openSendInfo = Intent(applicationContext, FileSendActivity::class.java)
        startActivity(openSendInfo)
    }

    // =============================================================================================

    @SuppressLint("SimpleDateFormat")
    private fun generateCsvFile(csvFile: File): String {
        // generates the csv file and saves it into the watches' downloaded files
        val contentValues = ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                "log_watch_${SimpleDateFormat("ddMMyyyy_hhmmssSSS").format(startTime)}.csv"
            )
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = this.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        var filePath = ""
        uri?.let { mediaUri ->
            this.contentResolver.openOutputStream(mediaUri)?.use { outputStream ->
                // helper function for output stream
                fun writeLine(line: String) {
                    outputStream.write("$line\n".toByteArray())
                }

                val dateTime: String = SimpleDateFormat("ddMMyyyy_hhmmssSSS").format(startTime)
                writeLine("$COMMENT_START log_watch_$dateTime.csv")
                writeLine(COMMENT_START)
                writeLine("$COMMENT_START Header Description:")
                writeLine(COMMENT_START)
                writeLine("$COMMENT_START Version: ${BuildConfig.VERSION_CODE} Platform: ${Build.VERSION.RELEASE} Manufacturer: ${Build.MANUFACTURER} Model: ${Build.MODEL}")
                writeLine(COMMENT_START)


                // Read each of the files from csvFile and send them to the output stream
                val reader = BufferedReader(FileReader(csvFile))

                writeLine("")
                writeLine("$COMMENT_START ${csvFile.name}")

                var line: String? = reader.readLine()
                while (line != null) {
                    writeLine(line)
                    line = reader.readLine()
                }
                reader.close()

                outputStream.flush()

                val cursor = contentResolver.query(mediaUri, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val columnIndex = c.getColumnIndex(MediaStore.Images.Media.DATA)
                        if (columnIndex >= 0) {
                            filePath = c.getString(columnIndex)
                            // Use the file path as needed
                            Log.d("File path", filePath)
                        } else {
                            Log.e("Error", "Column MediaStore.Images.Media.DATA not found.")
                        }
                    }
                }

            }
        }
        return filePath
    }


    // =============================================================================================


    private fun sendFiles(file: File) {
        getPhoneNodeId { nodeIds ->
            Log.d(TAG, "Received nodeIds: $nodeIds")
            // Check if there are connected nodes
            val connectedNode: String = if (nodeIds.size > 0) nodeIds[0] else ""

            if (connectedNode.isEmpty()) {
                Log.d(TAG, "no nodes found")
                Toast.makeText(this, "Phone not connected", Toast.LENGTH_SHORT).show()

            } else {
                Log.d(TAG, "nodes found, sending")

                Log.d(TAG, "sending file: $file")
                val csvPath = generateCsvFile(file)

                sendCsvFileToPhone(File(csvPath), connectedNode, this)
            }
        }
    }

    // =============================================================================================
    private fun getPhoneNodeId(callback: (ArrayList<String>) -> Unit) {
        val nodeIds = ArrayList<String>()
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Log.d(TAG, "connected node in getPhoneId " + node.id)
                nodeIds.add(node.id)
            }
            callback(nodeIds)
        }
    }

    // =============================================================================================

    private fun sendCsvFileToPhone(csvFile: File, nodeId: String, context: Context) {
        Log.d(TAG, "in sendCsvFileToPhone ${csvFile.name}")
        // Checks if the file is found and read
        try {
            val bufferedReader = BufferedReader(FileReader(csvFile))
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                line = bufferedReader.readLine()
            }
            bufferedReader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Getting channelClient for sending the file
        val channelClient = Wearable.getChannelClient(context)
        val callback = object: ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                Log.d(TAG, "onChannelOpened " + channel.nodeId)
                // Send the CSV file to the phone and check if send was successful
                channelClient.sendFile(channel, csvFile.toUri()).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        WatchActivityHandler.fileSendStatus(true)
                        fileSendSuccessful()
                        channelClient.close(channel)
                    } else {
                        Log.e(TAG, "Error with file sending " + task.exception.toString())
                        WatchActivityHandler.fileSendStatus(false)
                        fileSendTerminated()
                        channelClient.close(channel)
                    }
                }
            }

            override fun onChannelClosed(
                channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int
            ) {
                Log.d(
                    TAG, "Channel closed: nodeId=$nodeId, reason=$closeReason, errorCode=$appSpecificErrorCode"
                )
                Wearable.getChannelClient(applicationContext).close(channel)
            }
        }

        channelClient.registerChannelCallback(callback)
        channelClient.openChannel(
            nodeId, CSV_FILE_CHANNEL_PATH.toString()
        ).addOnCompleteListener { result ->
            Log.d(TAG, result.toString())
            if (result.isSuccessful) {
                Log.d(TAG, "Channel opened: nodeId=$nodeId, path=$CSV_FILE_CHANNEL_PATH")
                callback.onChannelOpened(result.result)
            } else {
                Log.e(
                    TAG, "Failed to open channel: nodeId=$nodeId, path=$CSV_FILE_CHANNEL_PATH"
                )
                channelClient.unregisterChannelCallback(callback)
            }
        }
    }
}


// Separate class for RecyclerView items in RecyclerView activity_send_surveys.xml
class FilesAdapter(
    private val filesList: List<File>, private val onButtonClick: (File) -> Unit
): RecyclerView.Adapter<FilesAdapter.FileViewHolder>() {


    inner class FileViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
        val fileCreationTimeTextView: TextView = view.findViewById(R.id.fileCreationTimeTextView)
        val circularButton: ImageButton = view.findViewById(R.id.circularButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = filesList[position]
        holder.fileNameTextView.text = file.nameWithoutExtension
        holder.fileCreationTimeTextView.text = getFileCreationTime(file)

        holder.circularButton.setOnClickListener {
            onButtonClick(file)
        }
    }

    // file file creation time for displaying
    private fun getFileCreationTime(file: File): String? {
        return try {
            val path = file.toPath()
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)

            val creationTime = attributes.creationTime()

            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
            dateFormat.format(creationTime.toMillis())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getItemCount(): Int = filesList.size
}
