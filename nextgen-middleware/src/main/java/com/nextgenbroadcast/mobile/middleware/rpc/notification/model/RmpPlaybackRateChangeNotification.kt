package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType

data class RmpPlaybackRateChangeNotification(
        var playbackRate: Float? = null
): RPCNotification(NotificationType.RMP_PLAYBACK_RATE_CHANGE)