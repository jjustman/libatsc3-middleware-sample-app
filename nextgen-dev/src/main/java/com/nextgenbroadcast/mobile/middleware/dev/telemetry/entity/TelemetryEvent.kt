package com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity

data class TelemetryEvent(
     val topic: String,
     val payload: TelemetryPayload
) {
    companion object {
        const val EVENT_TOPIC_BATTERY = "battery"
        const val EVENT_TOPIC_LOCATION = "location"
        const val EVENT_TOPIC_PHY = "phy"
        const val EVENT_TOPIC_L1D = "l1d"
        const val EVENT_TOPIC_SENSORS = "sensors"
        const val EVENT_TOPIC_SAANKHYA_PHY_DEBUG = "saankhya_phy_debug"
        const val EVENT_TOPIC_ATSC3TRANSPORT = "atsc3transport"
        const val EVENT_TOPIC_WIFI = "wifi"
        const val EVENT_TOPIC_PING = "ping"
        const val EVENT_TOPIC_ERROR = "error"
        const val EVENT_TOPIC_IS_ONLINE = "is_online"
    }
}