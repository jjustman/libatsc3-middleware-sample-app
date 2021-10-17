package com.nextgenbroadcast.mobile.middleware.dev.chart

import com.jjoe64.graphview.series.BaseSeries
import com.jjoe64.graphview.series.DataPoint

interface IChartDataSource {
    fun open()
    fun close(reset: Boolean = true)
    fun getSeries(): List<BaseSeries<DataPoint>>
}