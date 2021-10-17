package com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.model.ContentRecoveryState
import com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.model.RecoveredComponentInfo

@JsonRpcType
interface IContentRecovery {
    @JsonRpcMethod("org.atsc.query.contentRecoveryState")
    fun queryContentRecoveryState(): ContentRecoveryState

    @JsonRpcMethod("org.atsc.query.recoveredComponentInfo")
    fun queryRecoveredComponentInfo(): RecoveredComponentInfo
}