package com.nextgenbroadcast.mobile.middleware.rpc.xLink

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.xLink.model.XlinkResolutionRpcResponse

@JsonRpcType
interface IXLink {
    @JsonRpcMethod("org.atsc.xlinkResolution")
    fun xLinkResolved(
        @JsonRpcParam("xlink") xlink: String,
        @JsonRpcParam("mpdURL", nullable = true) mpdURL: String? = null,
        @JsonRpcParam("period", nullable = true) period: String? = null
    ): XlinkResolutionRpcResponse
}