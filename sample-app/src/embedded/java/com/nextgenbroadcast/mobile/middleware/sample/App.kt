package com.nextgenbroadcast.mobile.middleware.sample

import android.app.Application
import com.bugfender.sdk.Bugfender
import com.nextgenbroadcast.mobile.core.LOG

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        Bugfender.init(this, BuildConfig.BugfenderKey, BuildConfig.DEBUG)
        Bugfender.enableCrashReporting()
        Bugfender.enableUIEventLogging(this)
        Bugfender.enableLogcatLogging() // optional, if you want logs automatically collected from logcat
        try {
            ReceiverContentResolver.getDeviceId(this)?.let { deviceId ->
                Bugfender.setDeviceString("device_id", deviceId)
            }
        } catch (e: Exception) {
            LOG.d("App", "Failed to read Device Id", e)
        }
    }
}