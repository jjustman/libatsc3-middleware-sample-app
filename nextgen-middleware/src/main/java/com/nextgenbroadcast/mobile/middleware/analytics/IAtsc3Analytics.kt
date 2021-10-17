package com.nextgenbroadcast.mobile.middleware.analytics

import kotlinx.coroutines.Job

internal interface IAtsc3Analytics {
    fun setReportServerUrl(bsid: Int, serverUrl: String?)

    fun startSession(bsid: Int, serviceId: Int, globalServiceId: String?, serviceType: Int)
    fun finishSession()
    fun startDisplayMediaContent()
    fun finishDisplayMediaContent()
    fun startApplicationSession()
    fun finishApplicationSession()

    fun sendAllEvents(bsid: Int, reportServerUrl: String): Job
}