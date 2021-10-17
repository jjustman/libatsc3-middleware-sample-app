package com.nextgenbroadcast.mobile.middleware.cache

interface IApplicationCache {
    fun requestFiles(appContextId: String, rootPath: String?, baseUrl: String?, paths: List<String>, filters: List<String>?): Boolean
    fun prefetchFiles(urls: List<String>)
    fun clearCache(appContextId: String)
}