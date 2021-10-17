package com.nextgenbroadcast.mobile.middleware.settings

interface IClientSettings {
    val hostName: String
    val httpsPort: Int
    val wssPort: Int
}