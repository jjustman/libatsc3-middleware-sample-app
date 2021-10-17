package com.nextgenbroadcast.mobile.middleware.dev.telemetry.task

import android.net.wifi.WifiManager
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.flow.MutableSharedFlow

class WiFiInfoTelemetryTask(
        private val wifiManager: WifiManager
) : ITelemetryTask {
    override val name = "WiFiInfoTask"
    override var delayMils = 0L

    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        val connectionInfo = wifiManager.connectionInfo
        val ipAddressStr = getIpv4FromInt(connectionInfo.ipAddress)
        val ssid = connectionInfo.ssid.replace("\"", "", false)

        eventFlow.emit(TelemetryEvent(
                TelemetryEvent.EVENT_TOPIC_WIFI,
                WiFiData(ssid, ipAddressStr, connectionInfo.toString())
        ))
    }

    private fun getIpv4FromInt(ipAddress: Int) =
            String.format("%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
}

private data class WiFiData(
        val name: String,
        val ipv4: String,
        val commonInfo: String
) : TelemetryPayload()