package com.nextgenbroadcast.mobile.middleware.rpc.eventStream.model

data class EventStreamEventParams (
    var schemeIdUri: String,
    var value: String? = null,
    var eventTime: Double,
    var id: Int? = null,
    var data: String? = null
)