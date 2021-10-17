package com.nextgenbroadcast.mobile.middleware.rpc.xLink.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class XlinkResolutionRpcResponse(
    var xlink: String,
    var disposition: Disposition,
    var timing: Timing
): RpcResponse() {
    data class Timing(
        var currentPosition: Double,
        var periodStart: String,
        var duration: Double
    )

    data class Disposition(
        var code: Int,
        var description: String? = null
    )
}