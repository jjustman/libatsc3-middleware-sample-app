package com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest

import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.model.CacheRequest
import com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.model.QueryCacheUsage

class CacheRequestImpl(
        private val gateway: IRPCGateway
) : ICacheRequest {

    override fun cacheRequest(sourceURL: String?, targetURL: String?, URLs: List<String>, filters: List<String>?): CacheRequest {
        val cached = gateway.requestFileCache(sourceURL, targetURL, URLs, filters)
        return CacheRequest(cached)
    }

    override fun CacheRequestDASH(): CacheRequest {
        return CacheRequest()
    }

    override fun queryCacheUsage(): QueryCacheUsage {
        return QueryCacheUsage()
    }
}