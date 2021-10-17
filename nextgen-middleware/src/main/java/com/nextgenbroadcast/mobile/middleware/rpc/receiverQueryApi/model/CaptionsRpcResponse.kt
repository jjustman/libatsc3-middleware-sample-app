package com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class CaptionsRpcResponse(
        var ccEnabled: Boolean = false
) : RpcResponse()