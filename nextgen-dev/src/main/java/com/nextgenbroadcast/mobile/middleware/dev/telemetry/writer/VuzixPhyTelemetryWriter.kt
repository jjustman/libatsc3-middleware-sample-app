package com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.BatteryDataParcel
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.RfPhyStatisticsParcel
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.BatteryData
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.RfPhyData
import com.vuzix.connectivity.sdk.Connectivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull


class VuzixPhyTelemetryWriter(
    context: Context,
    private val deviceId: String
) : ITelemetryWriter {
    private val connectivity = Connectivity.get(context)

    override fun open() {
    }

    override fun close() {
    }

    override suspend fun write(eventFlow: Flow<TelemetryEvent>) {
        // drop if Vuzix Connectivity framework is not available or you are not linked to a remote device
        if (!connectivity.isAvailable || !connectivity.isLinked) return

        eventFlow.mapNotNull { event ->
            when (event.topic) {
                TelemetryEvent.EVENT_TOPIC_PHY -> {
                    (event.payload as? RfPhyData)?.let {
                        RfPhyStatisticsParcel(it.stat)
                    }
                }

                TelemetryEvent.EVENT_TOPIC_BATTERY -> {
                    (event.payload as? BatteryData)?.let {
                        BatteryDataParcel(it.level)
                    }
                }

                else -> null
            }?.let { payload ->
                Bundle().apply {
                    putString(EXTRA_DEVICE_ID, deviceId)
                    putString(EXTRA_TYPE, event.topic)
                    putLong(EXTRA_TIMESTAMP, event.payload.timeStamp)
                    putParcelable(EXTRA_PAYLOAD, payload)
                }
            }
        }.collect { extras ->
            try {
                connectivity.sendBroadcast(Intent(ACTION).apply {
                    setPackage(PACKAGE)
                    replaceExtras(extras)
                })
            } catch (e: Exception) {
                LOG.e(TAG, "Failed to publish with Vuzix connectivity", e)
            }
        }
    }

    companion object {
        val TAG: String = VuzixPhyTelemetryWriter::class.java.simpleName

        private const val PACKAGE = "com.nextgen.vuzixmonitor"
        private const val ACTION = "$PACKAGE.action.TELEMETRY"

        private const val EXTRA_DEVICE_ID = "extra_device_id"
        private const val EXTRA_TYPE = "extra_type"
        private const val EXTRA_TIMESTAMP = "extra_timestamp"
        private const val EXTRA_PAYLOAD = "extra_payload"
    }
}