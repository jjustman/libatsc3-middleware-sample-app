package com.nextgenbroadcast.mobile.middleware.dev.chart

import android.content.Context
import android.util.AttributeSet
import com.jjoe64.graphview.DefaultLabelFormatter
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.BaseSeries
import com.jjoe64.graphview.series.DataPoint
import com.nextgenbroadcast.mobile.core.LOG
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

open class TemporalChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GraphView(context, attrs, defStyleAttr) {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val timeZone = ZoneId.systemDefault()

    private var source: TemporalDataSource? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        with(gridLabelRenderer) {
            labelFormatter = object : DefaultLabelFormatter() {
                override fun formatLabel(value: Double, isValueX: Boolean): String? {
                    return if (!isValueX) {
                        super.formatLabel(value, isValueX)
                    } else if ((source?.getTotalCount() ?: 0) > 2) {
                        LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(value.toLong()),
                            timeZone
                        ).format(timeFormat)
                    } else null
                }
            }
        }
    }

    fun setDataSource(source: TemporalDataSource?) {
        this.source?.close()

        this.source = source

        removeAllSeries()
        secondScale.removeAllSeries()

        if (source == null) return

        onBeforeSourceSet(source)

        source.getSeries().forEach { series ->
            addSeries(series)
        }

        val secondarySeries = source.getSecondarySeries()
        if (secondarySeries.isNotEmpty()) {
            secondarySeries.forEach { series ->
                secondScale.addSeries(series)
            }
            secondScale.setMinY(source.getSecondaryMinY())
            secondScale.setMaxY(source.getSecondaryMaxY())
        }

        if (isAttachedToWindow) {
            source.open()
        }
    }

    fun setViewport(visiblePeriodMills: Long, minValue: Double, maxValue: Double) {
        with(viewport) {
            isXAxisBoundsManual = true
            setMinX(0.0)
            setMaxX(visiblePeriodMills.toDouble().toSeconds())
            isYAxisBoundsManual = true
            setMinY(minValue)
            setMaxY(maxValue)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        source?.open()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        source?.close()
    }

    open fun onBeforeSourceSet(source: TemporalDataSource) {}

    abstract class TemporalDataSource(
        private val visiblePeriod: Long
    ) : IChartDataSource {
        private var eventCounter = 0
        private var eventStartTime: Long = 0
        private var currentSpeed: Long = 0
        private var totalValueCount: Long = 0

        fun addValue(graphSeries: BaseSeries<DataPoint>, value: Double) {
            addValue(graphSeries, value, System.currentTimeMillis())
        }

        fun addValue(graphSeries: BaseSeries<DataPoint>, value: Double, time: Long) {
            if (eventCounter == 0) {
                eventStartTime = time
            }

            if (eventCounter > SPEED_UPDATE_ITEM_COUNT) {
                currentSpeed = ((time - eventStartTime) / SPEED_UPDATE_ITEM_COUNT)
                eventCounter = 0
                eventStartTime = time
            }

            val dataPoint = DataPoint(time.toDouble().toSeconds(), value)

            val maxValueCount = if (currentSpeed == 0L) {
                INITIAL_VALUE_COUNT
            } else {
                INITIAL_VALUE_COUNT + (visiblePeriod / currentSpeed)
            }

            try {
                graphSeries.appendData(dataPoint, true, maxValueCount.toInt())
            } catch (e: IndexOutOfBoundsException) {
                LOG.d(TAG, "Failed to append new value: $dataPoint", e)
            } catch (e: IllegalArgumentException) {
                LOG.d(TAG, "Failed to append new value: $dataPoint", e)
            }

            eventCounter++
            totalValueCount++
        }

        fun getTotalCount() = totalValueCount

        override fun open() {
            eventCounter = 0
        }

        override fun close(reset: Boolean) {
            eventCounter = 0
        }

        open fun getSecondarySeries(): List<BaseSeries<DataPoint>> = emptyList()
        open fun getMinY(): Double = 0.0
        open fun getMaxY(): Double = 100.0
        open fun getSecondaryMinY(): Double = 0.0
        open fun getSecondaryMaxY(): Double = 100.0

        companion object {
            val TAG: String = TemporalDataSource::class.java.simpleName

            private const val SPEED_UPDATE_ITEM_COUNT = 10
            private const val INITIAL_VALUE_COUNT = 100
        }
    }
}

private fun Double.toSeconds() = this / 1000