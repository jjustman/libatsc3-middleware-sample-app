package com.nextgenbroadcast.mobile.middleware.server.ws

import android.util.Log
import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.rpc.processor.RPCProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter

class MiddlewareWebSocket(
        private val rpcGateway: IRPCGateway
) : WebSocketAdapter() {

    private val rpcProcessor = RPCProcessor(rpcGateway)

    private var outbound: Session? = null

    override fun onWebSocketText(message: String?) {
        super.onWebSocketText(message)
        Log.d("WSServer: ", "<-- onWebSocketText: $message")

        if (message != null) {
            val response = rpcProcessor.processRequest(message)

            sendMessage(response)
        }
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        super.onWebSocketClose(statusCode, reason)
        Log.d("WSServer: ", "onWebSocketClose reason: $reason , statusCode: $statusCode")

        rpcGateway.onSocketClosed(this)

        outbound = null
    }

    override fun onWebSocketConnect(session: Session) {
        super.onWebSocketConnect(session)
        Log.d("WSServer: ", "onWebSocketConnect: " + session.localAddress)

        outbound = session

        rpcGateway.onSocketOpened(this)
    }

    override fun onWebSocketError(cause: Throwable) {
        super.onWebSocketError(cause)
        Log.d("WSServer: ", "onWebSocketError: " + cause.message)

        cause.printStackTrace(System.err)
    }

    fun sendMessage(message: String) {
        if (outbound?.isOpen == true) {
            Log.d("WSServer: ", "--> onWebSocketText: $message")

            session.remote.sendString(message, null)
        }
    }

    suspend fun disconnect() {
        outbound?.let { session ->
            if (!session.isOpen) return

            session.close()

            delay(500)

            if (session.isOpen) {
                disconnect()
            }
        }
    }

    fun disconnect(scope: CoroutineScope) {
        scope.launch {
            disconnect()
        }
    }
}