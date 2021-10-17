package com.nextgenbroadcast.mobile.middleware.provider.esg

import android.content.Context
import android.net.Uri
import com.nextgenbroadcast.mobile.middleware.R

object ESGContentAuthority {
    private var serviceContentUri: Uri? = null
    private var programContentUri: Uri? = null

    fun getServiceContentUri(context: Context): Uri {
        return serviceContentUri ?: let {
            Uri.parse("content://${getAuthority(context)}/${ESGContentProvider.SERVICE_CONTENT_PATH}").also {
                serviceContentUri = it
            }
        }
    }

    fun getProgramContentUri(context: Context): Uri {
        return programContentUri ?: let {
            Uri.parse("content://${getAuthority(context)}/${ESGContentProvider.PROGRAM_CONTENT_PATH}").also {
                programContentUri = it
            }
        }
    }

    fun getAuthority(context: Context) = context.getString(R.string.nextgenServicesGuideProvider)
}