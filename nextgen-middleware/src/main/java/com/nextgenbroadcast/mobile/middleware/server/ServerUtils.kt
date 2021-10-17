package com.nextgenbroadcast.mobile.middleware.server

import android.net.Uri
import com.nextgenbroadcast.mobile.core.md5
import com.nextgenbroadcast.mobile.middleware.settings.IClientSettings

internal object ServerUtils {

    fun createUrl(entryPageUrl: String, settings: IClientSettings) =
        createEntryPoint(null, entryPageUrl, settings)

    fun createEntryPoint(appContextId: String?, entryPageUrl: String?, settings: IClientSettings) = with(settings) {
        Uri.Builder()
            .scheme("https")
            .encodedAuthority("$hostName:$httpsPort")
            .apply { appContextId?.let { appendEncodedPath(appContextId.md5()) } }
            .apply { entryPageUrl?.let { appendEncodedPath(entryPageUrl) } }
            .build()
            .toString()
    }

    fun appendSocketPathOrNull(entryPoint: String, settings: IClientSettings) = with(settings) {
        if (hostName.isNotBlank() && wssPort > 0) {
            Uri.parse(entryPoint)
                .buildUpon()
                .appendQueryParameter("wsURL", "wss://$hostName:$wssPort")
                .appendQueryParameter("rev", ServerConstants.REVISION)
                .build()
                .toString()
        } else null
    }

    fun multipartFilePath(fileName: String, index: Int) = "$fileName?index=$index"
}