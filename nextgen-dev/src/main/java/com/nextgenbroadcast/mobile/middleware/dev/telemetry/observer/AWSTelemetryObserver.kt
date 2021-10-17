package com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws.AWSIoThing
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent

class AWSTelemetryObserver(
    eventTopicFormat: String,
    awsIoThing: AWSIoThing,
    clientId: String,
    topics: List<String>
) : AbstractAWSObserver(eventTopicFormat, awsIoThing, clientId, topics) {
    private val gson = Gson()

    @Throws(JsonSyntaxException::class)
    override fun createEvent(topic: String, payload: String) = ClientTelemetryEvent(
        topic,
        gson.fromJson(payload, JsonObject::class.java)
    )
}