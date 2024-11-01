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
import android.app.AlertDialog


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.security.MessageDigest


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
            val intent = Intent(this, SelectionActivity::class.java)
            startActivity(intent)
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

                fileSendConfirmationPopup(fileItem)

            }
        }

    }

    // =============================================================================================
    private fun fileSendConfirmationPopup(file: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.file_send_dialog_confirmation, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)


        builder.setTitle("Send File")
        builder.setMessage("Are you sure you want to send file the following file to phone: ${file.nameWithoutExtension}?")
        builder.setIcon(R.drawable.upload) // Use your drawable resource for the send icon

        val dialog = builder.create()

        // confirm button
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            "Are you sure you want to send the file to phone:\n${file.nameWithoutExtension}?"

        // cancel button
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            // cancelled
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btn_confirm).setOnClickListener {
            // file transfer confirmed
            sendFiles(file)
            dialog.dismiss()
        }

        val alert = builder.create()
        alert.show()
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

        // get files sorted from the most recent to the oldest
        return txtFilesList.sortedByDescending { it.lastModified() }
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

        val dateTime = SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(System.currentTimeMillis())

        val fileName = "log_watch_$dateTime.csv"

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
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

                writeLine("$COMMENT_START $fileName")
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
        getPhoneNodeId { nodeId ->
            Log.d(TAG, "Received nodeId: $nodeId")
            // Check if there are connected nodes
            if (nodeId.isNullOrEmpty()) {
                Log.d(TAG, "no nodes found")
                Toast.makeText(this, "Phone not connected", Toast.LENGTH_SHORT).show()
                return@getPhoneNodeId

            }

            // successful connection
            Log.d(TAG, "nodes found, sending file: $file")

            val csvPath = generateCsvFile(file)
            sendCsvFileToPhone(File(csvPath), nodeId, this)

        }
    }

    // =============================================================================================

    private fun getPhoneNodeId(callback: (String?) -> Unit) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            callback(nodes.firstOrNull()?.id)
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

        // Generate the checksum for the file
        val fileData = csvFile.readBytes()
        val checksum = generateChecksum(fileData)
        Log.d(TAG, "File checksum: $checksum")

        // Getting channelClient for sending the file
        val channelClient = Wearable.getChannelClient(context)
        val callback = object: ChannelClient.ChannelCallback() {
            override fun onChannelOpened(channel: ChannelClient.Channel) {
                Log.d(TAG, "onChannelOpened " + channel.nodeId)
                // Send the CSV file to the phone and check if send was successful
                channelClient.sendFile(channel, csvFile.toUri()).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        WatchActivityHandler.fileSendStatus(true)
                        sendChecksumToPhone(checksum, nodeId, context)
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

    // =============================================================================================

    // generate a SHA-256 checksum for data corruption check between smartphone and watch file
    // transfer
    private fun generateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun sendChecksumToPhone(checksum: String, nodeId: String, context: Context) {
        val messageClient = Wearable.getMessageClient(context)
        val checksumPath = "$CSV_FILE_CHANNEL_PATH/checksum"

        messageClient.sendMessage(nodeId, checksumPath, checksum.toByteArray()).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Checksum sent successfully")
            } else {
                Log.e(TAG, "Error sending checksum: ${task.exception}")
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
