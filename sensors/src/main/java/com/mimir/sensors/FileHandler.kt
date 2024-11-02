package com.mimir.sensors

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileHandler (context: Context, looper: Looper): Handler(looper) {

    //var mFileWriter: BufferedWriter
    //var file : File
    lateinit var mOutputStream : OutputStream

    val mSensorsResults = mutableListOf<String>()

    // ---------------------------------------------------------------------------------------------

    init {

        // Creating the log file
        val date = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val formatted = date.format(formatter)
        val filename = "log_mimir_$formatted.txt"

        // Save in external or internal storage
        // 2024.01.24: External not working in Google Pixel Watch since WearOS 4.0. Quick fix is to save data
        //             the internal memory of the app. Data can still be recovered through Android
        //             Studio File Explorer, at "/data/data/com.mobilewizards.logging_app/files/"
        // https://issuetracker.google.com/issues/299174252
        if(context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)){
            saveInInternalStorage(context, filename)
        }
        else{
            saveInExternalStorage(context, filename)
        }

        // Write basic header
        writeToFile(getHeader())
    }

    // ---------------------------------------------------------------------------------------------

    fun saveInExternalStorage(context: Context, filename: String) {

        // save in /storage/emulated/0/Android/data/com.mobilewizards.logging_app/files/Download
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)

        // Ensure the directory exists
        file.parentFile?.mkdirs()

        try {
            mOutputStream = FileOutputStream(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun saveInInternalStorage(context: Context, filename: String){

        // Get the app's private internal storage directory
        val internalStorageDir: File = context.filesDir

        // Create a File object with the desired filename
        val file = File(internalStorageDir, filename)

        try {
            mOutputStream = FileOutputStream(file)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // ---------------------------------------------------------------------------------------------


    fun getHeader() : String{

        var str = ""

        str += "#\n"
        str += "# Header Description:\n"
        str += "#\n"
        str += "# Version: " + "1.0" + " Platform: " + Build.MANUFACTURER + " Model: " + Build.MODEL + "\n"
        str += "#"

        return str
    }

    // ---------------------------------------------------------------------------------------------

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)

        // Buffer messages to avoid constant interaction with file
        mSensorsResults.add(msg.obj as String)
        if(mSensorsResults.size > 100) {
            for(str in mSensorsResults){
                writeToFile(str)
            }
            mOutputStream.flush()
            mSensorsResults.clear()
        }
    }

    // ---------------------------------------------------------------------------------------------

    fun writeToFile(mobject : String ){
        //Log.d("FileHandler", "Message written to file")
        mOutputStream.write(mobject.toByteArray())
        mOutputStream.write("\n".toByteArray())
    }

    // ---------------------------------------------------------------------------------------------

    fun closeFile(){

        // Write everything still in buffer before closing
        for(str in mSensorsResults){
            writeToFile(str)
        }
        mOutputStream.flush()
        mSensorsResults.clear()

        mOutputStream.close()
    }

}