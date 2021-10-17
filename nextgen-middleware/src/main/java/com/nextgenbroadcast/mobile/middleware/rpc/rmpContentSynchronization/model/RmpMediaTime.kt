package com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class RmpMediaTime(
        var currentTime: String? = null,
        var startDate: String? = null
) : RpcResponse()