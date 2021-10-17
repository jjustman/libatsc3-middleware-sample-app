package com.nextgenbroadcast.mobile.middleware.dev.telemetry.control

import com.google.gson.Gson
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws.AWSIoThing
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryControl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow

class AWSIoTelemetryControl(
    private val controlTopic: String,
    private val globalTopic: String,
    private val thing: AWSIoThing
) : ITelemetryControl {
    private val gson = Gson()

    override suspend fun subscribe(commandFlow: MutableSharedFlow<TelemetryControl>) {
        val payloadToControl = { payload: String? ->
            if (payload.isNullOrBlank()) {
                TelemetryControl()
            } else {
                gson.fromJson(payload, TelemetryControl::class.java)
            }
        }

        supervisorScope {
            while (isActive) {
                try {
                    launch {
                        thing.subscribe(controlTopic) { _, command ->
                            commandFlow.tryEmit(payloadToControl(command))
                        }
                    }

                    launch {
                        thing.subscribe(globalTopic) { topic, command ->
                            commandFlow.tryEmit(payloadToControl(command).apply {
                                action = topic.substring(topic.lastIndexOf("/") + 1)
                            })
                        }
                    }
                } catch (e: Exception) {
                    LOG.d(TAG, "AWS IoT command topic subscription error", e)
                }

                if (isActive) {
                    delay(10_000)
                }
            }
        }
    }

    companion object {
        val TAG: String = AWSIoTelemetryControl::class.java.simpleName
    }
}