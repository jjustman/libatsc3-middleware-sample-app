package com.nextgenbroadcast.mobile.middleware.analytics.scheduler

interface IAnalyticScheduler {
    fun scheduleWork(bsid: Int, reportServerUrl: String, delaySec: Long, keepIfExists: Boolean = false): Boolean
    fun cancelSchedule(bsid: Int)
}