package com.nextgenbroadcast.mobile.middleware.atsc3.entities.app

data class Atsc3ApplicationFile(
        val contentLocation: String,
        val contentType: String?,
        val version: Int
)