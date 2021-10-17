package com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer

import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws.AWSIoThing
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

abstract class AbstractAWSObserver(
    eventTopicFormat: String,
    private val awsIoThing: AWSIoThing,
    private val clientId: String,
    /*private*/ val topics: List<String>
) : ITelemetryObserver {
    private val eventTopic = eventTopicFormat.replace(AWSIoThing.AWSIOT_FORMAT_SERIAL, clientId)

    final override suspend fun read(eventFlow: MutableSharedFlow<ClientTelemetryEvent>) {
        supervisorScope {
            topics.forEach { nextTopic ->
                launch {
                    try {
                        awsIoThing.subscribe("$eventTopic/$nextTopic") { topic, payload ->
                            payload?.let {
                                createEvent(topic, payload)?.let { event ->
                                    eventFlow.tryEmit(event)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LOG.d(TAG, "Failed observing topic $nextTopic on device: $clientId", e)
                    }
                }
            }
        }
    }

    abstract fun createEvent(topic: String, payload: String): ClientTelemetryEvent?

    companion object {
        val TAG: String = AbstractAWSObserver::class.java.simpleName
    }
}