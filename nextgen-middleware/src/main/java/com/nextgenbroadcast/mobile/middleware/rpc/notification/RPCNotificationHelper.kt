package com.nextgenbroadcast.mobile.middleware.rpc.notification

import com.github.nmuzhichin.jsonrpc.model.request.Notification
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.middleware.rpc.notification.model.*
import com.nextgenbroadcast.mobile.middleware.rpc.processor.RPCObjectMapper
import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model.AlertingSignalingRpcResponse
import com.nextgenbroadcast.mobile.middleware.rpc.receiverQueryApi.model.ServiceGuideUrlsRpcResponse

class RPCNotificationHelper(
        private val sendNotification: (message: String) -> Unit
) {

    fun notifyServiceChange(serviceId: String) {
        sendNotification(ServiceChangeNotification(serviceId))
    }

    fun notifyContentChange(files: List<String>) {
        sendNotification(ContentChangeNotification(files))
    }

    fun notifyServiceGuideChange(urlList: List<ServiceGuideUrlsRpcResponse.Url>) {
        sendNotification(ServiceGuideChangeNotification(urlList))
    }

    fun notifyRmpPlaybackStateChange(playbackState: PlaybackState) {
        sendNotification(RmpPlaybackStateChangeNotification(playbackState))
    }

    fun notifyRmpMediaTimeChange(currentTime: Long) {
        if (currentTime <= 0) return

        val seconds = currentTime.toDouble() / 1000
        sendNotification(RmpMediaTimeChangeNotification(String.format("%.3f", seconds)))
    }

    fun notifyRmpPlaybackRateChange(playbackRate: Float) {
        sendNotification(RmpPlaybackRateChangeNotification(playbackRate))
    }

    fun notifyAlertingChange(alertList: List<AlertingSignalingRpcResponse.Alert>) {
        sendNotification(AlertingChangeNotification(alertList))
    }

    private fun sendNotification(rpcNotification: RPCNotification) {
        val rpcObjectMapper = RPCObjectMapper()
        val params: Map<String, Any> = rpcObjectMapper.objectToMap(rpcNotification)
        val notification = Notification(NOTIFICATION_METHOD_NAME, params)
        val message = rpcObjectMapper.objectToJson(notification)

        sendNotification(message)
    }

    companion object {
        private const val NOTIFICATION_METHOD_NAME = "org.atsc.notify"
    }
}