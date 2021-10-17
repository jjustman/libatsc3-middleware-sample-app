package com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class ContentRecoveryState(
        var audioWatermark: Int? = null,
        var videoWatermark: Int? = null,
        var audioFingerprint: Int? = null,
        var videoFingerprint: Int? = null
) : RpcResponse()