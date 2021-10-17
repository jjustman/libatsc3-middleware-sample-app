package com.nextgenbroadcast.mobile.middleware.service

import android.os.*
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverCore

class StandaloneAtsc3Service : Atsc3ForegroundService() {

    override fun createServiceBinder(receiver: Atsc3ReceiverCore): IBinder? = null

    companion object Initializer {
        init {
            clazz = StandaloneAtsc3Service::class.java
        }
    }
}