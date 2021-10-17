package com.nextgenbroadcast.mobile.middleware.cache

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val DOWNLOAD_IO = Executors.newCachedThreadPool().asCoroutineDispatcher()

class DownloadManager : IDownloadManager {
    private val client: OkHttpClient by lazy {
        OkHttpClient()
    }

    override fun downloadFile(sourceUrl: String, destFile: File): Job {
        val loadingFile = File(destFile.parentFile, getLoadingName(destFile))

        return CoroutineScope(DOWNLOAD_IO).launch {
            val request = Request.Builder()
                    .url(sourceUrl)
                    .build()

            try {
                suspendCancellableCoroutine<File> { cont ->
                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            cont.resumeWithException(e)
                        }

                        override fun onResponse(call: Call, response: Response) {
                            if (!response.isSuccessful) {
                                cont.resumeWithException(IOException("Unexpected HTTP code: ${response.code}"))
                                return
                            }

                            try {
                                response.body?.source()?.use { source ->
                                    val saved = saveToFile(source, loadingFile, cont::isActive)
                                    if (saved) {
                                        loadingFile.renameTo(destFile)
                                        cont.resume(loadingFile)
                                    } else {
                                        cont.resumeWithException(IOException("Download cancelled"))
                                    }
                                } ?: let {
                                    cont.resumeWithException(IllegalStateException("Body is null"))
                                }
                            } catch (e: Exception) {
                                cont.resumeWithException(e)
                            }
                        }
                    })
                }
            } catch (e: IOException) {
                Log.d(TAG, "Error on file download: $sourceUrl", e)
            }
        }
    }

    private fun saveToFile(source: BufferedSource, destFile: File, isActive: () -> Boolean): Boolean {
        if (!destFile.exists()) {
            destFile.parentFile?.mkdirs()
        }

        var finished = false
        destFile.sink().use { sink ->
            val buffer = Buffer()
            var totalBytesWritten: Long = 0
            while (isActive()) {
                val read = source.read(buffer, SEGMENT_SIZE)
                if (read == -1L) {
                    finished = true
                    break
                }

                val emitByteCount = buffer.completeSegmentByteCount()
                if (emitByteCount > 0L) {
                    totalBytesWritten += emitByteCount
                    sink.write(buffer, emitByteCount)
                }
            }
            if (buffer.size > 0L) {
                totalBytesWritten += buffer.size
                sink.write(buffer, buffer.size)
            }
        }

        return finished
    }

    override fun getLoadingName(file: File) = file.name + LOADING_POSTFIX

    companion object {
        val TAG: String = DownloadManager::class.java.simpleName

        private const val SEGMENT_SIZE = 8 * 1024L
        private const val LOADING_POSTFIX = "_loading"
    }
}