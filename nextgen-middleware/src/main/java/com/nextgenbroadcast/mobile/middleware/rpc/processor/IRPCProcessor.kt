package com.nextgenbroadcast.mobile.middleware.rpc.processor

interface IRPCProcessor {
    fun processRequest(request: String): String
    fun processRequest(requests: List<String>): List<String>
}