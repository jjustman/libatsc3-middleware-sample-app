package com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization

import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcMethod
import com.github.nmuzhichin.jsonrpc.annotation.JsonRpcType
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpMediaTime
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpPlaybackRate
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpPlaybackState
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpWallClockTime

@JsonRpcType
interface IRMPContentSynchronization {
    @JsonRpcMethod("org.atsc.query.rmpMediaTime")
    fun queryRMPMediaTime(): RmpMediaTime

    @JsonRpcMethod("org.atsc.query.rmpMediaTime")
    fun queryRMPWallClock(): RmpWallClockTime

    @JsonRpcMethod("org.atsc.query.rmpPlaybackState")
    fun queryRMPPlaybackState(): RmpPlaybackState

    @JsonRpcMethod("org.atsc.query.rmpPlaybackRate")
    fun queryRMPPlaybackRate(): RmpPlaybackRate
}