package com.nextgenbroadcast.mobile.middleware.net.sinclair

import com.nextgenbroadcast.mobile.core.toDegrees
import okhttp3.Request

internal class SinclairPlatform(
    baseUrl: String
) {
    private val propertyUrl = "$baseUrl/api/v1/property"

    fun frequenciesRequest(latitude: Double, longitude: Double, clientKey: String): Request {
        return Request.Builder()
            .url("$propertyUrl/frequencies?latitude=${latitude.toDegrees()}&longitude=${longitude.toDegrees()}")
            .addHeader("X-NGWP-Authorization", clientKey)
            .build()
    }

    fun fipsRequest(latitude: Double, longitude: Double, clientKey: String): Request {
        return Request.Builder()
            .url("$propertyUrl/location-info?latitude=${latitude.toDegrees()}&longitude=${longitude.toDegrees()}")
            .addHeader("X-NGWP-Authorization", clientKey)
            .build()
    }

//    fun frequenciesRequest(token: String, latitude: Double, longitude: Double): Request {
//        return Request.Builder()
//            .url("$baseUrl/api/v1/property/frequencies?latitude=${latitude.toDegrees()}&longitude=${longitude.toDegrees()}")
//            .addHeader("Authorization", auth(token))
//            .build()
//    }
//
//    private fun auth(token: String) = "Bearer $token"
}