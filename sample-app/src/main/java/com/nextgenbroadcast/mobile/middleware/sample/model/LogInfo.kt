package com.nextgenbroadcast.mobile.middleware.sample.model

import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfoType.GROUP
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfoType.RECORD

sealed class LogInfo(val type: LogInfoType) {
    data class Group(val title: String) : LogInfo(type = GROUP)
    data class Record(
        var name: String,
        var displayName: String,
        var enabled: Boolean
    ) : LogInfo(type = RECORD)
}

enum class LogInfoType {
    GROUP,
    RECORD;

    companion object {
        fun getOrNull(ordinal: Int): LogInfoType? {
            return values().getOrNull(ordinal)
        }
    }

}
