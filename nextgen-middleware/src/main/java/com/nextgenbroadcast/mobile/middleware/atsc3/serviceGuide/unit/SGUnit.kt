package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.unit

import com.nextgenbroadcast.mobile.middleware.server.ServerUtils

internal abstract class SGUnit(
        open var version: Long = 0,
) {
    var duFileName: String? = null
    var duIndex: Int? = null

    fun toUrl() = duFileName?.let { fileName ->
        duIndex?.let { index ->
            ServerUtils.multipartFilePath(fileName, index)
        }
    }
}