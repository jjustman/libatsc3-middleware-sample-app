package com.nextgenbroadcast.mobile.middleware.settings

import java.time.LocalDateTime
import java.util.*

interface IMiddlewareSettings : IServerSettings, IClientSettings, IReceiverSettings {
    val deviceId: String
    val advertisingId: String
    var lastReportDate: LocalDateTime
    var locale: Locale
}