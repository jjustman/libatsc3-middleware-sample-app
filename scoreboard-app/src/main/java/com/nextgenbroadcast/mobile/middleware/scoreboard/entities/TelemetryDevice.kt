package com.nextgenbroadcast.mobile.middleware.scoreboard.entities

data class TelemetryDevice(
    val id: String,
    val host: String,
    val port: Int,
    val availableOnNSD: Boolean = false,
    val availableOnAWS: Boolean = false
) {
    val isLost: Boolean
        get() = !availableOnNSD && !availableOnAWS
}