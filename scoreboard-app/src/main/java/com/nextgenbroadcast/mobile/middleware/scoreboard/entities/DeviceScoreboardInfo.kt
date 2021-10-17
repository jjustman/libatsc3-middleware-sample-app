package com.nextgenbroadcast.mobile.middleware.scoreboard.entities

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.ErrorData

data class DeviceScoreboardInfo(
    val device: TelemetryDevice,
    val selected: Boolean,
    val distance: Float? = null,
    val errorData: ErrorData? = null
)