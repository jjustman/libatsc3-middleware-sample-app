package com.nextgenbroadcast.mobile.middleware.cache

import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.md5
import kotlinx.coroutines.Job
import java.io.File
import java.net.URL

internal class ApplicationCache(
        private val cacheRoot: File,
        private val downloadManager: IDownloadManager
) : IApplicationCache {

    private val cacheMap: HashMap<String, CacheEntry> by lazy {
        hashMapOf()
    }

    @Synchronized
    override fun requestFiles(appContextId: String, rootPath: String?, baseUrl: String?, paths: List<String>, filters: List<String>?): Boolean {
        val cacheEntry = cacheMap.getOrElse(appContextId) {
            val cachePath = getCachePathForAppContextId(appContextId) + (rootPath ?: "")
            CacheEntry(cachePath).also {
                cacheMap[appContextId] = it
            }
        }

        var result = true

        paths.forEach { relativeFilePath ->
            val file = File(cacheEntry.folder, relativeFilePath)

            if (!file.exists()) {
                val loadingFileName = downloadManager.getLoadingName(file)

                if (baseUrl != null) {
                    cacheMap[getPrefetchContextId(baseUrl)]?.takeIf { entry ->
                        entry != cacheEntry
                    }?.let { prefetchEntry ->
                        val prefetchedFile = File(prefetchEntry.folder, relativeFilePath)
                        if (prefetchedFile.exists()) {
                            prefetchedFile.safeCopyTo(file)
                            return@forEach
                        } else {
                            val prefetchLoadingFileName = downloadManager.getLoadingName(prefetchedFile)
                            prefetchEntry.jobMap[prefetchLoadingFileName]?.let { prefetchJob ->
                                result = false

                                if (!cacheEntry.jobMap.containsKey(loadingFileName)) {
                                    cacheEntry.jobMap[loadingFileName] = prefetchJob
                                    prefetchJob.invokeOnCompletion { throwable ->
                                        synchronized(this@ApplicationCache) {
                                            cacheEntry.jobMap.remove(loadingFileName)
                                        }

                                        if (throwable == null) {
                                            prefetchedFile.safeCopyTo(file)
                                        }
                                    }
                                }
                                return@forEach
                            }
                        }
                    }
                }

                result = false

                if (!cacheEntry.jobMap.containsKey(loadingFileName)) {
                    deleteFile(cacheEntry, loadingFileName)

                    if (baseUrl != null) {
                        downloadManager.downloadFile(baseUrl + relativeFilePath, file).also { job ->
                            cacheEntry.jobMap[loadingFileName] = job

                            job.invokeOnCompletion { throwable ->
                                synchronized(this@ApplicationCache) {
                                    cacheEntry.jobMap.remove(loadingFileName)
                                }

                                if (throwable != null) {
                                    deleteFile(cacheEntry, loadingFileName)
                                }
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    override fun prefetchFiles(urls: List<String>) {
        urls.map { url ->
            with(URL(url)) {
                url.substring(0, url.length - path.length) to path
            }
        }.groupBy({ (baseUrl) ->
            baseUrl
        }, { (_, fileName) ->
            fileName
        }).forEach { (baseUrl, paths) ->
            requestFiles(getPrefetchContextId(baseUrl), null, baseUrl, paths, null)
        }
    }

    @Synchronized
    override fun clearCache(appContextId: String) {
        cacheMap[appContextId]?.let { cacheEntry ->
            cacheEntry.jobMap.values.forEach { job ->
                job.cancel()
            }

            cacheEntry.folder.listFiles()?.forEach { file ->
                file.delete()
            }
            cacheEntry.folder.delete()

            cacheMap.remove(appContextId)
        }
    }

    private fun getPrefetchContextId(baseUrl: String): String {
        return "$PREFETCH_CONTEXT_ID-${baseUrl.md5()}"
    }

    private fun deleteFile(cacheEntry: CacheEntry, fileName: String) {
        File(cacheEntry.folder, fileName).takeIf {
            it.exists()
        }?.delete()
    }

    private fun getCachePathForAppContextId(appContextId: String): String {
        return "${cacheRoot.absolutePath}/${appContextId.md5()}/"
    }

    private fun File.safeCopyTo(target: File): File? {
        try {
            return copyTo(target, overwrite = true)
        } catch (e: Exception) {
            LOG.d(TAG, "Failed to copy file $this to $target", e)
        }
        return null
    }

    private class CacheEntry(
        cachePath: String
    ) {
        val folder = File(cachePath)
        val jobMap = HashMap<String, Job>()
    }

    companion object {
        val TAG: String = ApplicationCache::class.java.simpleName

        private const val PREFETCH_CONTEXT_ID = "prefetch"
    }
}