package com.nextgenbroadcast.mobile.middleware.atsc3.entities.held

import java.time.ZonedDateTime

data class Atsc3HeldPackage(
        var appContextId: String? = null,
        var requiredCapabilities: Long? = null,
        var bcastEntryPackageUrl: String? = null,
        var bcastEntryPageUrl: String? = null,
        var appRendering: Boolean = false,
        var clearAppContextCacheDate: ZonedDateTime? = null,
        var bbandEntryPageUrl: String? = null,
        var validFrom: ZonedDateTime? = null,
        var validUntil: ZonedDateTime? = null,
        var coupledServices: List<Int>? = null
)