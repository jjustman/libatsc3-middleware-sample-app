package com.nextgenbroadcast.mobile.core.model

data class AppData(
    val contextId: String,
    val baseUrl: String,
    val bBandEntryPageUrl: String?,
    val bCastEntryPageUrl: String?,
    val compatibleServiceIds: List<Int>,
    val cachePath: String?,
    val sessionId: Int = 0
)