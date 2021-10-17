package com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer

import com.google.gson.JsonPrimitive
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws.AWSIoThing
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent

class AWSLoggingObserver(
    eventTopicFormat: String,
    awsIoThing: AWSIoThing,
    clientId: String,
    topics: List<String>
) : AbstractAWSObserver(eventTopicFormat, awsIoThing, clientId, topics) {

    override fun createEvent(topic: String, payload: String) = ClientTelemetryEvent(
        topic,
        JsonPrimitive(payload)
    )
}