package com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.descriptor

internal class SGFragment(
        var id: String? = null,
        var type: Int = -1,
        var encoding: Int = -1,
        var version: Long = 0,
        var transportID: Long = -1,
        var unit: SGDeliveryUnit? = null
)
