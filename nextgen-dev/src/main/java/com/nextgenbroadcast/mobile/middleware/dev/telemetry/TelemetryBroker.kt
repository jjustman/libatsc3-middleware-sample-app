package com.nextgenbroadcast.mobile.middleware.dev.telemetry

import androidx.annotation.MainThread
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.ITelemetryReader
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.task.ITelemetryTask
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer.ITelemetryWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

class TelemetryBroker(
        private val readers: List<ITelemetryReader>,
        _writers: List<ITelemetryWriter>,
        enableReaders: Boolean
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val writers = mutableListOf(*_writers.toTypedArray())
    private val eventFlow = MutableSharedFlow<TelemetryEvent>(
            replay = 0,
            extraBufferCapacity = 30,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val testEventFlow = eventFlow.onEach { event ->
        event.payload.testCase = testCase
    }
    private val readerJobs = ConcurrentHashMap<ITelemetryReader, Job>()
    private val writerJobs = ConcurrentHashMap<Class<ITelemetryWriter>, Job>()

    private var isStarted = false

    var testCase: String? = null

    private val _readersEnabled: MutableStateFlow<Map<String, Boolean>> = MutableStateFlow(
            readers.map { it.name to enableReaders }.toMap()
    )
    private val _readersDelay: MutableStateFlow<Map<String, Long>> = MutableStateFlow(
            readers.map { it.name to it.delayMils }.toMap()
    )

    val readerNames: List<String>
        get() = readers.map { reader ->
            reader.name
        }

    val readersEnabled = _readersEnabled.asStateFlow()
    val readersDelay = _readersDelay.asStateFlow()

    fun close() {
        stop()
    }

    private fun start() {
        if (isStarted) return

        try {
            val map = _readersEnabled.value

            readers.forEach { reader ->
                if (map[reader.name] == true) {
                    coroutineScope.launchReader(reader)
                }
            }

            writers.forEach { writer ->
                if (!writerJobs.containsKey(writer::class.java)) {
                    coroutineScope.launchWriter(writer)
                }
            }
        } catch (e: Exception) {
            LOG.d(TAG, "Telemetry gathering error: ", e)
        }

        isStarted = true
    }

    private fun CoroutineScope.launchReader(reader: ITelemetryReader) {
        readerJobs.put(reader,
                launch {
                    reader.read(eventFlow)
                }.apply {
                    invokeOnCompletion {
                        readerJobs.remove(reader, this)
                    }
                }
        )?.let {
            LOG.e(TAG, "Reader is duplicated: ${reader.name}")
            it.cancel()
        }
    }

    private fun CoroutineScope.launchWriter(writer: ITelemetryWriter) {
        try {
            writer.open()
        } catch (e: Exception) {
            LOG.e(TAG, "Failed to open writer: ${writer::class.java.simpleName}", e)
            return
        }

        writerJobs.put(writer.javaClass,
                launch {
                    writer.write(testEventFlow)
                }.apply {
                    invokeOnCompletion {
                        writerJobs.remove(writer.javaClass, this)

                        try {
                            writer.close()
                        } catch (e: Exception) {
                            LOG.e(TAG, "Failed to close writer: ${writer::class.java.simpleName}", e)
                        }
                    }
                }
        )?.let {
            LOG.e(TAG, "Writer is duplicated: $writer")
            it.cancel()
        }
    }

    private fun stop() {
        readerJobs.values.forEach { job ->
            if (!job.isCancelled) job.cancel()
        }
        readerJobs.clear()

        writers.forEach { writer ->
            try {
                writer.close()
            } catch (e: Exception) {
                LOG.e(TAG, "Can't Close writer: ${writer::class.java.simpleName}", e)
            }
        }

        writerJobs.values.forEach { job ->
            if (!job.isCancelled) job.cancel()
        }
        writerJobs.clear()

        eventFlow.resetReplayCache()

        // order is important
        isStarted = false
    }

    @MainThread
    fun addWriter(writer: ITelemetryWriter, persist: Boolean = true) {
        if (writers.contains(writer) || writerJobs.containsKey(writer.javaClass)) return

        if (persist) {
            writers.add(writer)
        }
        coroutineScope.launchWriter(writer)
    }

    fun removeWriter(clazz: Class<out ITelemetryWriter>) {
        // writer will be closed automatically
        writerJobs.remove(clazz)?.cancel()
    }

    @MainThread
    fun runTask(task: ITelemetryTask) {
        coroutineScope.launchReader(task)
        if (!isStarted) {
            start()
        }
    }

    @MainThread
    fun setReadersEnabled(enabled: Boolean) {
        setReaderEnabled(enabled, readers.map { it.name })
    }

    @MainThread
    fun setReaderEnabled(enabled: Boolean, nameList: List<String>) {
        setReaderEnabled(enabled, *nameList.toTypedArray())
    }

    @MainThread
    fun setReaderEnabled(enabled: Boolean/*TODO:, delayMils: Long*/, vararg names: String) {
        val map = _readersEnabled.value.toMutableMap()
        var changed = false

        names.forEach { name ->
            if (enabled) {
                readers.forEach { reader ->
                    if (reader.name.startsWith(name)) {
                        if (!readerJobs.containsKey(reader)) {
                            if (isStarted) {
                                coroutineScope.launchReader(reader)
                            }
                            map[reader.name] = true
                            changed = true
                        }
                    }
                }
            } else {
                readerJobs.forEach { (reader, job) ->
                    if (reader.name.startsWith(name)) {
                        job.cancel()
                        map[reader.name] = false
                        changed = true
                    }
                }
            }
        }

        if (changed) {
            _readersEnabled.value = map
            if (enabled && !isStarted) {
                start()
            }
            /*
            Don't stop because we could answer on command request

            else if (!enabled && isStarted) {
                // stop broker if all readers switched off
                val dis = map.values.distinct()
                if (dis.size == 1 && !dis.first()) {
                    stop()
                }
            }*/
        }
    }

    @MainThread
    fun setReaderDelay(name: String, delayMils: Long) {
        if (!isStarted || delayMils < 0) return

        readers.forEach { reader ->
            if (reader.name.startsWith(name)) {
                if (reader.delayMils != delayMils) {
                    val wasStarted = readerJobs.containsKey(reader)
                    readerJobs[reader]?.cancel()
                    reader.delayMils = delayMils
                    if (wasStarted) {
                        coroutineScope.launchReader(reader)
                    }
                }
            }
        }
    }

    companion object {
        val TAG: String = TelemetryBroker::class.java.simpleName
    }
}
