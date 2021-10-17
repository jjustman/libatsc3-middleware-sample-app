package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model.AudioAccessibilityPrefRpcResponse

data class AudioAccessibilityPreferenceChangeNotification (
    var msgType: String? = null,
    var videoDescriptionService: AudioAccessibilityPrefRpcResponse.VideoDescriptionService? = null
)