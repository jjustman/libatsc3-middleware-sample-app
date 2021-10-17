package com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer

import com.google.gson.JsonPrimitive
import com.nextgenbroadcast.mobile.middleware.dev.atsc3.PHYStatistics
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect

class StaticTelemetryObserver : ITelemetryObserver {
    override suspend fun read(eventFlow: MutableSharedFlow<ClientTelemetryEvent>) {
        PHYStatistics.rfMetricsFlow.collect {
            eventFlow.emit(ClientTelemetryEvent(
                topic,
                JsonPrimitive(it.snr1000_global)
            ))
        }
    }

    companion object {
        const val topic = "phy"
    }
}