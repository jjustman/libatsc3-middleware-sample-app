package com.nextgenbroadcast.mobile.middleware

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.work.WorkManager
import com.google.firebase.installations.FirebaseInstallations
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.DefaultParam
import com.lyft.kronos.SyncListener
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.analytics.Atsc3Analytics
import com.nextgenbroadcast.mobile.middleware.analytics.scheduler.AnalyticScheduler
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Module
import com.nextgenbroadcast.mobile.middleware.atsc3.IAtsc3Module
import com.nextgenbroadcast.mobile.core.atsc3.INtpClock
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.ServiceGuideDeliveryUnitReader
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.RoomServiceGuideStore
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.db.SGDataBase
import com.nextgenbroadcast.mobile.middleware.cache.ApplicationCache
import com.nextgenbroadcast.mobile.middleware.cache.DownloadManager
import com.nextgenbroadcast.mobile.middleware.controller.service.ServiceControllerImpl
import com.nextgenbroadcast.mobile.middleware.controller.view.ViewControllerImpl
import com.nextgenbroadcast.mobile.middleware.repository.RepositoryImpl
import com.nextgenbroadcast.mobile.middleware.service.provider.MediaFileProvider
import com.nextgenbroadcast.mobile.middleware.provider.esg.ESGContentAuthority
import com.nextgenbroadcast.mobile.middleware.settings.MiddlewareSettingsImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.concurrent.TimeUnit

internal object Atsc3ReceiverStandalone {
    private const val REPOSITORY_PREFERENCE = "${BuildConfig.LIBRARY_PACKAGE_NAME}.preference"

    @Volatile
    private var INSTANCE: Atsc3ReceiverCore? = null

    @JvmStatic
    fun get(context: Context): Atsc3ReceiverCore {
        val instance = INSTANCE
        return instance ?: synchronized(this) {
            val instance2 = INSTANCE
            instance2 ?: let {
                newInstance(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private fun newInstance(appContext: Context): Atsc3ReceiverCore {
        val ntpClock = if (Atsc3Module.USE_DEV_STATISTIC) {
            Log.i("KronosClock","before AndroidClockFactory.createKronosClock")
            val kronosClock = AndroidClockFactory.createKronosClock(
                appContext,
                object : SyncListener {
                    override fun onError(host: String, throwable: Throwable) {
                        Log.e("KronosClock", "onError: host: $host, throwable: $throwable")
                    }

                    override fun onStartSync(host: String) {
                        Log.i("KronosClock", "onStartSync: host: $host")
                    }

                    override fun onSuccess(ticksDelta: Long, responseTimeMs: Long) {
                        Log.i("KronosClock", "onSuccess: ticksDelta: $ticksDelta, responseTimeMs: $responseTimeMs")
                    }
                },
                DefaultParam.NTP_HOSTS,
                TimeUnit.SECONDS.toMillis(5)
            ).apply {
                syncInBackground()
            }

            object : INtpClock {
                override fun getCurrentNtpTimeMs(): Long {
                    return kronosClock.getCurrentNtpTimeMs() ?: -1
                }
            }
        } else null

        val atsc3Module = Atsc3Module(appContext.cacheDir, ntpClock)

        val preferences = appContext.getSharedPreferences(REPOSITORY_PREFERENCE, Context.MODE_PRIVATE)
        val settings = MiddlewareSettingsImpl(preferences).also { settings ->
            runBlocking { getDeviceId(atsc3Module) }?.let { deviceId ->
                settings.setDeviceId(deviceId)
            }
        }

        val mediaFileProvider = MediaFileProvider(appContext)
        val repository = RepositoryImpl(mediaFileProvider, settings)

        val db = SGDataBase.getDatabase(appContext)
        val serviceGuideStore = RoomServiceGuideStore(db).apply {
            //TODO: ESGContentProvider should observe changes on its own
            subscribe {
                val serviceContentUri = ESGContentAuthority.getServiceContentUri(appContext)
                try {
                    appContext.contentResolver.notifyChange(serviceContentUri, null)
                } catch (e: SecurityException) {
                    // It seems ESG provider wasn't initialized, unsubscribe changes
                    unsubscribe()
                } catch (e: Exception) {
                    LOG.d("serviceGuideStore", "Failed to notify ESG changes", e)
                }
            }
        }
        val serviceGuideReader = ServiceGuideDeliveryUnitReader(serviceGuideStore)

        val clockSource = Settings.Global.getInt(appContext.contentResolver, Settings.Global.AUTO_TIME, 0)
        val scheduler = AnalyticScheduler(WorkManager.getInstance(appContext))
        val analytics = Atsc3Analytics(clockSource, appContext.filesDir, repository, settings, scheduler)

        val downloadManager = DownloadManager()
        val applicationCache = ApplicationCache(appContext.cacheDir, downloadManager)

        val controllerScope = CoroutineScope(Dispatchers.Default)

        val serviceController = ServiceControllerImpl(repository, settings, atsc3Module, analytics, serviceGuideReader, applicationCache, controllerScope)
        val viewController = ViewControllerImpl(repository, analytics)

        return Atsc3ReceiverCore(atsc3Module, serviceController, viewController, settings, repository, analytics, applicationCache).apply {
            MainScope().launch {
                errorFlow.collect { message ->
                    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getDeviceId(atsc3Module: IAtsc3Module): String? {
        return atsc3Module.getSerialNum() ?: let {
            try {
                val task = FirebaseInstallations.getInstance().id
                if (task.isSuccessful) {
                    task.result
                } else {
                    // use loop instead of suspendCancellableCoroutine because it may block the Main thread
                    // that's must be unblocked to receive addOnCompleteListener callback.
                    withTimeout(200) {
                        while (!task.isComplete) {
                            delay(10)
                        }
                        if (task.isSuccessful) task.result else null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}