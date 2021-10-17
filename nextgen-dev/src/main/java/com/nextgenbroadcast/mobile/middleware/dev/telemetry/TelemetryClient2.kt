package com.nextgenbroadcast.mobile.middleware.dev.telemetry

import com.google.gson.Gson
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer.ITelemetryObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

class TelemetryClient2(
    private val stackSize: Int
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val observers = mutableMapOf<ITelemetryObserver, MutableSharedFlow<ClientTelemetryEvent>>()
    private val observerJobs = ConcurrentHashMap<ITelemetryObserver, Job>()

    val gson = Gson()

    fun start() {
        try {
            observers.forEach { (observer, flow) ->
                if (!observerJobs.containsKey(observer)) {
                    coroutineScope.launchObserver(observer, flow)
                }
            }
        } catch (e: Exception) {
            LOG.d(TAG, "Telemetry gathering error: ", e)
        }
    }

    fun stop() {
        observerJobs.values.forEach { job ->
            if (!job.isCancelled) job.cancel()
        }
        observerJobs.clear()
    }

    fun addObserver(observer: ITelemetryObserver) {
        addObserver(observer, newEventFlow())
    }

    fun addObserver(observer: ITelemetryObserver, flow: MutableSharedFlow<ClientTelemetryEvent>) {
        observers[observer] = flow
        coroutineScope.launchObserver(observer, flow)
    }

    fun removeObserver(observer: ITelemetryObserver) {
        observerJobs.remove(observer)?.cancel()
        observers.remove(observer)
    }

    fun getFlow(observer: ITelemetryObserver): Flow<ClientTelemetryEvent>? {
        return observers[observer]
    }

    private fun newEventFlow() = MutableSharedFlow<ClientTelemetryEvent>(
        replay = stackSize,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private fun CoroutineScope.launchObserver(observer: ITelemetryObserver, eventFlow: MutableSharedFlow<ClientTelemetryEvent>) {
        observerJobs.put(observer,
            launch {
                observer.read(eventFlow)
            }.apply {
                invokeOnCompletion {
                    observerJobs.remove(observer, this)
                }
            }
        )?.let {
            LOG.e(TAG, "Observer is duplicated")
            it.cancel()
        }
    }

    companion object {
        val TAG: String = TelemetryClient2::class.java.simpleName
    }
}