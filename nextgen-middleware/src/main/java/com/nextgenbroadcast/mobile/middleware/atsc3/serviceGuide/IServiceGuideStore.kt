package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide

import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGContent
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit.SGService

internal interface IServiceGuideStore {
    fun clearAll()
    fun storeService(serviceMap: Map<Int, SGService>)
    fun storeContent(contentMap: Map<String, SGContent>)
}