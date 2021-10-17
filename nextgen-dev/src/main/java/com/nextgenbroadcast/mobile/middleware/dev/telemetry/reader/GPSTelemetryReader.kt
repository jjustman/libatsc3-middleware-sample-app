package com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.ReceiverTelemetry
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive

@SuppressLint("MissingPermission")
class GPSTelemetryReader(
        context: Context
) : ITelemetryReader {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val settingsClient: SettingsClient = LocationServices.getSettingsClient(context)

    override val name = NAME
    override var delayMils: Long = LocationFrequencyType.MEDIUM.delay()

    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        val (intervalFrequency, fastestIntervalFrequency) = LocationFrequencyType.decode(delayMils)
        val locationRequest = LocationRequest.create().apply {
            interval = intervalFrequency.toLong()
            fastestInterval = fastestIntervalFrequency.toLong()
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val task = settingsClient.checkLocationSettings(builder.build())

        callbackFlow<LocationData> {
            val callback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    try {
                        sendBlocking(LocationData(location.provider, location.latitude, location.longitude))
                    } catch (e: Exception) {
                        LOG.e(TAG, "Error on sending Location data", e)
                    }
                }
            }

            task.addOnSuccessListener {
                if (isActive) {
                    fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
                }
            }.addOnFailureListener {
                cancel()
            }

            awaitClose {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }.buffer(Channel.CONFLATED) // To avoid blocking
                .collect { data ->
                    eventFlow.emit(TelemetryEvent(TelemetryEvent.EVENT_TOPIC_LOCATION, data))
                }
    }

    companion object {
        private val TAG = GPSTelemetryReader::class.java.simpleName
        const val NAME = ReceiverTelemetry.TELEMETRY_LOCATION
    }
}

enum class LocationFrequencyType(
        private val interval: Int,
        private val fastestInterval: Int
) {
    ULTRA(1000, 250),
    HIGH(2000, 1000),
    MEDIUM(5000, 2000),
    LOW(10000, 5000);

    fun delay(): Long {
        return (interval.toLong() shl 32) or (fastestInterval.toLong())
    }

    companion object {
        fun decode(delay: Long): Pair<Int, Int> {
            return Pair((delay shr 32).toInt(),  (delay and 0xFFFFFFFF).toInt())
        }
    }
}

data class LocationData(
        val provider: String,
        val lat: Double,
        val lng: Double
) : TelemetryPayload()