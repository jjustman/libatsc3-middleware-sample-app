package com.nextgenbroadcast.mobile.middleware.dev.initialization

import android.content.Context
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.initialization.IFrequencyLocator
import com.nextgenbroadcast.mobile.middleware.dev.config.DevConfig

class StubFrequencyLocator : IFrequencyLocator {
    override suspend fun locateFrequency(context: Context): List<Int> {
        try {
            return DevConfig.get(context).frequencies ?: emptyList()
        } catch (e: Exception) {
            LOG.w(TAG, "Failed to frequencies from external config", e)
        }

        return emptyList()
    }

    companion object {
        val TAG: String = StubFrequencyLocator::class.java.simpleName
    }
}