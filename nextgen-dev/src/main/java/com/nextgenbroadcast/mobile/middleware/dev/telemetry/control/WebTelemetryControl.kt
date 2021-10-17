package com.nextgenbroadcast.mobile.middleware.dev.telemetry.control

import com.nextgenbroadcast.mobile.core.dev.IWebInterface
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryControl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect

class WebTelemetryControl(
        private val webServer: IWebInterface
) : ITelemetryControl {

    override suspend fun subscribe(commandFlow: MutableSharedFlow<TelemetryControl>) {
        callbackFlow {
            webServer.addHandler(CONNECTION_PATH) { req, resp ->
                val cmdAction = if (req.pathInfo.length > 1) req.pathInfo.substring(1) else req.pathInfo
                val cmdArgs = req.parameterMap.mapValues { (_, value) ->
                    value.joinToString(ITelemetryControl.CONTROL_ARGUMENT_DELIMITER)
                }

                sendBlocking(TelemetryControl().apply {
                    action = cmdAction
                    arguments = cmdArgs
                })

                resp.writer.apply {
                    println("Receive command: $cmdAction, params: $cmdArgs")
                }
            }

            awaitClose {
                webServer.removeHandler(CONNECTION_PATH)
            }
        }.buffer(Channel.CONFLATED) // To avoid blocking
                .collect { data ->
                    commandFlow.emit(data)
                }
    }

    companion object {
        private const val CONNECTION_PATH = "command"
    }
}