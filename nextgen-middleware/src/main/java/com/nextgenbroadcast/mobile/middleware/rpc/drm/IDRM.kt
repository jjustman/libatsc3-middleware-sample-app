package com.nextgenbroadcast.mobile.middleware.rpc.drm

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcParam
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.drm.model.DRMOperation
import com.nextgenbroadcast.mobile.middleware.rpc.drm.model.DRMMessage

@JsonRpcType
interface IDRM {
    @JsonRpcMethod("org.atsc.drmOperation")
    fun drmOperation(
        @JsonRpcParam("systemId") systemId: String,
        @JsonRpcParam("service") service: String,
        @JsonRpcParam("message") message: DRMMessage
    ): DRMOperation
}