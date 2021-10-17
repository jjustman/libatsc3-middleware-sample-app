package com.nextgenbroadcast.mobile.middleware.scoreboard.entities

data class ChartData(
    val primaryDataSources: List<ChartDataSource>,
    val secondaryDataSources: List<ChartDataSource> = emptyList(),
    val primaryChartConfiguration: ChartConfiguration = ChartConfiguration(0.00, 100.0),
    val secondaryChartConfiguration: ChartConfiguration = ChartConfiguration(0.00, 100.0)
)