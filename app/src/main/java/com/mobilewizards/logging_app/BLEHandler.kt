package com.mobilewizards.logging_app

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ContentValues
import android.content.Context
import android.icu.text.SimpleDateFormat
import android.net.MacAddress
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.material.snackbar.Snackbar
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.UUID


private var startTime: Long? = null

class BLEHandler(private val context: Context) {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private lateinit var scanCallback: ScanCallback
    private var pairedDevices: Set<BluetoothDevice>? = null
    init {
        initializeBluetooth()
        initializeScanCallback()
    }

    @SuppressLint("MissingPermission")
    private fun initializeBluetooth() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        pairedDevices= bluetoothAdapter.bondedDevices

    }

    private var bleScanList = mutableListOf<String>()
    private var bleScanDeviceList = mutableListOf<BluetoothDevice>()

    fun getBLEValues(): MutableList<String> {
        return bleScanList
    }

    fun getBlEDeviceValues(): MutableList<BluetoothDevice>{
        return bleScanDeviceList
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): Set<BluetoothDevice>? {
        return  pairedDevices
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
                    bleScanDeviceList.add(scanResult.device)
                    bleScanList.addAll(measurementsList)

                    Log.i("BleLogger", "Device: ${device.address} RSSI: $rssi Data: ${data?.bytes.contentToString()}")
                }
            }
            override fun onBatchScanResults(results: List<ScanResult>) {
                super.onBatchScanResults(results)
                for (result in results) {
                    val device = result.device
                    val rssi = result.rssi
                    val scanRecord = result.scanRecord?.bytes

                    Log.i(
                        "ScanCallback",
                        "Device: ${device.address} RSSI: $rssi Data: ${scanRecord?.contentToString()}"
                    )
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
            Log.d("Error", e.message.toString())
        }
    }
    fun setUpLoggingWithFilter(filters:List<ScanFilter> , settings: ScanSettings) {
        try {
            bluetoothLeScanner?.startScan(filters,settings,scanCallback)
            startTime = System.currentTimeMillis()
        } catch(e: SecurityException){
            Log.d("Error", e.message.toString())
        }
    }



    @SuppressLint("MissingPermission")
    fun connectPairedDevice(deviceAddress: String){
        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(deviceAddress)
        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID
        val socket: BluetoothSocket? = device?.createRfcommSocketToServiceRecord(uuid)
        try {
            socket?.use {
                bluetoothAdapter.cancelDiscovery()
                it.connect()
                // Connection successful
            }
        } catch (e: IOException) {
            e.printStackTrace()
            // Handle the error
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
            Log.e("Error", e.message.toString())
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