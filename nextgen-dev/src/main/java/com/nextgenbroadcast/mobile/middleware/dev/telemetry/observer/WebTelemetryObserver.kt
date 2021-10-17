package com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.supervisorScope
import okio.ByteString.Companion.toByteString
import org.eclipse.jetty.client.HttpClient
import org.eclipse.jetty.client.api.Response
import org.eclipse.jetty.client.api.Result
import java.net.ConnectException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

class WebTelemetryObserver(
        host: String,
        port: Int,
        topics: List<String>
): ITelemetryObserver {
    private val clientUrl = "http://$host:$port/$CONNECTION_PATH/${topics.joinToString("/")}"
    private val gson = Gson()

    private val httpClient by lazy {
        HttpClient()
    }

    override suspend fun read(eventFlow: MutableSharedFlow<ClientTelemetryEvent>) {
        delay(3000) // wait for web client

        if (!httpClient.isRunning) {
            httpClient.start()
        }

        try {
            supervisorScope {
                var retryCount = MAX_RETRY_COUNT
                while (isActive) {
                    retryCount--

                    try {
                        collectEvents(eventFlow)
                        retryCount = MAX_RETRY_COUNT // restore retry count after successful connection
                    } catch (e: Exception) {
                        LOG.i(TAG, "Telemetry observation finished with exception", e)
                    }

                    if (retryCount <= 0) break

                    if (isActive) delay(1000)
                }
            }
        } catch (e: Exception) {
            LOG.i(TAG, "Closing telemetry observer", e)
        }

        httpClient.stop()
    }

    private suspend fun collectEvents(eventFlow: MutableSharedFlow<ClientTelemetryEvent>) {
        callbackFlow<ClientTelemetryEvent> {
            val request = httpClient.newRequest(clientUrl)
            var firstSkipped = false
            request.send(object : Response.Listener {
                override fun onContent(response: Response?, content: ByteBuffer) {
                    // skip welcome words
                    if (firstSkipped) {
                        val json = content.toByteString().utf8()
                        try {
                            val event = gson.fromJson<ClientTelemetryEvent>(json, object : TypeToken<ClientTelemetryEvent>() {}.type)
                            sendBlocking(event)
                        } catch (e: Exception) {
                            LOG.e(TAG, "Error on receiving event $json", e)
                        }
                    }
                    firstSkipped = true
                }

                override fun onFailure(response: Response?, failure: Throwable?) {
                    if (failure !is ClosedChannelException) {
                        LOG.e(TAG, "Failed receiving telemetry from $clientUrl", failure)
                    }
                }

                override fun onComplete(result: Result?) {
                    close(ConnectException())
                }
            })

            awaitClose {
                request.abort(ClosedChannelException())
            }
        }.buffer(Channel.CONFLATED) // To avoid send blocking
                .collect { event ->
                    eventFlow.emit(event)
                }
    }

    companion object {
        val TAG: String = WebTelemetryObserver::class.java.simpleName

        private const val CONNECTION_PATH = "telemetry"
        private const val MAX_RETRY_COUNT = 5
    }
}