package com.nextgenbroadcast.mobile.middleware.scoreboard.entities

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.LocationData

class LocationDataAndTime(
    val location: LocationData
) {
    val timestamp: Long = System.currentTimeMillis()
}