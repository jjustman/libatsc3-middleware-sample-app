package com.nextgenbroadcast.mobile.middleware.rpc.xLink

import com.nextgenbroadcast.mobile.middleware.rpc.RpcException
import com.nextgenbroadcast.mobile.middleware.rpc.xLink.model.XlinkResolutionRpcResponse

class XLinkImpl : IXLink {
    override fun xLinkResolved(xlink: String, mpdURL: String?, period: String?): XlinkResolutionRpcResponse {
        throw RpcException()
        //return XlinkResolutionRpcResponse()
    }
}