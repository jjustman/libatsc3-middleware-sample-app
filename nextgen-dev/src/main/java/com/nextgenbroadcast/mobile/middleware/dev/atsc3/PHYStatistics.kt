package com.nextgenbroadcast.mobile.middleware.dev.atsc3

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.ngbp.libatsc3.middleware.android.phy.models.RfPhyStatistics

object PHYStatistics {
    val rfMetricsFlow = MutableSharedFlow<RfPhyStatistics>(0, 3, BufferOverflow.DROP_OLDEST)

    var PHYRfStatistics: String = ""
    var PHYBWStatistics: String = ""
    var PHYL1dTimingStatistics: String = ""
}