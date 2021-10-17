package com.nextgenbroadcast.mobile.middleware.rpc.notification.model

import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType

open class RPCNotification(notificationType: NotificationType) {
    private val msgType: String = notificationType.value
}