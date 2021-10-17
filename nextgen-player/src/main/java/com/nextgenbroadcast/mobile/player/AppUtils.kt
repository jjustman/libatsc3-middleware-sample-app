package com.nextgenbroadcast.mobile.player

import android.content.Context
import com.google.android.exoplayer2.util.Util
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

object AppUtils {
    fun getUserAgent(context: Context): String {
        var appName: String = context.applicationInfo.loadLabel(context.packageManager).toString()
        appName = try {
            URLEncoder.encode(appName, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            appName.replace("[^\\w]".toRegex(), "")
        }
        return Util.getUserAgent(context, appName)
    }
}