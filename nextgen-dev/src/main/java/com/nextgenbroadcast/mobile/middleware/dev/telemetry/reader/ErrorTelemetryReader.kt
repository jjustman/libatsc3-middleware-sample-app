package com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader

import com.nextgenbroadcast.mobile.middleware.dev.telemetry.ReceiverTelemetry
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect

class ErrorTelemetryReader(
    private val errorFlow: SharedFlow<String>
) : ITelemetryReader {

    override val name = NAME
    override var delayMils: Long = -1

    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        errorFlow.collect { errorMessage ->
            eventFlow.emit(TelemetryEvent(
                TelemetryEvent.EVENT_TOPIC_ERROR,
                ErrorData(errorMessage)
            ))
        }
    }

    companion object {
        const val NAME = ReceiverTelemetry.TELEMETRY_ERROR
    }
}

data class ErrorData(
    val message: String
) : TelemetryPayload()
