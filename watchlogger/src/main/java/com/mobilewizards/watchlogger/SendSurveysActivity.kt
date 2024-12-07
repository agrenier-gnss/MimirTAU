package com.mobilewizards.logging_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.net.toUri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.mobilewizards.logging_app.databinding.ActivitySendSurveysBinding
import com.mobilewizards.watchlogger.WatchActivityHandler
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import android.widget.ImageButton
import android.app.AlertDialog


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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

        val surveyToMenuBtn = findViewById<Button>(R.id.surveyToMenuBtn)

        WatchActivityHandler.getFilePaths().forEach { path ->
            filePaths.add(path)
        }

        surveyToMenuBtn.setOnClickListener {
            // back to the menu screen
            val intent = Intent(this, SelectionActivity::class.java)
            startActivity(intent)
        }


        // File list items
        val fileList = getSurveyFiles()
        val filesRecyclerView = findViewById<RecyclerView>(R.id.filesRecyclerView)
        val filesAdapter = FilesAdapter(fileList, TAG)

        // File send to phone button
        filesAdapter.onFileSendClick = { fileItem ->

            getPhoneNodeId { nodeId ->
                // Check if there are connected nodes
                if (nodeId.isNullOrEmpty()) {
                    // No connection
                    Toast.makeText(this, "Phone not connected", Toast.LENGTH_SHORT).show()
                } else {
                    // there is a phone connection
                    fileSendConfirmationPopup(fileItem)
                }
            }

        }

        // File delete from the watch button
        filesAdapter.onFileDeleteClick = { fileItem ->
            fileDeleteConfirmationPopup(fileItem) { isDeleted ->
                // if the file is deleted from the storage, we delete it off the UI list too
                if (isDeleted) {
                    filesAdapter.deleteFile(fileItem)
                }

            }
        }


        filesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SendSurveysActivity)
            adapter = filesAdapter
        }
    }

    // =============================================================================================
    private fun fileSendConfirmationPopup(file: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.file_action_dialog_confirmation, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)

        val dialog = builder.create()

        // dialog text
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            "Are you sure you want to send the file to phone:\n${file.nameWithoutExtension}?"

        // Setting the confirm button to show file sending icon
        val confirmSendButton = dialogView.findViewById<ImageButton>(R.id.btn_confirm)
        confirmSendButton.apply {
            setBackgroundResource(R.drawable.blue_circular_button_background)
            setImageResource(R.drawable.upload)
        }

        // confirm button
        dialogView.findViewById<ImageButton>(R.id.btn_confirm).setOnClickListener {
            // file transfer confirmed
            sendFiles(file)
            dialog.dismiss()
        }

        // cancel button
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            // cancelled
            dialog.dismiss()
        }

        dialog.show()
    }

    // =============================================================================================


    private fun fileDeleteConfirmationPopup(file: File, onDeletionResult: (Boolean) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.file_action_dialog_confirmation, null)
        val builder = AlertDialog.Builder(this).setView(dialogView)

        val dialog = builder.create()


        // dialog text
        dialogView.findViewById<TextView>(R.id.dialog_message).text =
            "Are you sure you want to delete the file from the watch:\n${file.nameWithoutExtension}?"

        // Setting the confirm button to show file deletion trashcan icon
        val confirmDeletionButton = dialogView.findViewById<ImageButton>(R.id.btn_confirm)
        confirmDeletionButton.apply {
            setBackgroundResource(R.drawable.red_circular_button_background)
            setImageResource(R.drawable.trashcan)
        }


        // confirm button
        dialogView.findViewById<ImageButton>(R.id.btn_confirm).setOnClickListener {
            // file deletion confirmed
            val fileDeleted = deleteFiles(file)

            onDeletionResult(fileDeleted)
            dialog.dismiss()
        }

        // cancel button
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            // cancelled
            onDeletionResult(false)
            dialog.dismiss()

        }

        dialog.show()
    }


    // =============================================================================================

    private fun getSurveyFiles(): MutableList<File> {
        // app file directory
        Log.d(TAG, filesDir.absolutePath)
        val appFilesDir = File(filesDir.absolutePath)

        // All the files in the app file directory with all the non txt files filtered out
        val filesList = appFilesDir.listFiles()?.toList() ?: emptyList()

        val txtFilesList = filesList.filter { file ->
            file.extension == "txt"
        }

        // get files sorted from the most recent to the oldest
        return txtFilesList.sortedByDescending { it.lastModified() }.toMutableList()
    }


    // =============================================================================================


    private fun generateCsvFile(file: File): File {
        // generates a CSV file in a temporary directory and returns the File object

        val originalName = file.nameWithoutExtension
        val newFileName = "${originalName}_sw.csv"
        val tempDir = applicationContext.cacheDir // app's cache directory
        val tempFile = File(tempDir, newFileName)

        tempFile.outputStream().buffered().use { outputStream ->
            // Helper function for writing lines to the file
            fun writeLine(line: String) {
                outputStream.write("$line\n".toByteArray())
            }

            writeLine("$COMMENT_START $originalName")
            writeLine(COMMENT_START)
            writeLine("$COMMENT_START Header Description:")
            writeLine(COMMENT_START)
            writeLine("$COMMENT_START Version: ${BuildConfig.VERSION_CODE} Platform: ${Build.VERSION.RELEASE} Manufacturer: ${Build.MANUFACTURER} Model: ${Build.MODEL}")
            writeLine(COMMENT_START)
            writeLine("")

            // Read the input file in chunks and write to the output file
            file.inputStream().buffered().use { inputStream ->
                val buffer = ByteArray(8192) // 8 KB buffer size
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }

            outputStream.flush()
        }

        Log.d("File path", "temporary file created in ${tempFile.absolutePath}")
        return tempFile
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

            val tempFile = generateCsvFile(file)
            sendCsvFileToPhone(tempFile, nodeId, this)

        }
    }

    // =============================================================================================

    private fun deleteFiles(file: File): Boolean {

        val fileDeleted = file.delete() // Returns true if deletion was successful

        if (fileDeleted) {
            // Remove file from file adapter list
            Toast.makeText(
                this@SendSurveysActivity, "File deleted successfully!", Toast.LENGTH_SHORT
            ).show()
            Log.d(TAG, "deleteFiles: file $file successfully deleted from storage")
            return true

        } else {
            Toast.makeText(
                this@SendSurveysActivity, "ERROR: Failed to delete the file", Toast.LENGTH_SHORT
            ).show()
            Log.w(TAG, "ERROR: deleteFiles: file $file couldn't be deleted from storage")
            return false
        }
    }

    // =============================================================================================

    private fun getPhoneNodeId(callback: (String?) -> Unit) {
        val nodeClient = Wearable.getNodeClient(applicationContext)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            // Filter for nearby nodes only
            val nearbyNode = nodes.firstOrNull { it.isNearby }

            // Pass the ID of the first nearby node (or null if none are nearby)
            callback(nearbyNode?.id)

            if (nearbyNode == null) {
                Log.e("pairing", "No nearby paired device found.")
            } else {
                Log.d("pairing", "Nearby device is paired and connected.")
            }
        }.addOnFailureListener { exception ->
            Log.e("pairing", "Failed to get connected nodes: ${exception.message}")
            callback(null)
        }
    }


    // =============================================================================================

    private fun sendCsvFileToPhone(tempFile: File, nodeId: String, context: Context) {
        Log.d(TAG, "in sendCsvFileToPhone ${tempFile.name}")
        // Checks if the file is found and read
//        try {
//            val bufferedReader = BufferedReader(FileReader(csvFile))
//            var line: String? = bufferedReader.readLine()
//            while (line != null) {
//                line = bufferedReader.readLine()
//            }
//            bufferedReader.close()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }

        // Generate the checksum for the file
        val checksum = generateChecksum(tempFile.inputStream())
        Log.d(TAG, "File checksum: $checksum")

        // Create a temporary file to hold chunks (use temporary directory)
        val cacheFile = File(context.cacheDir, "temp_chunked_file.csv")

        try {
            // Stream file into temporary chunked file
            FileOutputStream(cacheFile).use { outputStream ->
                val buffer = ByteArray(262144) // 256 KB buffer
                tempFile.inputStream().use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            val channelClient = Wearable.getChannelClient(context)
            val callback = object: ChannelClient.ChannelCallback() {
                override fun onChannelOpened(channel: ChannelClient.Channel) {
                    Log.d(TAG, "onChannelOpened ${channel.nodeId}")
                    val fileByteSize = tempFile.length().toString()

                    // sending file size before starting to send file
                    sendFileSizeToPhone(fileByteSize, nodeId, context)

                    channelClient.sendFile(channel, cacheFile.toUri()).addOnCompleteListener { task ->
                        if (task.isSuccessful) {

                            // will display that file was sent successfully even if the checksum or file name send fail
                            // but pausing the watchlogger for potentially a minute to see if everything was sent well
                            // isn't really an option
                            sendFileNameToPhone(tempFile.name, nodeId, context)
                            sendChecksumToPhone(checksum, nodeId, context)
                            fileSendOk = true

                        } else {
                            Log.e(TAG, "Error with file sending " + task.exception.toString())

                            fileSendOk = false
                        }
                        WatchActivityHandler.fileSendStatus(fileSendOk)
                        val openSendInfo = Intent(applicationContext, FileSendActivity::class.java)
                        startActivity(openSendInfo)
                        finish()
                        channelClient.close(channel)
                    }
                }

                override fun onChannelClosed(
                    channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int
                ) {
                    Log.d(
                        TAG, "Channel closed: nodeId=$nodeId, reason=$closeReason, errorCode=$appSpecificErrorCode"
                    )
                    Wearable.getChannelClient(applicationContext).close(channel)

                    val cacheDeleted = cacheFile.delete()

                    if (cacheDeleted) {
                        Log.d(TAG, "Cache file deleted: ${tempFile.name}")
                    } else {
                        Log.w(TAG, "Failed to delete cache file: ${tempFile.name}")
                    }

                    val tempDeleted = tempFile.delete()

                    if (tempDeleted) {
                        Log.d(TAG, "Temporary file deleted: ${tempFile.name}")
                    } else {
                        Log.w(TAG, "Failed to delete temporary file: ${tempFile.name}")
                    }
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
                    cacheFile.delete()
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error while processing file: ${e.message}")
        }
    }

    // =============================================================================================

    // generate a SHA-256 checksum for data corruption check between smartphone and watch file
    // transfer
    fun generateChecksum(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192) // Adjust buffer size as needed
        var bytesRead: Int

        try {
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } catch (e: IOException) {
            // Handle exception
        } finally {
            inputStream.close()
        }

        val hashBytes = digest.digest()
        return hashBytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun sendChecksumToPhone(checksum: String, nodeId: String, context: Context) {
        val messageClient = Wearable.getMessageClient(context)
        val checksumPath = "/checksum" // message identifier tag

        messageClient.sendMessage(nodeId, checksumPath, checksum.toByteArray()).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Checksum sent successfully. Value: $checksum")

            } else {
                Log.e(TAG, "Error sending checksum: ${task.exception}")
            }
        }

    }

    private fun sendFileNameToPhone(fileName: String, nodeId: String, context: Context) {
        val messageClient = Wearable.getMessageClient(context)
        val filenamePath = "/file-name" // message identifier tag

        messageClient.sendMessage(nodeId, filenamePath, fileName.toByteArray()).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "File name sent successfully. Filename: $fileName")
            } else {
                Log.e(TAG, "Error sending filename: ${task.exception}")
            }
        }
    }


    private fun sendFileSizeToPhone(fileSize: String, nodeId: String, context: Context) {
        val messageClient = Wearable.getMessageClient(context)
        val filenamePath = "/file-size" // message identifier tag

        messageClient.sendMessage(nodeId, filenamePath, fileSize.toByteArray()).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "File size sent successfully. File size: $fileSize")
            } else {
                Log.e(TAG, "Error sending file size: ${task.exception}")
            }
        }
    }
}

