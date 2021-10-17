package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model.CaptionDisplayRpcResponse

data class CaptionDisplayPreferencesChangeNotification (
    var msgType: String? = null,
    var cta708: CaptionDisplayRpcResponse.cta708Data? = null,
    var imsc1: CaptionDisplayRpcResponse.imsc1Data? = null
)