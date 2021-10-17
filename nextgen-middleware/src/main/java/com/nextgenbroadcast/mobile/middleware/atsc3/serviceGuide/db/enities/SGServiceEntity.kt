package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sg_service")
data class SGServiceEntity(
        @PrimaryKey
        val serviceId: Int,
        val globalServiceId: String?,
        val majorChannelNo: Int,
        val minorChannelNo: Int,
        val shortServiceName: String?,
        val version: Long,
        val bsid: Int
)