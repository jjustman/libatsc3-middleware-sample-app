package com.nextgenbroadcast.mobile.middleware.gateway.rpc

import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.middleware.rpc.notification.NotificationType
import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model.AlertingSignalingRpcResponse
import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model.ServiceGuideUrlsRpcResponse
import com.nextgenbroadcast.mobile.middleware.server.ws.MiddlewareWebSocket

interface IRPCGateway {
    val deviceId: String
    val advertisingId: String
    val language: String
    val queryServiceId: String?
    val mediaUrl: String?
    val currentAppBaseUrl: String?
    val playbackState: PlaybackState

    fun onSocketOpened(socket: MiddlewareWebSocket)
    fun onSocketClosed(socket: MiddlewareWebSocket)

    fun updateRMPPosition(scaleFactor: Double, xPos: Double, yPos: Double)

    fun requestMediaPlay(mediaUrl: String? = null, delay: Long)
    fun requestMediaStop(delay: Long)

    fun subscribeNotifications(notifications: Set<NotificationType>): Set<NotificationType>
    fun unsubscribeNotifications(notifications: Set<NotificationType>): Set<NotificationType>

    fun sendNotification(message: String)

    fun requestFileCache(baseUrl: String?, rootPath: String?, paths: List<String>, filters: List<String>?): Boolean

    fun getServiceGuideUrls(service: String?): List<ServiceGuideUrlsRpcResponse.Url>

    fun requestServiceChange(globalServiceId: String): Boolean

    fun getAlertChangingData(alertingTypes: List<String>): List<AlertingSignalingRpcResponse.Alert>
}