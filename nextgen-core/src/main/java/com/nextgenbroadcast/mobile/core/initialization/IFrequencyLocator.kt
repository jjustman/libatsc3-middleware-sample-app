package com.nextgenbroadcast.mobile.core.initialization

import android.content.Context

interface IFrequencyLocator {
    suspend fun locateFrequency(context: Context): List<Int>
}