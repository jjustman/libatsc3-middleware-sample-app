package com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer

import com.google.gson.Gson
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*
import kotlin.concurrent.schedule

class FileTelemetryWriter(
        private val dir: File,
        private val fileName: String,
        private val lifetime: Int
) : ITelemetryWriter {
    private val gson = Gson()

    private var file: RandomAccessFile? = null
    private var timerTask: TimerTask? = null

    override fun open() {
        if (file != null) throw IllegalStateException("File already opened")

        file = RandomAccessFile(getUniqueFile(), "rw")
    }

    private fun getUniqueFile(): File {
        var i = 1
        var uniqueFile = File(dir, "$fileName.$FILE_EXT")
        while (uniqueFile.exists()) {
            uniqueFile = File(dir, "$fileName($i).$FILE_EXT")
            i++
        }
        return uniqueFile
    }

    override fun close() {
        file?.close()
        file = null
    }

    override suspend fun write(eventFlow: Flow<TelemetryEvent>) {
        supervisorScope {
            if (lifetime > 0) {
                timerTask = Timer().schedule(lifetime * 1000L) {
                    close()
                    this@supervisorScope.cancel()
                }
            }

            eventFlow.onCompletion {
                timerTask?.cancel()
            }.collect { event ->
                try {
                    val line = gson.toJson(event) + "\n"
                    file?.write(line.toByteArray(Charsets.US_ASCII))
                } catch (e: IOException) {
                    LOG.d(TAG, "Can't store telemetry topic: ${event.topic}", e)
                }
            }
        }

    }

    companion object {
        val TAG: String = FileTelemetryWriter::class.java.simpleName

        private const val FILE_EXT = "log"
    }
}