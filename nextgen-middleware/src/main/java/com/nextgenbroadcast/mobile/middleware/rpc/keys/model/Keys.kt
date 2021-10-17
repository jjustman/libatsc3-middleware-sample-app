package com.nextgenbroadcast.mobile.middleware.rpc.keys.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class Keys(
        var accepted: List<String>? = null
) : RpcResponse()