package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

data class CaptionStateChangeNotification (
    var msgType: String? = null,
    var captionDisplay: Boolean = false
)