package com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class AudioAccessibilityPrefRpcResponse(
    var videoDescriptionService: VideoDescriptionService? = null,
    var audioEIService: AudioEIService? = null
) : RpcResponse() {
    data class VideoDescriptionService(
        var enabled: Boolean = false,
        var language: String? = null
    )

    data class AudioEIService(
        var enabled: Boolean = false,
        var language: String? = null
    )

}