package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType

data class RmpPlaybackStateChangeNotification (
        var playbackState: PlaybackState? = null
) : RPCNotification(NotificationType.RMP_PLAYBACK_STATE_CHANGE)