package com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization

import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpMediaTime
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpPlaybackRate
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpPlaybackState
import com.nextgenbroadcast.mobile.middleware.rpc.rmpContentSynchronization.model.RmpWallClockTime

class RMPContentSynchronizationImpl(
        private val gateway: IRPCGateway
) : IRMPContentSynchronization {
    override fun queryRMPMediaTime(): RmpMediaTime {
        return RmpMediaTime()
    }

    override fun queryRMPWallClock(): RmpWallClockTime {
        return RmpWallClockTime()
    }

    override fun queryRMPPlaybackState(): RmpPlaybackState {
        return RmpPlaybackState(gateway.playbackState.state)
    }

    override fun queryRMPPlaybackRate(): RmpPlaybackRate {
        return RmpPlaybackRate()
    }
}