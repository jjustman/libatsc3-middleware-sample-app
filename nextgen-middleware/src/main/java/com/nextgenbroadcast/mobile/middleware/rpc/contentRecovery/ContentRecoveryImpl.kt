package com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery

import com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.model.ContentRecoveryState
import com.nextgenbroadcast.mobile.middleware.rpc.contentRecovery.model.RecoveredComponentInfo

class ContentRecoveryImpl : IContentRecovery {
    override fun queryContentRecoveryState(): ContentRecoveryState {
        return ContentRecoveryState()
    }

    override fun queryRecoveredComponentInfo(): RecoveredComponentInfo {
        return RecoveredComponentInfo()
    }
}