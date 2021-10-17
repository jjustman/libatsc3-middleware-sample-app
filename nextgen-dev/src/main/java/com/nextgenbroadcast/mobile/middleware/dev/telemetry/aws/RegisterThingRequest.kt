package com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws

class RegisterThingRequest (
    var certificateOwnershipToken: String
) {
    val parameters = mutableMapOf<String, String>()
}