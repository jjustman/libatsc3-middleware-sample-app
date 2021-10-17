package com.nextgenbroadcast.mobile.middleware.gateway.web

import com.nextgenbroadcast.mobile.middleware.repository.IRepository
import com.nextgenbroadcast.mobile.middleware.settings.IServerSettings

internal class WebGatewayImpl (
    repository: IRepository,
    private val settings: IServerSettings
) : IWebGateway {
    override val hostName = settings.hostName
    override var httpPort: Int
        get() = settings.httpPort
        set(value) { settings.httpPort = value }
    override var httpsPort: Int
        get() = settings.httpsPort
        set(value) { settings.httpsPort = value }
    override var wsPort: Int
        get() = settings.wsPort
        set(value) { settings.wsPort = value }
    override var wssPort: Int
        get() = settings.wssPort
        set(value) { settings.wssPort = value }

    override val appCache = repository.applications
}