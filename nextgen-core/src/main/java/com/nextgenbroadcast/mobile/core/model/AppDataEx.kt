package com.nextgenbroadcast.mobile.core.model

import android.net.Uri

val AppData.bCastEntryPageUrlFull: String?
    get() = bCastEntryPageUrl?.let { entryPage ->
        Uri.parse(baseUrl).buildUpon().appendEncodedPath(entryPage).build().toString()
    }

val AppData.isAvailable: Boolean
    get() = isBBandAvailable || isBCastAvailable

val AppData.isBBandAvailable: Boolean
    get() = !bBandEntryPageUrl.isNullOrBlank()

val AppData.isBCastAvailable: Boolean
    get() = !bCastEntryPageUrl.isNullOrBlank() && !cachePath.isNullOrBlank()

fun AppData.isAppEquals(other: AppData?): Boolean {
    return other?.let {
        contextId == other.contextId
                && bBandEntryPageUrl == other.bBandEntryPageUrl
                && bCastEntryPageUrl == other.bCastEntryPageUrl
    } ?: false
}
