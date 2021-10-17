package com.nextgenbroadcast.mobile.middleware.location

import android.location.Location

class FrequencyLocation(
        val location: Location,
        val frequencyList: List<Int>
)