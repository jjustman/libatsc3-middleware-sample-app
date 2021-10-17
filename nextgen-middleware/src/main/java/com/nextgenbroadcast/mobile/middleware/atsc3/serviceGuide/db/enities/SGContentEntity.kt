package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.enities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
        tableName = "sg_content",
        indices = [Index(value = arrayOf("id"), unique = true)]
)
data class SGContentEntity(
        @PrimaryKey
        val id: String,
        val icon: String?,
        val version: Long
)