package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType

data class ServiceChangeNotification (
        var service: String? = null
): RPCNotification(NotificationType.SERVICE_CHANGE)