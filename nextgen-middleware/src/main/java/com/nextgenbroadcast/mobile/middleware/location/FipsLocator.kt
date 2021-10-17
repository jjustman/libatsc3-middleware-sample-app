package com.nextgenbroadcast.mobile.middleware.location

import android.content.Context
import android.location.Location
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.Auth0
import com.nextgenbroadcast.mobile.middleware.BuildConfig
import com.nextgenbroadcast.mobile.middleware.net.await
import com.nextgenbroadcast.mobile.middleware.net.sinclair.LocationInfo
import com.nextgenbroadcast.mobile.middleware.net.sinclair.SinclairPlatform
import okhttp3.OkHttpClient

class FipsLocator {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient()
    }

    suspend fun locateFips(context: Context): String? {
        val locator = LocationRequester(context)
        return locator.getLastLocation()?.let { location ->
            locateFips(location)
        }
    }

    private suspend fun locateFips(location: Location): String? {
        return getFipsByLocation(location.latitude, location.longitude)?.fips
    }

    private suspend fun getFipsByLocation(latitude: Double, longitude: Double): LocationInfo? {
        try {
            val request = SinclairPlatform(BuildConfig.SinclairPlatformUrl).fipsRequest(latitude, longitude, Auth0.clientKey())
            return httpClient.newCall(request).await { response ->
                response.body?.let { body ->
                    Gson().fromJson(body.string(), object : TypeToken<LocationInfo?>() {}.type)
                }
            }
        } catch (e: Exception) {
            LOG.d(TAG, "Error on frequency request", e)
        }

        return null
    }

    companion object {
        val TAG: String = FipsLocator::class.java.simpleName
    }
}