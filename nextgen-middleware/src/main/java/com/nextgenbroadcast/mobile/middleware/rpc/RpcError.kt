package com.nextgenbroadcast.mobile.middleware.rpc

open class RpcError(private val code: Int, private val message: String?) {
    fun getCode(): Int {
        return code
    }

    fun getMessage(): String? {
        return message
    }
}