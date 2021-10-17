package com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import kotlinx.coroutines.flow.Flow

interface ITelemetryWriter {
    fun open()
    fun close()

    suspend fun write(eventFlow: Flow<TelemetryEvent>)
}