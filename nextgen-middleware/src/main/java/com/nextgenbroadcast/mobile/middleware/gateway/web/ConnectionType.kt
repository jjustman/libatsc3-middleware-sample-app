package com.nextgenbroadcast.mobile.middleware.gateway.web

enum class ConnectionType(val type: String) {
    HTTP("HTTP"),
    HTTPS("HTTPS"),
    WS("WS"),
    WSS("WSS")
}