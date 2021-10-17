package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "sg_schedule_content",
        primaryKeys = ["scheduleId", "contentId"],
        indices = [
            Index(value = arrayOf("contentId"), unique = true)
        ]
)
data class SGScheduleContentEntity(
        val scheduleId: String,
        val contentId: String
)