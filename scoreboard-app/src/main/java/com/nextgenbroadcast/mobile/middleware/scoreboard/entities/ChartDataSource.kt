package com.nextgenbroadcast.mobile.middleware.scoreboard.entities

import com.jjoe64.graphview.series.BaseSeries
import com.jjoe64.graphview.series.DataPoint
import kotlinx.coroutines.flow.Flow

data class ChartDataSource(
    val topic: String,
    val series: BaseSeries<DataPoint>,
    val data: Flow<TDataPoint>
)
