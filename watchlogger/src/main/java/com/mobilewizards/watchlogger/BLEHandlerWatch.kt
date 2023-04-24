package com.mobilewizards.watchlogger

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File

class BLEHandlerWatch(private val context: Context) {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private lateinit var scanCallback: ScanCallback
    private val handler = Handler()
    private val TAG = "watchLogger"


    init {
        initializeBluetooth()
        initializeScanCallback()
    }
    private fun initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner


    }

    private var bleScanList = mutableListOf<String>()
    private fun initializeScanCallback() {
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    val measurementsList = mutableListOf<String>()
                    val device = scanResult.device
                    val rssi = scanResult.rssi
                    val data = scanResult.scanRecord

                    val measurementString =
                        "$device," +
                                "$rssi," +
                                "$data"

                    measurementsList.add(measurementString)
                    bleScanList.addAll(measurementsList)

                    Log.i("BleLogger", "Device: ${device.address} RSSI: $rssi Data: ${data?.bytes.contentToString()}")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BleLogger", "Scan failed with error code $errorCode")
            }
        }
    }

    fun setUpLogging() {
        try {
            bluetoothLeScanner?.startScan(scanCallback)
        } catch(e: SecurityException){
            Log.d("Error", "No permission for BLE fetching")
        }
    }

    fun stopLogging() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "watch_bluetooth_measurements ")
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            Log.d("uri", uri.toString())
            uri?.let { mediaUri ->
                context.contentResolver.openOutputStream(mediaUri)?.use { outputStream ->
                    outputStream.write("Device,RSSI,Data\n".toByteArray())
                    bleScanList.forEach { measurementString ->
                        outputStream.write("$measurementString\n".toByteArray())
                    }
                    outputStream.flush()
                }
//                Toast.makeText(context, "Bluetooth scan results saved to Downloads folder", Toast.LENGTH_SHORT).show()
            }

            var filePath = ""
            fun getRealPathFromUri(contentResolver: ContentResolver, uri: Uri): String {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                val cursor = contentResolver.query(uri, projection, null, null, null)
                val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor?.moveToFirst()
                val path = columnIndex?.let { cursor?.getString(it) }
                cursor?.close()
                return path ?: ""
            }
            uri?.let { getRealPathFromUri(context.contentResolver, it) }
                ?.let { Log.d("uri", it)
                    filePath = it}
            WatchActivityHandler.setFilePaths(File(filePath))
            Log.d(TAG, "gnss file path $filePath")

        } catch(e: SecurityException){
            Log.e("Error", "No permission for BLE fetching")
        }
    }
}