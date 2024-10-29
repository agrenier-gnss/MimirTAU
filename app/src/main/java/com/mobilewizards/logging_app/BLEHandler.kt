package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ContentValues
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

private var startTime: Long? = null

class BLEHandler(private val context: Context) {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private lateinit var scanCallback: ScanCallback

    init {
        initializeBluetooth()
        initializeScanCallback()
    }

    private fun initializeBluetooth() {

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private var bleScanList = mutableListOf<String>()

    fun getBLEValues(): MutableList<String> {
        return bleScanList
    }

    private fun initializeScanCallback() {
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    val measurementsList = mutableListOf<String>()
                    val device = scanResult.device
                    val rssi = scanResult.rssi
                    val data = scanResult.scanRecord

                    val measurementString =
                        "${scanResult.timestampNanos}," +
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
            startTime = System.currentTimeMillis()
        } catch(e: SecurityException){
            Log.d("Error", "No permission for BLE fetching")
        }
    }

    fun stopScanDevice(){
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException){
            Log.d("Error", "No permission for BLE fetching")
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun stopLogging() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "log_bluetooth_${SimpleDateFormat("ddMMyyyy_hhmmssSSS").format(startTime)}.csv")
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            Log.d("uri", uri.toString())
            uri?.let { mediaUri ->
                context.contentResolver.openOutputStream(mediaUri)?.use { outputStream ->
                    outputStream.write("# ".toByteArray())
                    outputStream.write("\n".toByteArray())
                    outputStream.write("# ".toByteArray())
                    outputStream.write("Header Description:".toByteArray());
                    outputStream.write("\n".toByteArray())
                    outputStream.write("# ".toByteArray())
                    outputStream.write("\n".toByteArray())
                    outputStream.write("# ".toByteArray())
                    outputStream.write("Version: ".toByteArray())
                    var manufacturer: String = Build.MANUFACTURER
                    var model: String = Build.MODEL
                    var fileVersion: String = "${BuildConfig.VERSION_CODE}" + " Platform: " +
                            "${Build.VERSION.RELEASE}" + " " + "Manufacturer: "+
                            "${manufacturer}" + " " + "Model: " + "${model}"

                    outputStream.write(fileVersion.toByteArray())
                    outputStream.write("\n".toByteArray())
                    outputStream.write("# ".toByteArray())
                    outputStream.write("\n".toByteArray())
                    outputStream.write("# ".toByteArray())
                    outputStream.write("Timestamp,Device,RSSI,Data\n".toByteArray())
                    outputStream.write("# ".toByteArray())
                    outputStream.write("\n".toByteArray())
                    bleScanList.forEach { measurementString ->
                        outputStream.write("$measurementString\n".toByteArray())
                    }
                    outputStream.flush()
                }

                val view = (context as Activity).findViewById<View>(android.R.id.content)
                val snackbar = Snackbar.make(view, "Bluetooth scan results saved to Downloads folder", Snackbar.LENGTH_LONG)
                snackbar.setAction("Close") {
                    snackbar.dismiss()
                }
                snackbar.view.setBackgroundColor(ContextCompat.getColor(context, R.color.green))
                snackbar.show()

            }

        } catch(e: SecurityException){
            Log.e("Error", "No permission for BLE fetching")
            val view = (context as Activity).findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(view, "Error. BLE does not have required permissions.", Snackbar.LENGTH_LONG)
            snackbar.setAction("Close") {
                snackbar.dismiss()
            }
            snackbar.view.setBackgroundColor(ContextCompat.getColor(context, R.color.red))
            snackbar.show()
        }
    }
}