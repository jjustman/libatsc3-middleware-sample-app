package com.nextgenbroadcast.mobile.middleware.service.holder

import android.content.Context
import androidx.annotation.MainThread
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverCore
import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.gateway.web.IWebGateway
import com.nextgenbroadcast.mobile.middleware.server.cert.UserAgentSSLContext
import com.nextgenbroadcast.mobile.middleware.server.web.IMiddlewareWebServer
import com.nextgenbroadcast.mobile.middleware.server.web.MiddlewareWebServer
import kotlinx.coroutines.*

internal class WebServerHolder(
        private val context: Context,
        private val receiver: Atsc3ReceiverCore,
        private val onCreated: (server: IMiddlewareWebServer) -> Unit = {},
        private val onDestroyed: () -> Unit = {}
) {
    private var webGateway: IWebGateway? = null
    private var rpcGateway: IRPCGateway? = null
    private var webServer: MiddlewareWebServer? = null
    private var stateScope: CoroutineScope? = null

    @MainThread
    fun open() {
        if (webServer != null) return

        val appContext = context.applicationContext
        val sslContext = UserAgentSSLContext.newInstance(appContext)

        val scope = CoroutineScope(Dispatchers.Default).also {
            stateScope = it
        }
        val web = receiver.createWebGateway().also {
            webGateway = it
        }
        val rpc = receiver.createRPCGateway(scope).also {
            rpcGateway = it
        }

        webServer = MiddlewareWebServer.Builder()
                .stateScope(scope)
                .rpcGateway(rpc)
                .webGateway(web)
                .build().also { server ->
                    GlobalScope.launch {
                        server.start(sslContext)

                        withContext(Dispatchers.Main) {
                            onCreated(server)
                        }
                    }
                }
    }

    @MainThread
    fun close() {
        try {
            stateScope?.cancel()
        } catch (e: IllegalStateException) {
            LOG.w(TAG, "Failed to close web server state scope", e)
        }
        stateScope = null

        webServer?.let { server ->
            if (server.isRunning()) {
                GlobalScope.launch {
                    try {
                        server.stop()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    withContext(Dispatchers.Main) {
                        onDestroyed()
                    }
                }
            }
        }

        webServer = null
        webGateway = null
        rpcGateway = null
    }

    companion object {
        val TAG: String = WebServerHolder::class.java.simpleName
    }
}