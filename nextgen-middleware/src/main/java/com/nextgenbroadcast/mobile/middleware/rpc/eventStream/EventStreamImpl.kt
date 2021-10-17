package com.nextgenbroadcast.mobile.middleware.rpc.eventStream

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

class EventStreamImpl : IEventStream {
    override fun eventStreamSubscribe(schemeIdUri: String, value: String?): RpcResponse {
        return RpcResponse()
    }

    override fun eventStreamUnsubscribe(schemeIdUri: String, value: String?): RpcResponse {
        return RpcResponse()
    }
}