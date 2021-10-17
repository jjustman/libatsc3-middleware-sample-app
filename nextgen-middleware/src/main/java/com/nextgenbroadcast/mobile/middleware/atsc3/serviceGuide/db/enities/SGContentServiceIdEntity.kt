package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(tableName = "sg_content_serviceId",
        primaryKeys = ["contentId", "serviceId"],
        foreignKeys = [ForeignKey(
                entity = SGContentEntity::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("contentId"),
                onDelete = ForeignKey.CASCADE,
                onUpdate = ForeignKey.CASCADE
        )]
)
data class SGContentServiceIdEntity(
        val contentId: String,
        val serviceId: Int
)