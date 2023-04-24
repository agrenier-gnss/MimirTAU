package com.mobilewizards.logging_app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.location.*
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import java.util.*


private lateinit var locationManager: LocationManager
private lateinit var gpsLocationListener: LocationListener
private lateinit var networkLocationListener: LocationListener
private lateinit var gnssMeasurementsEventListener: android.location.GnssMeasurementsEvent.Callback
private lateinit var gnssNavigationMessageListener: android.location.GnssNavigationMessage.Callback

private const val VERSION_TAG = "Version: "
private const val COMMENT_START = "# "

class GnssHandler{

    protected var context: Context


    constructor(context: Context) : super() {
        this.context = context.applicationContext
    }
    private var gnssMeasurementsList = mutableListOf<String>()
    public fun setUpLogging(){
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        logLocation(1000)
        logGNSS( 1000)
        logGnssNavigationMessages(1000)
    }

    private fun logLocation(samplingFrequency: Long) {
        //Locationlistener for Gps
        gpsLocationListener = object : LocationListener{

            override fun onLocationChanged(location: Location){

                val locationStream: String = java.lang.String.format(
                    Locale.US,
                    "Fix,%s,%f,%f,%f,%f,%f,%d",
                    location.provider,
                    location.latitude,
                    location.longitude,
                    location.altitude,
                    location.speed,
                    location.accuracy,
                    location.time
                )
                gnssMeasurementsList.add(locationStream)
            }

            override fun onFlushComplete(requestCode: Int) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}

        }

