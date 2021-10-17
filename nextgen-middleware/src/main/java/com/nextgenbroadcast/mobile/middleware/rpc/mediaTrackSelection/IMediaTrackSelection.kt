package com.nextgenbroadcast.mobile.middleware.rpc.mediaTrackSelection

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

@JsonRpcType
interface IMediaTrackSelection {
    @JsonRpcMethod("org.atsc.track.selection")
    fun mediaTrackSelection(@JsonRpcParam("selectionId") selectionId: Int): RpcResponse
}