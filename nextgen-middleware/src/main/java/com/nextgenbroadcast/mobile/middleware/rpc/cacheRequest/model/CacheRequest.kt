package com.nextgenbroadcast.mobile.middleware.rpc.cacheRequest.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class CacheRequest(
        var cached: Boolean = false
) : RpcResponse()