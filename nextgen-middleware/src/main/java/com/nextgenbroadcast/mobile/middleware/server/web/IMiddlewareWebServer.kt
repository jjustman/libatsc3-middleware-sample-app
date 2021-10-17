package com.nextgenbroadcast.mobile.middleware.server.web

import com.nextgenbroadcast.mobile.core.dev.IWebInterface
import com.nextgenbroadcast.mobile.middleware.gateway.web.ConnectionType
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

interface IMiddlewareWebServer : IWebInterface {
    fun addConnection(type: ConnectionType, host: String, port: Int)

    override fun addHandler(path: String, onGet: (req: HttpServletRequest, resp: HttpServletResponse) -> Unit): Boolean
    override fun removeHandler(path: String)
}