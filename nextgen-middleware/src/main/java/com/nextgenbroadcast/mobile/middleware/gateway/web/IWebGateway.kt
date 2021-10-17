package com.nextgenbroadcast.mobile.middleware.gateway.web

import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3Application
import kotlinx.coroutines.flow.StateFlow

internal interface IWebGateway {
    val hostName: String
    var httpPort: Int
    var httpsPort: Int
    var wsPort: Int
    var wssPort: Int

    val appCache: StateFlow<List<Atsc3Application>>
}