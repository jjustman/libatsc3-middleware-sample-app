package com.nextgenbroadcast.mobile.middleware.rpc.markUnused

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

@JsonRpcType
interface IMarkUnused {
    @JsonRpcMethod("org.atsc.cache.markUnused")
    fun markUnused(@JsonRpcParam("elementUri") elementUri: String): RpcResponse
}