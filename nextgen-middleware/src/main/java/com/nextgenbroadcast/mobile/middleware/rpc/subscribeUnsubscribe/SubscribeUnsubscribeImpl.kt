package com.nextgenbroadcast.mobile.middleware.rpc.subscribeUnsubscribe

import com.nextgenbroadcast.mobile.middleware.gateway.rpc.IRPCGateway
import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType
import com.nextgenbroadcast.mobile.middleware.rpc.subscribeUnsubscribe.model.SubscribeRpcResponse

class SubscribeUnsubscribeImpl(
        private val gateway: IRPCGateway
) : ISubscribeUnsubscribe {

    override fun integratedSubscribe(msgType: List<String>): SubscribeRpcResponse {
        val notifications = convertMsgTypeToNotifications(msgType)
        val subscribedNotifications = gateway.subscribeNotifications(notifications)

        return SubscribeRpcResponse(subscribedNotifications.map { it.value })
    }

    override fun integratedUnsubscribe(msgType: List<String>): SubscribeRpcResponse {
        val notifications = convertMsgTypeToNotifications(msgType)
        val unsubscribedNotifications = gateway.unsubscribeNotifications(notifications)

        return SubscribeRpcResponse(unsubscribedNotifications.map { it.value })
    }

    private fun convertMsgTypeToNotifications(msgType: List<String>): Set<NotificationType> {
        return when {
            msgType.isEmpty() -> emptySet()
            msgType.first() == "All" -> NotificationType.values().toSet()
            else -> NotificationType.values().filter{ msgType.contains(it.value) }.toSet()
        }
    }
}