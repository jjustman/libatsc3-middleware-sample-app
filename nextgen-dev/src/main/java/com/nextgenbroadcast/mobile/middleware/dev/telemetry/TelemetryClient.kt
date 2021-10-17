package com.nextgenbroadcast.mobile.middleware.dev.telemetry

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer.ITelemetryObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.lang.reflect.Type

class TelemetryClient(
    private val telemetryObserver: ITelemetryObserver,
    stackSize: Int
) {
    val gson = Gson()
    val eventFlow = MutableSharedFlow<ClientTelemetryEvent>(
        replay = stackSize,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var observingJob: Job? = null

    fun start(reset: Boolean = true) {
        if (observingJob != null) return

        if (reset) eventFlow.resetReplayCache()

        observingJob = CoroutineScope(Dispatchers.IO).launch {
            telemetryObserver.read(eventFlow)
        }
    }

    fun stop() {
        observingJob?.cancel()
        observingJob = null
    }

    fun isStarted(): Boolean {
        return observingJob?.isActive ?: false
    }

    inline fun <reified T> getPayloadFlow(): Flow<T?> {
        return eventFlow.map { event ->
            try {
                gson.fromJson<T>(event.payload, typeToken<T>())
            } catch (e: Exception) {
                LOG.w(TAG, "Cann't parse telemetry event", e)
                null
            }
        }
    }

    inline fun <reified T> typeToken(): Type = object: TypeToken<T>() {}.type

    companion object {
        val TAG: String = TelemetryClient::class.java.simpleName
    }
}