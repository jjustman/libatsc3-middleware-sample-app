package com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity

abstract class TelemetryPayload {
    var testCase: String? = null
    val timeStamp = System.currentTimeMillis()
}