package com.nextgenbroadcast.mobile.middleware.phy

import android.content.Context

interface IUsbConnector {
    fun connect(context: Context, compatibleList: List<Pair<Int, Int>>): Boolean
}