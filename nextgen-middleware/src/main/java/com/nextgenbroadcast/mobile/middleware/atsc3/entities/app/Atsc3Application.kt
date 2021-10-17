package com.nextgenbroadcast.mobile.middleware.atsc3.entities.app

data class Atsc3Application(
        val uid: String,
        val packageName: String,
        val appContextIdList: List<String>,
        val cachePath: String,
        val files: Map<String, Atsc3ApplicationFile>
)