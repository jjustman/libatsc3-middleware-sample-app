package com.nextgenbroadcast.mobile.middleware.analytics.model

import com.google.gson.annotations.SerializedName
import java.util.*

class ReportInterval(
        var startTime: Date,
        var endTime: Date?,
        var destinationDeviceType: Int?
) {
    @SerializedName("broadcastInterval")
    val broadcastIntervals = mutableListOf<BroadcastInterval>()
    @SerializedName("AppInterval")
    val appIntervals = mutableListOf<AppInterval>()

    val isFinished: Boolean
        get() {
            return endTime != null
        }
}