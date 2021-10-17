package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities

import androidx.room.*

@Entity(tableName = "sg_schedule",
        indices = [Index(value = ["serviceId"])]
)
data class SGScheduleEntity(
        @PrimaryKey
        val id: String,
        val serviceId: Int,
        val version: Long
)