package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index


@Entity(tableName = "sg_presentation",
        primaryKeys = ["content_Id", "startTime"],
        indices = [Index(value = ["content_Id"])]
)
data class SGPresentationEntity(
        @ColumnInfo(name = "content_Id")
        val contentId: String,
        val startTime: Long,
        val endTime: Long,
        val duration: Int
)