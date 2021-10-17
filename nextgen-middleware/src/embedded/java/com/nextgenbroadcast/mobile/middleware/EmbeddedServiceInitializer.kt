package com.nextgenbroadcast.mobile.middleware

import android.content.Context
import androidx.startup.Initializer
import com.nextgenbroadcast.mobile.middleware.service.EmbeddedAtsc3Service

class EmbeddedServiceInitializer : Initializer<EmbeddedAtsc3Service.Initializer> {
    override fun create(context: Context) = EmbeddedAtsc3Service.Initializer

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = arrayListOf()
}