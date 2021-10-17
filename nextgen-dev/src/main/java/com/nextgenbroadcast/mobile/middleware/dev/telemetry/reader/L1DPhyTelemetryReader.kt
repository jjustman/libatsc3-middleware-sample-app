package com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader

import com.nextgenbroadcast.mobile.core.atsc3.INtpClock
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.ReceiverTelemetry
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import org.ngbp.libatsc3.middleware.android.phy.models.L1D_timePhyInformation

class L1DPhyTelemetryReader(
    private val flow: SharedFlow<L1D_timePhyInformation>,
    private val ntpClock: INtpClock?
) : ITelemetryReader {

    override val name = NAME
    override var delayMils: Long = -1

    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        flow.collect { info ->
            val ntpTimestampInMs = ntpClock?.getCurrentNtpTimeMs() ?: -1
            eventFlow.emit(TelemetryEvent(
                TelemetryEvent.EVENT_TOPIC_L1D,
                L1DPhyData(
                    stat = info,
                    anchorNtpTimestamp = ntpTimestampInMs / 1000.0 - info.L1D_unix_ts_calculated
                )
            ))
        }
    }

    companion object {
        const val NAME = ReceiverTelemetry.TELEMETRY_PHY
    }
}

data class L1DPhyData(
    val stat: L1D_timePhyInformation,
    val anchorNtpTimestamp: Double
) : TelemetryPayload()