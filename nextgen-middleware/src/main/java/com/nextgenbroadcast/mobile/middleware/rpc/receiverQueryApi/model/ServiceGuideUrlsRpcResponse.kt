package com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class ServiceGuideUrlsRpcResponse(
        var urlList: List<Url>? = null
) : RpcResponse() {

    data class Url(
            val sgType: String,
            val sgUrl: String,
            val service: String?,
            val content: String?
    )

}