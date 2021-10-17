package com.nextgenbroadcast.mobile.middleware.atsc3

import com.nextgenbroadcast.mobile.middleware.atsc3.entities.Atsc3ServiceLocationTable

class Atsc3Profile(
        val sourceType: String,
        val configs: Map<Any, Atsc3ServiceLocationTable>,
        val timestamp: Long,
        val location: SimpleLocation
) {
    class SimpleLocation(
            val lat: Double,
            val lng: Double
    )
}