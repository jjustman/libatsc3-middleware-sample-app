package com.nextgenbroadcast.mobile.middleware.analytics.model

import java.util.*

class AppInterval(
        var startTime: Date,
        var endTime: Date?,
        var lifeCycle: Int?,
) {
    companion object {
        const val DOWNLOADED_AND_NOT_LAUNCHED = 1
        const val DOWNLOADED_AND_AUTO_LAUNCHED = 2
        const val DOWNLOADED_AND_USER_LAUNCHED = 3
    }
}