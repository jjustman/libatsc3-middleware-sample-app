package com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.model

import com.nextgenbroadcast.mobile.middleware.rpc.RpcResponse

data class RecoveredComponentInfo(
    var component: MutableList<Component?>? = null
) : RpcResponse() {
    data class Component(
        var mediaType: String? = null,
        var componentID: String? = null,
        var descriptor: String? = null
    )
}