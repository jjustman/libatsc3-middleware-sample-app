package com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class CaptionDisplayRpcResponse(
    var msgType: String? = null,
    var cta708: cta708Data? = null,
    var imsc1: imsc1Data? = null
) : RpcResponse() {
    data class cta708Data(
        var characterColor: String? = null,
        var characterOpacity: Double? = null,
        var characterSize: Int? = null,
        var fontStyle: String? = null,
        var backgroundColor: String? = null,
        var backgroundOpacity: Int? = null,
        var characterEdge: String? = null,
        var characterEdgeColor: String? = null,
        var windowColor: String? = null,
        var windowOpacity: Int? = null
    )

    data class imsc1Data(
        var region_textAlign: String? = null,
        var content_fontWeight: String? = null
    )
}