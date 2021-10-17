package com.nextgenbroadcast.mobile.middleware.rpc.eventStream

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

@JsonRpcType
interface IEventStream {
    @JsonRpcMethod("org.atsc.eventStream.subscribe")
    fun eventStreamSubscribe(
        @JsonRpcParam("schemeIdUri") schemeIdUri: String,
        @JsonRpcParam("value", nullable = true) value: String?
    ): RpcResponse

    @JsonRpcMethod("org.atsc.eventStream.unsubscribe")
    fun eventStreamUnsubscribe(
        @JsonRpcParam("schemeIdUri") schemeIdUri: String,
        @JsonRpcParam("value", nullable = true) value: String?
    ): RpcResponse

    //TODO: org.atsc.eventStream.event
}