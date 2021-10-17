package com.nextgenbroadcast.mobile.middleware.atsc3.source

interface IAtsc3Source {
    fun open(): Int
    fun close()
    fun stop()

    fun getConfigCount(): Int
    fun getConfigByIndex(configIndex: Int): Any
    fun getAllConfigs(): List<Any>

    fun getSdkVersion(): String?
    fun getFirmwareVersion(): String?

    companion object {
        const val RESULT_ERROR = -1
        const val RESULT_OK = 0
    }
}