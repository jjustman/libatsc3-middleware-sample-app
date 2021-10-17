package com.nextgenbroadcast.mobile.core.model

import android.os.Bundle

val AVService.uniqueId: Long
    get() = (bsid.toLong() shl Int.SIZE_BITS) or id.toLong()

fun AVService.isServiceEquals(service: AVService?): Boolean {
    return service != null && ((service.bsid == bsid && service.id == id)
            || service.globalId?.equals(globalId, true) == true
            || service.shortName?.equals(shortName, true) == true)
}

fun AVService.toBundle() = Bundle().apply {
    putInt(AVServiceEx.FIELD_BSID, bsid)
    putInt(AVServiceEx.FIELD_ID, id)
    putString(AVServiceEx.FIELD_SHORT_NAME, shortName)
    putString(AVServiceEx.FIELD_GLOBAL_ID, globalId)
    putInt(AVServiceEx.FIELD_MAJOR_CHANNEL_NO, majorChannelNo)
    putInt(AVServiceEx.FIELD_MINOR_CHANNEL_NO, minorChannelNo)
    putInt(AVServiceEx.FIELD_CATEGORY, category)
    putBoolean(AVServiceEx.FIELD_HIDDEN, hidden)
    putBoolean(AVServiceEx.FIELD_DEFAULT, default)
}

fun Bundle.toAVService() = AVService(
    getInt(AVServiceEx.FIELD_BSID),
    getInt(AVServiceEx.FIELD_ID),
    getString(AVServiceEx.FIELD_SHORT_NAME),
    getString(AVServiceEx.FIELD_GLOBAL_ID),
    getInt(AVServiceEx.FIELD_MAJOR_CHANNEL_NO),
    getInt(AVServiceEx.FIELD_MINOR_CHANNEL_NO),
    getInt(AVServiceEx.FIELD_CATEGORY),
    getBoolean(AVServiceEx.FIELD_HIDDEN),
    getBoolean(AVServiceEx.FIELD_DEFAULT)
)

private object AVServiceEx {
    const val FIELD_BSID = "bsid"
    const val FIELD_ID = "id"
    const val FIELD_SHORT_NAME = "shortName"
    const val FIELD_GLOBAL_ID = "globalId"
    const val FIELD_MAJOR_CHANNEL_NO = "majorChannelNo"
    const val FIELD_MINOR_CHANNEL_NO = "minorChannelNo"
    const val FIELD_CATEGORY = "category"
    const val FIELD_HIDDEN = "hidden"
    const val FIELD_DEFAULT = "default"
}