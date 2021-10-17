package com.nextgenbroadcast.mobile.middleware.dev.telemetry.task

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.flow.MutableSharedFlow

class IsOnlineTelemetryTask : ITelemetryTask {
    override val name = "isOnlineTask"
    override var delayMils = 0L

    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        eventFlow.emit(TelemetryEvent(TelemetryEvent.EVENT_TOPIC_IS_ONLINE, IsOnlineData()))
    }
}

private data class IsOnlineData(
    val name: String = "online"
): TelemetryPayload()