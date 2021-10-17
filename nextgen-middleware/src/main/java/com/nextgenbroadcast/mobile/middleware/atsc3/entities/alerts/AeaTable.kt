package com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts

import java.time.ZonedDateTime

data class AeaTable(
        var id: String = "",
        var refId: String? = null,
        var type: String = "",
        var effective: String? = null,
        var expires: ZonedDateTime? = null,
        var xml: String = "",
        var messages: MutableMap<String, String>? = mutableMapOf(),
        var alternateUrlList: List<String>? = null
) {
    companion object {
        const val CANCEL_ALERT = "cancel"
        const val UPDATE_ALERT = "update"
        const val ALERT = "alert"
    }
}