package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType

data class RmpMediaTimeChangeNotification(
        var currentTime: String
): RPCNotification(NotificationType.RMP_MEDIA_TIME_CHANGE)