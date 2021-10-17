package com.nextgenbroadcast.mobile.middleware.location

import android.content.Context
import android.location.Location
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.initialization.IFrequencyLocator
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverStandalone
import com.nextgenbroadcast.mobile.middleware.Auth0
import com.nextgenbroadcast.mobile.middleware.BuildConfig
import com.nextgenbroadcast.mobile.middleware.net.await
import com.nextgenbroadcast.mobile.middleware.net.sinclair.SinclairPlatform
import com.nextgenbroadcast.mobile.middleware.net.sinclair.Station
import okhttp3.OkHttpClient

class FrequencyLocator : IFrequencyLocator {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient()
    }

    override suspend fun locateFrequency(context: Context): List<Int> {
        val settings = Atsc3ReceiverStandalone.get(context).settings
        val locator = LocationRequester(context)
        val prevFrequencyLocation = settings.frequencyLocation

        val frequencies = mutableListOf<Int>()
        locator.getLastLocation()?.let { location ->
            Log.d(TAG, "locator.locateFrequency context: $context, location: $location")
            val prevLocation = prevFrequencyLocation?.location
            if (prevLocation == null || location.distanceTo(prevLocation) > RECEPTION_RADIUS) {
                locateFrequency(location)?.let { frequencyLocation ->
                    Log.d(TAG, "locator.locateFrequency let: $context, location: $frequencyLocation")

                    settings.frequencyLocation = frequencyLocation

                    frequencies.addAll(
                        frequencyLocation.frequencyList.filter { it > 0 }
                    )
                }
            } else {
                frequencies.addAll(prevFrequencyLocation.frequencyList)
            }
        }

        return frequencies
    }

    private suspend fun locateFrequency(location: Location): FrequencyLocation? {
        val frequencyList = getFrequenciesByLocation(location.latitude, location.longitude)
        if (frequencyList.isNotEmpty()) {
            return FrequencyLocation(location, frequencyList)
        }

        return null
    }

    private suspend fun getFrequenciesByLocation(latitude: Double, longitude: Double): List<Int> {
        try {
            val frequenciesRequest = SinclairPlatform(BuildConfig.SinclairPlatformUrl)
                .frequenciesRequest(latitude, longitude, Auth0.clientKey())

            LOG.d(TAG, "getFrequenciesByLocation, frequenciesRequest is: $frequenciesRequest")
            val stations: List<Station>? = httpClient.newCall(frequenciesRequest).await { response ->
                response.body?.let { body ->
                    Gson().fromJson(body.string(), object : TypeToken<List<Station>?>() {}.type)
                }
            }

            if (!stations.isNullOrEmpty()) {
                LOG.i(TAG, "getFrequenciesByLocation, returning stations: $stations")
                return stations.map { station ->
                    station.frequency * 1000
                }.distinct()
            }
        } catch (e: Exception) {
            LOG.d(TAG, "Error on frequency request", e)
        }

        LOG.d(TAG, "getFrequenciesByLocation, returning emptyList()")
        return emptyList()
    }

    companion object {
        val TAG: String = FrequencyLocator::class.java.simpleName

        const val RECEPTION_RADIUS = 50 * 1000 // in metres
    }
}