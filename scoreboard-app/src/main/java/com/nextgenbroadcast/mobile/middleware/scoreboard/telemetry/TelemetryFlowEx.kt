package com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryPayload
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.*
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.TDataPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import java.text.SimpleDateFormat
import java.util.*
import kotlin.jvm.Throws

const val COMMAND_DATE_FORMAT = "HH:mm:ss.SSS"

private val gson = Gson()

private val phyPayloadType = object : TypeToken<RfPhyData>() {}.type
private val batteryPayloadType = object : TypeToken<BatteryData>() {}.type
private val locationPayloadType = object : TypeToken<LocationData>() {}.type
private val errorPayloadType = object : TypeToken<ErrorData>() {}.type

private val dateFormat = SimpleDateFormat(COMMAND_DATE_FORMAT, Locale.US)

fun ClientTelemetryEvent.getClientId(): String? {
    val startIndex = topic.indexOf("_") + 1
    val endIndex = topic.indexOf("/", startIndex)
    return if (startIndex in 1 until endIndex) {
        topic.substring(startIndex, endIndex)
    } else null
}

fun ClientTelemetryEvent.getEventTopic(): String {
    val index = topic.lastIndexOf("/")
    return if (index > 0) {
        topic.substring(index + 1)
    } else topic
}

@Throws(JsonSyntaxException::class)
fun ClientTelemetryEvent.toPhyEvent(eventTopic: String = getEventTopic()): TelemetryEvent {
    return TelemetryEvent(eventTopic, gson.fromJson<RfPhyData>(payload, phyPayloadType))
}

@Throws(JsonSyntaxException::class)
fun ClientTelemetryEvent.toBatteryEvent(eventTopic: String = getEventTopic()): TelemetryEvent {
    return TelemetryEvent(eventTopic, gson.fromJson<BatteryData>(payload, batteryPayloadType))
}

@Throws(JsonSyntaxException::class)
fun ClientTelemetryEvent.toLocationEvent(eventTopic: String = getEventTopic()): TelemetryEvent {
    return TelemetryEvent(eventTopic, gson.fromJson<LocationData>(payload, locationPayloadType))
}

@Throws(JsonSyntaxException::class)
fun ClientTelemetryEvent.toErrorEvent(eventTopic: String = getEventTopic()): TelemetryEvent {
    return TelemetryEvent(eventTopic, gson.fromJson<ErrorData>(payload, errorPayloadType))
}

fun Flow<ClientTelemetryEvent>.mapToEvent(filter: String? = null): Flow<TelemetryEvent> = mapNotNull { event ->
    try {
        val eventTopic = event.getEventTopic()
        if (filter == null || filter == eventTopic) {
            when (eventTopic) {
                TelemetryEvent.EVENT_TOPIC_PHY -> event.toPhyEvent(eventTopic)
                TelemetryEvent.EVENT_TOPIC_BATTERY -> event.toBatteryEvent(eventTopic)
                else -> null
            }
        } else null
    } catch (e: Exception) {
        LOG.w("Flow.mapToDataPoint", "Can't parse telemetry event payload", e)
        null
    }
}

fun <T: TelemetryPayload> Flow<TelemetryEvent>.mapToDataPoint(selector: T.() -> Double): Flow<TDataPoint> =
    mapNotNull { event ->
        try {
            (event.payload as? T)?.let { payload ->
                TDataPoint(payload.timeStamp, selector(payload))
            }
        } catch (e: Exception) {
            LOG.w("Flow.mapToDataPoint", "Can't parse telemetry event payload", e)
            null
        }
    }

private val DONE = Any()
private val NULL = Any()

fun Flow<TDataPoint>.sampleTelemetry(scope: CoroutineScope, tickDelayMillis: Long, emptyDelayMillis: Long, replay: Boolean = false): Flow<TDataPoint> {
    val values = produceIn(scope)
    return flow {
        var lastTime = 0L
        var lastDataSentAt = 0L

        var lastValue: Any? = null
        val ticker = ticker(tickDelayMillis)
        while (lastValue !== DONE) {
            select<Unit> {
                values.onReceiveCatching { result ->
                    result
                        .onSuccess { point ->
                            lastValue = point
                            lastTime = point.timestamp
                            lastDataSentAt = System.currentTimeMillis()
                            emit(point)
                        }
                        .onFailure { throwable ->
                            throwable?.let { throw throwable }
                            ticker.cancel(/*ChildCancelledException()*/)
                            lastValue = DONE
                        }
                }

                ticker.onReceive {
                    val timeDiff = System.currentTimeMillis() - lastDataSentAt
                    if (lastValue == null || timeDiff > emptyDelayMillis && lastTime > 0) {
                        emit(TDataPoint(lastTime + timeDiff, 0.0))
                    } else if (replay) {
                        emit((lastValue as TDataPoint).copy(timestamp = lastTime + timeDiff))
                    }
                }
            }
        }
    }
}

fun String.commandFormat(prefix: String): String {
    return "$prefix ${dateFormat.format(Date())} : $this"
}

fun String.inCommandFormat() = commandFormat(">>")
fun String.outCommandFormat() = commandFormat("<<")

fun ClientTelemetryEvent.toInCommandFormat() = toString().inCommandFormat()

fun TelemetryEvent.toInCommandFormat() = toString().inCommandFormat()