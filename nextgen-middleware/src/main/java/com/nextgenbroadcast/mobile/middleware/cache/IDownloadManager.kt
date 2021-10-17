package com.nextgenbroadcast.mobile.middleware.cache

import kotlinx.coroutines.Job
import java.io.File

interface IDownloadManager {
    fun downloadFile(sourceUrl: String, destFile: File): Job
    fun getLoadingName(file: File): String
}