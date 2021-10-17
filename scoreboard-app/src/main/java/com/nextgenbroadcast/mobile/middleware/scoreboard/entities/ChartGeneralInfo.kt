package com.nextgenbroadcast.mobile.middleware.scoreboard.entities

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.ErrorData

data class ChartGeneralInfo(
    val deviceId: String,
    val isLost: Boolean,
    val errorData: ErrorData?,
    val distance: Float?,
    val chartData: ChartData?
)

data class ChartConfiguration(
    val minValue: Double,
    val maxValue: Double
)