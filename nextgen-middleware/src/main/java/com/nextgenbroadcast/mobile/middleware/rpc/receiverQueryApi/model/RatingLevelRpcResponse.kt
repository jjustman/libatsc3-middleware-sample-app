package com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class RatingLevelRpcResponse(
        var rating: String? = null,
        var contentRating: String? = null,
        var blocked: Boolean = false
) : RpcResponse()