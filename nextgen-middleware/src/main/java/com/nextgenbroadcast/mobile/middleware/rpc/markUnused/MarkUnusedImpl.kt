package com.nextgenbroadcast.mobile.middleware.rpc.markUnused

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

class MarkUnusedImpl : IMarkUnused {
    override fun markUnused(elementUri: String): RpcResponse {
        return RpcResponse()
    }
}