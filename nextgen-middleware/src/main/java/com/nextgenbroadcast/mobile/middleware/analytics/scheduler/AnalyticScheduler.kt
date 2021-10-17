package com.nextgenbroadcast.mobile.middleware.analytics.scheduler

import androidx.work.*
import com.nextgenbroadcast.mobile.middleware.analytics.Atsc3Analytics
import java.util.concurrent.TimeUnit

class AnalyticScheduler(
        private val workManager: WorkManager
) : IAnalyticScheduler {
    override fun scheduleWork(bsid: Int, reportServerUrl: String, delaySec: Long, keepIfExists: Boolean): Boolean {
        val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<AnalyticsSendingWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delaySec, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Atsc3Analytics.RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
                .setInputData(workDataOf(
                        AnalyticsSendingWorker.ARG_BASE_URL to reportServerUrl,
                        AnalyticsSendingWorker.ARG_BSID to bsid
                ))
                .build()

        workManager.enqueueUniqueWork(
                getWorkName(bsid),
                if (keepIfExists) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE,
                uploadWorkRequest
        )

        return true
    }

    override fun cancelSchedule(bsid: Int) {
        workManager.cancelUniqueWork(getWorkName(bsid))
    }

    private fun getWorkName(bsid: Int) = "$bsid-${AnalyticsSendingWorker.NAME}"
}