// Separate class for RecyclerView items in RecyclerView activity_send_surveys.xml
class FilesAdapter(
    val filesList: MutableList<File>, private val TAG: String

): RecyclerView.Adapter<FilesAdapter.FileViewHolder>() {

    // the action for clicking the buttons
    var onFileSendClick: ((File) -> Unit)? = null
    var onFileDeleteClick: ((File) -> Unit)? = null


    // view holder initialization
    inner class FileViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
        val fileCreationTimeTextView: TextView = view.findViewById(R.id.fileCreationTimeTextView)
        val fileSizeTextView: TextView = view.findViewById(R.id.fileSizeTextView)


        // possibly add a "toDriveBtn" for sending survey to drive in the future
        val fileSendButton: ImageButton = view.findViewById(R.id.fileSendButton)
        val fileDeleteButton: ImageButton = view.findViewById(R.id.fileDeleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }


    // for Items on the view holder and what they should display or do
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = filesList[position]
        holder.fileNameTextView.text = file.nameWithoutExtension
        holder.fileCreationTimeTextView.text = getFileCreationTime(file)
        holder.fileSizeTextView.text = getFormattedFileSize(file)
        holder.fileSendButton.setOnClickListener {
            onFileSendClick!!(file) // should never be null when this is called
        }

        holder.fileDeleteButton.setOnClickListener {
            onFileDeleteClick!!(file) // should never be null when this is called
        }

    }

    // file creation time for displaying
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

    private fun getFormattedFileSize(file: File): String {

        val sizeInBytes = file.length() // File size in bytes

        val kb = sizeInBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        // takes the highest size category over 1
        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$sizeInBytes Bytes"
        }
    }

    // delete the file from the RecyclerView UI list
    fun deleteFile(fileItem: File) {
        val index = filesList.indexOf(fileItem)
        if (index != -1) {
            Log.d(TAG, "filesAdapter: file: $fileItem deleted from the list")
            filesList.removeAt(index)
            notifyItemRemoved(index)
        }
    }


    override fun getItemCount(): Int = filesList.size
}
