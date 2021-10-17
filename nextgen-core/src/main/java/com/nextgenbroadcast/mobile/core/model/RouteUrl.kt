package com.nextgenbroadcast.mobile.core.model

data class RouteUrl (
    val id: String,
    val path: String,
    val title: String,
    val isDefault: Boolean = false,
    val isPool: Boolean = false
)