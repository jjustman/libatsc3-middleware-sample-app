package com.nextgenbroadcast.mobile.middleware.settings

interface IServerSettings {
    val hostName: String
    var httpPort: Int
    var httpsPort: Int
    var wsPort: Int
    var wssPort: Int
}