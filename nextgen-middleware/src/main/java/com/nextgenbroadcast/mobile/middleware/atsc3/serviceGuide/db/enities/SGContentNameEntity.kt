package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities

import androidx.room.Entity

@Entity(tableName = "sg_content_name",
        primaryKeys = ["contentId", "language"],
        foreignKeys = [androidx.room.ForeignKey(
                entity = SGContentEntity::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("contentId"),
                onDelete = androidx.room.ForeignKey.CASCADE,
                onUpdate = androidx.room.ForeignKey.CASCADE
        )]
)
data class SGContentNameEntity(
        val contentId: String,
        val language: String,
        val name: String
)