        networkLocationListener = object : LocationListener{
            override fun onLocationChanged(location: Location){

                val locationStream: String = java.lang.String.format(
                    Locale.US,
                    "Fix,%s,%f,%f,%f,%f,%f,%d",
                    location.provider,
                    location.latitude,
                    location.longitude,
                    location.altitude,
                    location.speed,
                    location.accuracy,
                    location.time
                )
                gnssMeasurementsList.add(locationStream)
            }
            override fun onFlushComplete(requestCode: Int) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    samplingFrequency, 0F, gpsLocationListener
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    samplingFrequency, 0F, networkLocationListener
                )
            }
        }
        catch(e: SecurityException){
            Log.e("Error", "No permission for location fetching")
            val view = (context as Activity).findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(view, "Error. GNSS does not have required permissions.", Snackbar.LENGTH_LONG)
            snackbar.setAction("Close") {
                snackbar.dismiss()
            }
            snackbar.view.setBackgroundColor(ContextCompat.getColor(context, R.color.red))
            snackbar.show()
        }
    }


    private fun logGNSS( samplingFrequency: Long) {
        gnssMeasurementsEventListener = object : android.location.GnssMeasurementsEvent.Callback(){
            var lastMeasurementTime = 0L
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {

                val currentTime = SystemClock.elapsedRealtime()
                if (currentTime - lastMeasurementTime >= samplingFrequency) {

                    val measurementsList = mutableListOf<String>()

                    var clock: GnssClock = event.clock

                    val clockString="Raw,"+"$currentTime," + "${clock.timeNanos}," +
                            "${clock.getTimeNanos()}," +
                            "${if (clock.hasLeapSecond()) clock.leapSecond else ""},"+
                            "${if(clock.hasTimeUncertaintyNanos()) clock.getTimeUncertaintyNanos() else ""},"+
                            "${clock.getFullBiasNanos()},"+
                            "${if(clock.hasBiasNanos()) clock.getBiasNanos() else ""},"+
                            "${if(clock.hasBiasUncertaintyNanos() ) clock.getBiasUncertaintyNanos() else ""},"+
                            "${if(clock.hasDriftNanosPerSecond()) clock.getDriftNanosPerSecond() else ""},"+
                            "${if(clock.hasDriftUncertaintyNanosPerSecond()) clock.getDriftUncertaintyNanosPerSecond() else ""},"+
                            "${clock.getHardwareClockDiscontinuityCount()}" + ","

                    for (measurement in event.measurements) {

                        var carrierPhase: Double
                        var carrierPhaseString: String
                        var carrierCycle: Int
                        var carrierCylceString: String
                        var carrierPhaseUncertainty: Double
                        var carriePhaseUncertaintyString: String

                        if(measurement.hasCarrierFrequencyHz()){
                            carrierPhase = measurement.getAccumulatedDeltaRangeMeters() / (2 * kotlin.math.PI * measurement.carrierFrequencyHz)
                            carrierCycle = (carrierPhase/(2*kotlin.math.PI)).toInt()
                            carrierPhaseUncertainty = measurement.accumulatedDeltaRangeUncertaintyMeters/(2*kotlin.math.PI*measurement.carrierFrequencyHz)
                            carrierPhaseString = "${carrierPhase}"
                            carriePhaseUncertaintyString = "${carrierPhaseUncertainty}"
                            carrierCylceString = "${carrierCycle}"
                        }
                        else{
                            carrierPhaseString = ""
                            carriePhaseUncertaintyString = ""
                            carrierCylceString = ""
                        }

                        val measurementString =
                            "${measurement.getSvid()}," +
                                    "${measurement.getTimeOffsetNanos()}," +
                                    "${measurement.getState()}," +
                                    "${measurement.getReceivedSvTimeNanos()}," +
                                    "${measurement.getReceivedSvTimeUncertaintyNanos()}," +
                                    "${measurement.getCn0DbHz()}," +
                                    "${measurement.getPseudorangeRateMetersPerSecond()}," +
                                    "${measurement.getPseudorangeRateUncertaintyMetersPerSecond()}," +
                                    "${measurement.getAccumulatedDeltaRangeState()}," +
                                    "${measurement.getAccumulatedDeltaRangeMeters()}," +
                                    "${measurement.getAccumulatedDeltaRangeUncertaintyMeters()}," +
                                    "${if(measurement.hasCarrierFrequencyHz()) measurement.getCarrierFrequencyHz() else ""}," +
                                    "${if(measurement.hasCarrierCycles()) measurement.carrierCycles else ""},"+
                                    "${if(measurement.hasCarrierPhase()) measurement.carrierPhase else ""}," +
                                    "${if(measurement.hasCarrierPhaseUncertainty()) measurement.carrierPhaseUncertainty else ""}," +
                                    "${measurement.getMultipathIndicator()}," +
                                    "${if(measurement.hasSnrInDb()) measurement.getSnrInDb() else ""}," +
                                    "${measurement.getConstellationType()}," +
                                    "${if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && measurement.hasAutomaticGainControlLevelDb())
                                        measurement.getAutomaticGainControlLevelDb() else ""}"

                        val eventString = clockString + measurementString



                        measurementsList.add(eventString)
                        Log.d("GNSS Measurement", measurementString)
                    }
                    gnssMeasurementsList.addAll(measurementsList)
                    lastMeasurementTime = currentTime
                }
            }

            override fun onStatusChanged(status: Int) {}
        }

        try {
            locationManager.registerGnssMeasurementsCallback(gnssMeasurementsEventListener)
        } catch (e: SecurityException) {
            Log.e("Error", "No permission for location fetching")
            val view = (context as Activity).findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(view, "Error. GNSS does not have required permissions.", Snackbar.LENGTH_LONG)
            snackbar.setAction("Close") {
                snackbar.dismiss()
            }
            snackbar.view.setBackgroundColor(ContextCompat.getColor(context, R.color.red))
            snackbar.show()
        }

    }

    fun logGnssNavigationMessages(samplingFrequency: Long){
        gnssNavigationMessageListener = object : GnssNavigationMessage.Callback(){
            var lastMeasurementTime = 0L
            override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage?) {

                val currentTime = SystemClock.elapsedRealtime()
                if (currentTime - lastMeasurementTime >= samplingFrequency) {
                    var gnssNavigationMessageString = "Nav," +
                            "${event?.getSvid()}," +
                            "${event?.getType()}," +
                            "${event?.getStatus()}," +
                            "${event?.getMessageId()}," +
                            "${event?.submessageId},"


                    val data: ByteArray? = event?.getData()
                    if (data != null) {
                        for (word in data) {
                            gnssNavigationMessageString += "${word.toInt()},"
                        }
                    }
                    gnssMeasurementsList.add(gnssNavigationMessageString)
                }
            }

        }

        locationManager.registerGnssNavigationMessageCallback(gnssNavigationMessageListener)
    }


    fun stopLogging(context: Context) {
        locationManager.removeUpdates(gpsLocationListener)
        locationManager.removeUpdates(networkLocationListener)
        locationManager.unregisterGnssMeasurementsCallback(gnssMeasurementsEventListener)
        locationManager.unregisterGnssNavigationMessageCallback(gnssNavigationMessageListener)

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "gnss_measurements.csv")
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        Log.d("uri", uri.toString())
        uri?.let { mediaUri ->
            context.contentResolver.openOutputStream(mediaUri)?.use { outputStream ->
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray());
                outputStream.write("Header Description:".toByteArray());
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write(VERSION_TAG.toByteArray())
                var manufacturer: String = Build.MANUFACTURER
                var model: String = Build.MODEL
                var fileVersion: String = "${BuildConfig.VERSION_CODE}" + " Platform: " +
                        "${Build.VERSION.RELEASE}" + " " + "Manufacturer: "+
                        "${manufacturer}" + " " + "Model: " + "${model}"

                outputStream.write(fileVersion.toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write(
                    "Raw,ElapsedRealtimeMillis,TimeNanos,LeapSecond,TimeUncertaintyNanos,FullBiasNanos,".toByteArray()
                            + "BiasNanos,BiasUncertaintyNanos,DriftNanosPerSecond,DriftUncertaintyNanosPerSecond,".toByteArray()
                            + "HardwareClockDiscontinuityCount,Svid,TimeOffsetNanos,State,ReceivedSvTimeNanos,".toByteArray()
                            + "ReceivedSvTimeUncertaintyNanos,Cn0DbHz,PseudorangeRateMetersPerSecond,".toByteArray()
                            + "PseudorangeRateUncertaintyMetersPerSecond,".toByteArray()
                            + "AccumulatedDeltaRangeState,AccumulatedDeltaRangeMeters,".toByteArray()
                            + "AccumulatedDeltaRangeUncertaintyMeters,CarrierFrequencyHz,CarrierCycles,".toByteArray()
                            + "CarrierPhase,CarrierPhaseUncertainty,MultipathIndicator,SnrInDb,".toByteArray()
                            + "ConstellationType,AgcDb".toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write(
                    "Fix,Provider,Latitude,Longitude,Altitude,Speed,Accuracy,(UTC)TimeInMs".toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write("Nav,Svid,Type,Status,MessageId,Sub-messageId,Data(Bytes)".toByteArray());
                outputStream.write("\n".toByteArray())
                outputStream.write(COMMENT_START.toByteArray())
                outputStream.write("\n".toByteArray())

                gnssMeasurementsList.forEach { measurementString ->
                    outputStream.write("$measurementString\n".toByteArray())
                }


                outputStream.flush()
            }
            val view = (context as Activity).findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(view, "GNSS scan results saved to Downloads folder", Snackbar.LENGTH_LONG)
            snackbar.setAction("Close") {
                snackbar.dismiss()
            }
            snackbar.view.setBackgroundColor(ContextCompat.getColor(context, R.color.green))
            snackbar.show()
        }
    }
}