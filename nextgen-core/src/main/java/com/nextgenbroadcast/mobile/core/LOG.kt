package com.nextgenbroadcast.mobile.core

import android.util.Log

object LOG {

    @JvmOverloads
    @JvmStatic
    fun v(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.v(tag, msg, tr)
        } else {
            Log.v(tag, msg)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.d(tag, msg, tr)
        } else {
            Log.d(tag, msg)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun i(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.i(tag, msg, tr)
        } else {
            Log.i(tag, msg)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.w(tag, msg, tr)
        } else {
            Log.w(tag, msg)
        }
    }

    @JvmOverloads
    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.e(tag, msg, tr)
        } else {
            Log.e(tag, msg)
        }
    }

}