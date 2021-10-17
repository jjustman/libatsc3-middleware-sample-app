package com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.model.CacheRequest
import com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.model.QueryCacheUsage

@JsonRpcType
interface ICacheRequest {
    @JsonRpcMethod("org.atsc.CacheRequest")
    fun cacheRequest(
            @JsonRpcParam("sourceURL", nullable = true) sourceURL: String?,
            @JsonRpcParam("targetURL", nullable = true) targetURL: String?,
            @JsonRpcParam("URLs") URLs: List<String>,
            @JsonRpcParam("filters", nullable = true) filters : List<String>?
    ): CacheRequest

    @JsonRpcMethod("org.atsc.CacheRequestDASH")
    fun CacheRequestDASH(): CacheRequest

    @JsonRpcMethod("org.atsc.cacheUsage")
    fun queryCacheUsage(): QueryCacheUsage
}