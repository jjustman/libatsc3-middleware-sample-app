package com.nextgenbroadcast.mobile.middleware.dev.telemetry.task

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.flow.MutableSharedFlow

class PongTelemetryTask : ITelemetryTask {
    override val name = "pongTask"
    override var delayMils = 0L

    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        eventFlow.emit(TelemetryEvent(TelemetryEvent.EVENT_TOPIC_PING, PongData()))
    }
}

private data class PongData(
        val name: String = "pong"
): TelemetryPayload()