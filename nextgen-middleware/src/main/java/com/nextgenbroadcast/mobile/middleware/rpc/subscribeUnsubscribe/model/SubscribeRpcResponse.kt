package com.nextgenbroadcast.mobile.middleware.rpc.subscribeUnsubscribe.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SubscribeRpcResponse(
        var msgType: List<String>? = null
) : RpcResponse()