package com.nextgenbroadcast.mobile.middleware.service.provider

import android.content.Context
import android.net.Uri
import com.nextgenbroadcast.mobile.middleware.R
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Module
import com.nextgenbroadcast.mobile.middleware.provider.dash.DashContentProvider
import com.nextgenbroadcast.mobile.middleware.provider.mmt.MMTContentProvider
import java.io.File

open class MediaFileProvider(
        private val context: Context
) : IMediaFileProvider {
    private val dashProviderAuthority = context.getString(R.string.nextgenMediaFileProvider)
    private val mmtProviderAuthority = context.getString(R.string.nextgenMMTContentProvider)

    override fun getMediaFileUri(path: String): Uri {
        return if (path.startsWith(SCHEME_MMT)) {
            MMTContentProvider.getUriForService(
                    context,
                    mmtProviderAuthority,
                    path.substring(SCHEME_MMT.length)
            )
        } else {
            DashContentProvider.getUriForFile(
                    context,
                    dashProviderAuthority,
                    File(path)
            )
        }
    }

    companion object {
        const val SCHEME_MMT = Atsc3Module.SCHEME_MMT
    }
}