package com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.ReceiverTelemetry
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.TimeUnit

class BatteryTelemetryReader(
        context: Context
) : ITelemetryReader {
    private val appContext = context.applicationContext
    private val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

    override val name = NAME
    override var delayMils: Long = BATTERY_MEASURING_FREQUENCY

    override suspend fun read(eventFlow: MutableSharedFlow<TelemetryEvent>) {
        supervisorScope {
            while (isActive) {
                appContext.registerReceiver(null, intentFilter)?.let { batteryStatus ->
                    val level: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = level * 100 / scale.toFloat()

                    eventFlow.emit(TelemetryEvent(
                            TelemetryEvent.EVENT_TOPIC_BATTERY,
                            BatteryData(batteryPct)
                    ))
                }

                delay(delayMils)
            }
        }
    }

    companion object {
        const val NAME = ReceiverTelemetry.TELEMETRY_BATTERY

        val BATTERY_MEASURING_FREQUENCY = TimeUnit.MINUTES.toMillis(1)
    }
}

data class BatteryData(
        val level: Float
) : TelemetryPayload()