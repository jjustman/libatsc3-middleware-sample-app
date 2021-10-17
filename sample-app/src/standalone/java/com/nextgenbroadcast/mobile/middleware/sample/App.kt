package com.nextgenbroadcast.mobile.middleware.sample

import android.app.Application
import androidx.work.Configuration

class App : Application(), Configuration.Provider {

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
                .setDefaultProcessName("${BuildConfig.APPLICATION_ID}:atsc3Service")
                .build()
    }
}