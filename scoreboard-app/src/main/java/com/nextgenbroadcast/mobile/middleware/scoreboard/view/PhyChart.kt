package com.nextgenbroadcast.mobile.middleware.scoreboard.view

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.jjoe64.graphview.LegendRenderer
import com.jjoe64.graphview.series.BaseSeries
import com.jjoe64.graphview.series.DataPoint
import com.nextgenbroadcast.mobile.middleware.dev.chart.TemporalChartView
import com.nextgenbroadcast.mobile.middleware.scoreboard.R
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.ChartConfiguration
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.ChartDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class PhyChart @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TemporalChartView(context, attrs, defStyleAttr) {

    override fun onFinishInflate() {
        super.onFinishInflate()

        title = resources.getString(R.string.chart_title)
        val labelTextColor = ContextCompat.getColor(context, R.color.white)

        titleColor = labelTextColor
        with(gridLabelRenderer) {
            gridColor = ContextCompat.getColor(context, R.color.chart_grid_color)
            verticalLabelsColor = labelTextColor
            horizontalLabelsColor = labelTextColor
            verticalLabelsSecondScaleColor = labelTextColor
            textSize = resources.getDimension(R.dimen.chart_label_text_size)
            numVerticalLabels = 4
        }

        with(legendRenderer) {
            isVisible = true
            align = LegendRenderer.LegendAlign.MIDDLE // position will be corrected in onLayout()
            width = resources.getDimensionPixelSize(R.dimen.chart_legend_width)
            textSize = resources.getDimension(R.dimen.chart_legend_text_size)
            textColor = labelTextColor
        }
    }

    override fun onBeforeSourceSet(source: TemporalDataSource) {
        setViewport(VISIBLE_PERIOD, source.getMinY(), source.getMaxY())
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        legendRenderer.margin = right - legendRenderer.width - resources.getDimensionPixelSize(R.dimen.chart_legend_offset)
    }

    class MultiDataSource(
        private val primarySources: List<ChartDataSource>,
        private val secondarySources: List<ChartDataSource>,
        private val primaryChartConfiguration: ChartConfiguration,
        private val secondaryChartConfiguration: ChartConfiguration
    ) : TemporalDataSource(VISIBLE_PERIOD) {

        private var sourceJob: Job? = null

        override fun open() {
            super.open()
            sourceJob = CoroutineScope(Dispatchers.Main).launch {
                addValueToChart(primarySources, primaryChartConfiguration) +
                    addValueToChart(secondarySources, secondaryChartConfiguration)
            }
        }

        private fun CoroutineScope.addValueToChart(
            sources: List<ChartDataSource>,
            config: ChartConfiguration
        ) = sources.map { dataSource ->
            launch {
                dataSource.data.collect { (time, value) ->
                    addValue(
                        dataSource.series,
                        normalizeValue(value, config),
                        time
                    )
                }
            }
        }

        override fun close(reset: Boolean) {
            super.close(reset)
            sourceJob?.cancel()
            sourceJob = null
            if (reset) {
                (primarySources + secondarySources).forEach { source ->
                    source.series.resetData(emptyArray())
                }
            }
        }

        override fun getSeries(): List<BaseSeries<DataPoint>> = primarySources.map { source ->
            source.series
        }

        override fun getSecondarySeries(): List<BaseSeries<DataPoint>> = secondarySources.map { source ->
            source.series
        }

        override fun getSecondaryMinY() = secondaryChartConfiguration.minValue
        override fun getSecondaryMaxY() = secondaryChartConfiguration.maxValue
        override fun getMaxY(): Double = primaryChartConfiguration.maxValue
        override fun getMinY(): Double = primaryChartConfiguration.minValue

        private fun normalizeValue(value: Double, config: ChartConfiguration): Double {
            return min(max(config.minValue, value), config.maxValue)
        }
    }

    companion object {
        private val VISIBLE_PERIOD = TimeUnit.MINUTES.toMillis(5)
    }
}