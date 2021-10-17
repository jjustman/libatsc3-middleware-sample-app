package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide

import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGContent
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGSchedule
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGService

data class SGUrl(
        val sgType: SGUrlType,
        val sgPath: String,
        val service: String?,
        val content: String?,
        val version: Long
) {
    enum class SGUrlType {
        Service, Schedule, Content
    }

    companion object {
        internal fun service(service: SGService): SGUrl? = service.toUrl()?.let { path ->
            SGUrl(SGUrlType.Service, path, service.globalServiceId, null, service.version)
        }

        internal fun schedule(schedule: SGSchedule, service: String?): SGUrl? = schedule.toUrl()?.let { path ->
            SGUrl(SGUrlType.Schedule, path, service, null, schedule.version)
        }

        internal fun content(content: SGContent, service: String?): SGUrl? = content.toUrl()?.let { path ->
            SGUrl(SGUrlType.Content, path, service, content.id, content.version)
        }
    }
}