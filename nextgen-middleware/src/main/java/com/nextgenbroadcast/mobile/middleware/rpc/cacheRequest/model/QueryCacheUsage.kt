package com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class QueryCacheUsage(
        var usageSize: Int? = null,
        var quotaSize: Int? = null
) : RpcResponse()
