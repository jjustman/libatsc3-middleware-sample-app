package com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer

import com.google.gson.Gson
import com.nextgenbroadcast.mobile.core.dev.IWebInterface
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import java.util.concurrent.ConcurrentHashMap
import javax.servlet.AsyncEvent
import javax.servlet.AsyncListener
import javax.servlet.http.HttpServletRequest

class WebTelemetryWriter(
        private val webServer: IWebInterface
) : ITelemetryWriter {

    private val gson = Gson()
    private val flow = MutableSharedFlow<TelemetryEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val jobs = ConcurrentHashMap<HttpServletRequest, Job>()

    override fun open() {
        webServer.addHandler(CONNECTION_PATH) { req, resp ->
            val filter: List<String> = req.pathInfo
                    .trim('/')
                    .split("/")
                    .filter { it.isNotEmpty() }

            req.startAsync().apply {
                timeout = 0 // infinite timeout
                addListener(object : AsyncListener {
                    override fun onComplete(event: AsyncEvent) {
                        cancelJob(req)
                    }

                    override fun onTimeout(event: AsyncEvent) {
                        cancelJob(req)
                    }

                    override fun onError(event: AsyncEvent) {
                        cancelJob(req)
                    }

                    override fun onStartAsync(event: AsyncEvent) {
                        // ignore
                    }
                })
            }

            resp.writer.println("Event logging started...")
            if (filter.isNotEmpty()) {
                resp.writer.println("filters: $filter")
            }
            resp.writer.flush()

            jobs[req] = CoroutineScope(Dispatchers.IO).launch {
                flow.filter { event ->
                    filter.isEmpty() || filter.contains(event.topic)
                }.collect { event ->
                    resp.writer.apply {
                        println(gson.toJson(event))
                        resp.writer.flush()
                    }
                }
            }
        }
    }

    private fun cancelJob(req: HttpServletRequest) {
        jobs.remove(req)?.cancel()
    }

    override fun close() {
        webServer.removeHandler(CONNECTION_PATH)
    }

    override suspend fun write(eventFlow: Flow<TelemetryEvent>) {
        eventFlow.collect {
            flow.emit(it)
        }
    }

    companion object {
        private const val CONNECTION_PATH = "telemetry"
    }
}