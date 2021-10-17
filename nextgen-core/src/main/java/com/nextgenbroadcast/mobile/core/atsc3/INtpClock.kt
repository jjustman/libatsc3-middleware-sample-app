package com.nextgenbroadcast.mobile.core.atsc3

interface INtpClock {
    fun getCurrentNtpTimeMs(): Long
}