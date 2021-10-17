package com.nextgenbroadcast.mobile.middleware.atsc3.entities.service

import kotlin.collections.ArrayList

data class Atsc3Service(
        val bsid: Int = 0,
        var serviceId: Int = 0,
        var globalServiceId: String? = null,
        var sltSvcSeqNum: Int = 0,
        var majorChannelNo: Int = 0,
        var minorChannelNo: Int = 0,
        var serviceCategory: Int = 0,
        var shortServiceName: String? = null,
        var hidden: Boolean = false,
        var broadcastSvcSignalingCollection: ArrayList<BroadcastSvcSignaling> = ArrayList()
) {
    override fun toString(): String {
        return "$majorChannelNo.$minorChannelNo $shortServiceName"
    }
}