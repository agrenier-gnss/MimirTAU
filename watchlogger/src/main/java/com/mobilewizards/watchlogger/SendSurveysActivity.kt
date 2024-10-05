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

private const val VERSION_TAG = "Version: "
private const val COMMENT_START = "# "


class SendSurveysActivity: Activity() {

    private lateinit var binding: ActivitySendSurveysBinding
    private val TAG = "watchLogger"
    private val CSV_FILE_CHANNEL_PATH = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    private var filePaths = mutableListOf<File>()
    private var fileSendOk: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySendSurveysBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toPhoneBtn = findViewById<Button>(R.id.SendToPhoneBtn)
        val toDriveBtn = findViewById<Button>(R.id.SendToDrive)

        WatchActivityHandler.getFilePaths().forEach { path ->
            filePaths.add(path)
        }

        toPhoneBtn.setOnClickListener {
            sendFiles()
        }

        toDriveBtn.setOnClickListener {
            //
        }
    }

    private fun fileSendSuccessful() {
        fileSendOk = true
        WatchActivityHandler.fileSendStatus(fileSendOk)
        val openSendInfo = Intent(applicationContext, FileSendActivity::class.java)
        startActivity(openSendInfo)
    }

    private fun fileSendTerminated() {
        fileSendOk = false
        WatchActivityHandler.fileSendStatus(fileSendOk)
        val openSendInfo = Intent(applicationContext, FileSendActivity::class.java)
        startActivity(openSendInfo)
    }

    @SuppressLint("SimpleDateFormat")
    private fun sendFiles() {
        getPhoneNodeId { nodeIds ->
            Log.d(TAG, "Received nodeIds: $nodeIds")
            // Check if there are connected nodes
            val connectedNode: String = if (nodeIds.size > 0) nodeIds[0] else ""

            if (connectedNode.isEmpty()) {
                Log.d(TAG, "no nodes found")
                Toast.makeText(this, "Phone not connected", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "nodes found, sending")

                val contentValues = ContentValues().apply {
                    put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        "log_watch_${SimpleDateFormat("ddMMyyyy_hhmmssSSS").format(startTime)}.csv"
                    )
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = this.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                var path = ""
                uri?.let { mediaUri ->
                    this.contentResolver.openOutputStream(mediaUri)?.use { outputStream ->

                        outputStream.write(COMMENT_START.toByteArray())
                        outputStream.write("log_watch_${SimpleDateFormat("ddMMyyyy_hhmmssSSS").format(startTime)}.csv\n".toByteArray())
                        outputStream.write(COMMENT_START.toByteArray())
                        outputStream.write("\n".toByteArray())
                        outputStream.write(COMMENT_START.toByteArray())
                        outputStream.write("Header Description:".toByteArray())
                        outputStream.write("\n".toByteArray())
                        outputStream.write(COMMENT_START.toByteArray())
                        outputStream.write("\n".toByteArray())
                        outputStream.write(COMMENT_START.toByteArray())
                        outputStream.write(VERSION_TAG.toByteArray())
                        val manufacturer: String = Build.MANUFACTURER
                        val model: String = Build.MODEL
                        val fileVersion =
                            "${BuildConfig.VERSION_CODE} Platform: ${Build.VERSION.RELEASE} Manufacturer: $manufacturer Model: $model"
                        outputStream.write(fileVersion.toByteArray())
                        outputStream.write("\n".toByteArray())
                        outputStream.write(COMMENT_START.toByteArray())
                        outputStream.write("\n".toByteArray())

                        WatchActivityHandler.getFilePaths().forEach { file ->

                            val reader = BufferedReader(FileReader(file))

                            outputStream.write("\n".toByteArray())
                            outputStream.write(COMMENT_START.toByteArray())
                            outputStream.write("${file.name}\n".toByteArray())

                            var line: String? = reader.readLine()
                            while (line != null) {
                                outputStream.write("$line\n".toByteArray())
                                line = reader.readLine()
                            }
                            reader.close()
                        }
                        outputStream.flush()

                        val cursor = contentResolver.query(mediaUri, null, null, null, null)
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val columnIndex = c.getColumnIndex(MediaStore.Images.Media.DATA)
                                if (columnIndex >= 0) {
                                    path = c.getString(columnIndex)
                                    // Use the file path as needed
                                    Log.d("File path", path)
                                } else {
                                    Log.e("Error", "Column MediaStore.Images.Media.DATA not found.")
                                }
                            }
                        }
                    }
                }
                sendCsvFileToPhone(File(path), connectedNode, this)
            }
        }
    }

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

    private fun sendCsvFileToPhone(csvFile: File, nodeId: String, context: Context) {
        Log.d(TAG, "in sendCsvFileToPhone " + csvFile.name)
        // Checks if the file is found and read
        try {
            val bufferedReader = BufferedReader(FileReader(csvFile))
            var line: String? = bufferedReader.readLine()
            while (line != null) {
                Log.d(TAG, line.toString())
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
                // Send the CSV file to the phone
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