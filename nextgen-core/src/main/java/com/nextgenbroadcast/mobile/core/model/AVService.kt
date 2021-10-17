package com.nextgenbroadcast.mobile.core.model

data class AVService(
        val bsid: Int,
        val id: Int,
        val shortName: String?,
        val globalId: String?,
        val majorChannelNo: Int,
        val minorChannelNo: Int,
        val category: Int,
        val hidden: Boolean = false,
        val default: Boolean = false
)