package com.nextgenbroadcast.mobile.middleware.scoreboard.entities

sealed class CommandTarget {
    object Broadcast : CommandTarget() {
        override fun ids(): List<String> = emptyList()
    }

    data class Device(val deviceId: String) : CommandTarget() {
        override fun ids(): List<String> = listOf(deviceId)
    }

    data class SelectedDevices(val deviceIdList: List<String>) : CommandTarget() {
        override fun ids(): List<String> = deviceIdList
    }

    abstract fun ids(): List<String>

}
