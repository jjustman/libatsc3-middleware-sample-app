package com.nextgenbroadcast.mobile.middleware.analytics.scheduler

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverStandalone
import com.nextgenbroadcast.mobile.middleware.analytics.Atsc3Analytics
import com.nextgenbroadcast.mobile.middleware.analytics.IAtsc3Analytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class AnalyticsSendingWorker(
        appContext: Context,
        workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    private val atsc3Analytics: IAtsc3Analytics by lazy {
        Atsc3ReceiverStandalone.get(appContext).analytics
    }

    override fun doWork(): Result {
        val bsid = inputData.getInt(ARG_BSID, 0)
        return inputData.getString(ARG_BASE_URL)?.let { url ->
            runBlocking {
                try {
                    with(atsc3Analytics.sendAllEvents(bsid, url)) {
                        join()
                        if (isCancelled) {
                            if (runAttemptCount < Atsc3Analytics.MAX_RETRY_COUNT) Result.retry() else Result.failure()
                        } else {
                            Result.success()
                        }
                    }
                } catch (e: CancellationException) {
                    Result.retry()
                }
            }
        } ?: Result.failure()
    }

    companion object {
        const val NAME = "ATSC3_ANALYTICS_REPORT_SENDER"
        const val ARG_BASE_URL = "BASE_URL"
        const val ARG_BSID = "BSID"
    }
}