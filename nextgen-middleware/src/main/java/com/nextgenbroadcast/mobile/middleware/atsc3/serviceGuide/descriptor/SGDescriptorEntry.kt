package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.descriptor

internal class SGDescriptorEntry(
        val startTime: Long,
        val endTime: Long,
        val transmissionSessionID: Long,
        val fragments: Map<String, SGFragment>
)