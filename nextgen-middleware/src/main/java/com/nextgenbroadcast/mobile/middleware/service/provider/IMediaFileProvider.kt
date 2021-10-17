package com.nextgenbroadcast.mobile.middleware.service.provider

import android.net.Uri

interface IMediaFileProvider {
    fun getMediaFileUri(path: String): Uri
}