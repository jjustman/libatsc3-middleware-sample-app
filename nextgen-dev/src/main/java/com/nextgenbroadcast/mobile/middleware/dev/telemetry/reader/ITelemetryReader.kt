package com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import kotlinx.coroutines.flow.MutableSharedFlow

interface ITelemetryReader {
    val name: String
    var delayMils: Long

    suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>)
}