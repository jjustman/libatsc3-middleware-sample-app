package com.nextgenbroadcast.mobile.middleware.rpc.requestReceiverActions.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class AudioVolume(
        var audioVolume: Double? = null
) : RpcResponse()