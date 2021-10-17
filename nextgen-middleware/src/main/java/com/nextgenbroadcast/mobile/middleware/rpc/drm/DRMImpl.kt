package com.nextgenbroadcast.mobile.middleware.rpc.drm

import com.nextgenbroadcast.mobile.middleware.rpc.drm.model.DRMOperation
import com.nextgenbroadcast.mobile.middleware.rpc.drm.model.DRMMessage

class DRMImpl : IDRM {
    override fun drmOperation(systemId: String, service: String, message: DRMMessage): DRMOperation {
        return DRMOperation(emptyList())
    }
}