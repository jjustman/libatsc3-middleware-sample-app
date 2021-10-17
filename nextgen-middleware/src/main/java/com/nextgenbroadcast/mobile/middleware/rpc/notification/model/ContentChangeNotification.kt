package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType

data class ContentChangeNotification (
    val packageList: List<String>
): RPCNotification(NotificationType.CONTENT_CHANGE)