package com.nextgenbroadcast.mobile.middleware.analytics

import android.location.Location
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.nextgenbroadcast.mobile.core.DateUtils
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.analytics.model.AppInterval
import com.nextgenbroadcast.mobile.middleware.analytics.model.BroadcastInterval
import com.nextgenbroadcast.mobile.middleware.analytics.model.ReportInterval
import com.nextgenbroadcast.mobile.middleware.analytics.scheduler.IAnalyticScheduler
import com.nextgenbroadcast.mobile.middleware.analytics.serializer.DateSerializer
import com.nextgenbroadcast.mobile.middleware.analytics.serializer.LocationSerializer
import com.nextgenbroadcast.mobile.middleware.net.await
import com.nextgenbroadcast.mobile.middleware.repository.IRepository
import com.nextgenbroadcast.mobile.middleware.settings.IMiddlewareSettings
import com.squareup.tape2.QueueFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

internal class Atsc3Analytics(
        private val clockSource: Int,
        private val cacheFolder: File,
        private val repository: IRepository,
        private val settings: IMiddlewareSettings,
        private val scheduler: IAnalyticScheduler
) : IAtsc3Analytics {
    private val lock = Mutex()
    private val deviceId = settings.deviceId

    private val storeMap = ConcurrentHashMap<Int, QueueFile>()

    private var lastReportDate: LocalDateTime
        get() = settings.lastReportDate
        set(value) {
            settings.lastReportDate = value
        }

    private val gson: Gson by lazy {
        GsonBuilder()
                .registerTypeAdapter(Date::class.java, DateSerializer())
                .registerTypeAdapter(Location::class.java, LocationSerializer())
                .create()
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient()
    }

    private var activeSession: AVService? = null
    private var serverUrl: String? = null

    override fun setReportServerUrl(bsid: Int, serverUrl: String?) {
        this.serverUrl = serverUrl

        if (serverUrl != null) {
            // send a reports if it's time to, or schedule the next sending
            if (DateUtils.hoursTillNow(lastReportDate) >= MAX_HOURS_BEFORE_SEND_REPORT) {
                sendAllEventsAndReschedule(bsid, serverUrl)
            } else {
                scheduler.scheduleWork(bsid, serverUrl, TimeUnit.HOURS.toSeconds(MAX_HOURS_BEFORE_SEND_REPORT))
            }
        }
    }

    override fun startSession(bsid: Int, serviceId: Int, globalServiceId: String?, serviceType: Int) {
        finishSession()
        activeSession = AVService(BSID_REGISTRATION_COUNTRY, bsid, serviceId, globalServiceId, serviceType)
    }

    override fun startDisplayMediaContent() {
        activeSession?.let { session ->
            val currentTime = Date()
            val reportInterval = getLastOrCreateReportInterval(session, currentTime)

            finishDisplayMediaContent(reportInterval, currentTime)
            reportInterval.broadcastIntervals.add(
                    BroadcastInterval(
                            broadcastStartTime = currentTime,
                            broadcastEndTime = null,
                            receiverStartTime = currentTime
                    )
            )
        }
    }

    override fun finishDisplayMediaContent() {
        activeSession?.let { session ->
            getLastReportInterval(session)?.let { reportInterval ->
                if (!reportInterval.isFinished) {
                    finishDisplayMediaContent(reportInterval, Date())
                }
            }
        }
    }

    override fun startApplicationSession() {
        activeSession?.let { session ->
            val currentTime = Date()
            val reportInterval = getLastOrCreateReportInterval(session, currentTime)

            finishApplicationSession(reportInterval, currentTime)
            reportInterval.appIntervals.add(
                    AppInterval(
                            startTime = currentTime,
                            endTime = null,
                            lifeCycle = AppInterval.DOWNLOADED_AND_USER_LAUNCHED
                    )
            )
        }
    }

    override fun finishApplicationSession() {
        getLastReportInterval(activeSession)?.let { reportInterval ->
            if (!reportInterval.isFinished) {
                finishApplicationSession(reportInterval, Date())
            }
        }
    }

    override fun finishSession() {
        activeSession?.let { session ->
            activeSession = null

            finishDisplayContent(session)

            addToStore(session)
        }
    }

    private fun finishDisplayContent(session: AVService) {
        session.reportIntervals.let { intervals ->
            intervals.lastOrNull()?.let { reportInterval ->
                if (!reportInterval.isFinished) {
                    val currentTime = Date()
                    if (isIntervalLess10sec(reportInterval.startTime, currentTime)) {
                        intervals.remove(reportInterval)
                    } else {
                        reportInterval.endTime = currentTime

                        finishDisplayMediaContent(reportInterval, currentTime)
                        finishApplicationSession(reportInterval, currentTime)
                    }
                }
            }
        }
    }

    private fun finishDisplayMediaContent(reportInterval: ReportInterval, currentTime: Date) {
        reportInterval.broadcastIntervals.let { intervals ->
            intervals.lastOrNull()?.let { broadcastInterval ->
                if (isIntervalLess10sec(broadcastInterval.receiverStartTime, currentTime)) {
                    intervals.remove(broadcastInterval)
                } else {
                    intervals.lastOrNull()?.apply {
                        //TODO: should be something else, ask Jason
                        broadcastEndTime = currentTime
                    }
                }
            }
        }
    }

    private fun finishApplicationSession(interval: ReportInterval, currentTime: Date) {
        interval.appIntervals.let { intervals ->
            intervals.lastOrNull()?.let { appInterval ->
                if (appInterval.endTime == null) {
                    if (isIntervalLess5sec(appInterval.startTime, currentTime)) {
                        intervals.remove(appInterval)
                    } else {
                        appInterval.endTime = currentTime
                    }
                }
            }
        }
    }

    private fun getLastOrCreateReportInterval(session: AVService, currentTime: Date): ReportInterval {
        return getLastReportInterval(session) ?: startSessionReportInterval(session, currentTime)
    }

    private fun getLastReportInterval(session: AVService?): ReportInterval? {
        return session?.reportIntervals?.lastOrNull()?.takeIf { !it.isFinished }
    }

    private fun startSessionReportInterval(session: AVService, timestamp: Date): ReportInterval {
        return ReportInterval(
                startTime = timestamp,
                endTime = null,
                destinationDeviceType = DEVICE_TYPE_PRIMARY
        ).also {
            session.reportIntervals.add(it)
        }
    }

    private fun isIntervalLess10sec(startTime: Date, endTime: Date) = (endTime.time - startTime.time) < 10_000

    private fun isIntervalLess5sec(startTime: Date, endTime: Date) = (endTime.time - startTime.time) < 5_000

    /////////////////////////////////////////////////////////////////////////////////////////
    //          PERSISTENT CACHE
    /////////////////////////////////////////////////////////////////////////////////////////

    private inline fun QueueFile.clearOnError(action: () -> Unit) {
        try {
            action()
        } catch (t: Throwable) {
            LOG.e(TAG, "Analytic queue IO exception", t)
            clear()
        }
    }

    private fun addToStore(avService: AVService) {
        damp(avService)

        CoroutineScope(LOGGING_IO).launch {
            val bsid = avService.bsid
            val persistedStore = getStoreByBsid(bsid)
            val deviceLocation = repository.lastLocation.value ?: Location("none")
            persistedStore.clearOnError {
                suspendCancellableCoroutine<CacheEntry> { cont ->
                    if (persistedStore.size() >= MAX_CACHE_SIZE) {
                        persistedStore.remove()
                    }

                    val entry = CacheEntry(avService, deviceLocation)
                    persistedStore.add(gson.toJson(entry).toByteArray())

                    cont.resume(entry)
                }
            }

            if (persistedStore.size() >= CACHE_FULL_SIZE) {
                serverUrl?.let {
                    sendAllEventsAndReschedule(bsid, it)
                }
            }
        }
    }

    private fun createCDMJson(events: List<JSONObject>): JSONObject {
        //TODO: check location, divide events by location
        val location = events.mapNotNull { it.optJSONObject("location") }.firstOrNull()

        val services = events.map {
            it.getJSONObject("avService")
        }

        return JSONObject().apply {
            put("protocolVersion", "0x%02x".format(PROTOCOL_VERSION))
            put("deviceInfo", JSONObject().apply {
                put("deviceID", deviceId)
                put("deviceModel", android.os.Build.MODEL)
                put("deviceManufacturer", android.os.Build.MANUFACTURER)
                put("deviceOS", "Android")
                put("peripheralDevice", "FALSE")
                put("clockSource", clockSource)
                put("location", location)
            })
            put("avService", JSONArray(services))
        }
    }

    @Suppress("SameParameterValue")
    private suspend fun peekFromStore(store: QueueFile, maxCount: Int): List<JSONObject> {
        return withContext(LOGGING_IO) {
            var count = 0
            mutableListOf<JSONObject>().apply {
                val iterator = store.iterator()
                store.clearOnError {
                    while (count++ < maxCount && iterator.hasNext()) {
                        add(JSONObject(String(iterator.next())))
                    }
                }
            }
        }
    }

    override fun sendAllEvents(bsid: Int, reportServerUrl: String): Job {
        return CoroutineScope(SENDING_IO).launch {
            val persistedStore = getStoreByBsid(bsid)
            var events = peekFromStore(persistedStore, 5)
            while (isActive && events.isNotEmpty()) {
                val cdmJson = createCDMJson(events)
                val request = Request.Builder()
                        .url(reportServerUrl)
                        .addHeader("content-type", CONTENT_TYPE_JSON)
                        .post(cdmJson.toString().toRequestBody(MEDIA_TYPE_JSON))
                        .build()

                val succeed = try {
                    httpClient.newCall(request).await { response ->
                        if (response.isSuccessful) {
                            persistedStore.remove(events.size)
                            return@await true
                        } else {
                            return@await false
                        }
                    }
                } catch (e: IOException) {
                    LOG.d(TAG, "Can't send reports to: $reportServerUrl", e)
                    false
                }

                if (succeed) {
                    lastReportDate = LocalDateTime.now()
                } else {
                    cancel()
                    break
                }

                events = peekFromStore(persistedStore, 5)
            }
        }
    }

    private fun sendAllEventsAndReschedule(bsid: Int, reportServerUrl: String) {
        scheduler.cancelSchedule(bsid)

        sendAllEvents(bsid, reportServerUrl).invokeOnCompletion { exception ->
            val delay = if (exception != null) {
                TimeUnit.MINUTES.toSeconds(MIN_MINUTES_BEFORE_RETRY_REPORT)
            } else {
                TimeUnit.HOURS.toSeconds(MAX_HOURS_BEFORE_SEND_REPORT)
            }

            scheduler.scheduleWork(bsid, reportServerUrl, delay)
        }
    }

    private fun damp(avService: AVService) {
        if (!LOGGING) return

        LOG.d(TAG, gson.toJson(avService))
    }

    private suspend fun getStoreByBsid(bsid: Int): QueueFile {
        return storeMap[bsid] ?: lock.withLock {
            storeMap[bsid] ?: let {
                QueueFile.Builder(
                        File(cacheFolder, getStoreName(bsid))
                ).build().also {
                    storeMap[bsid] = it
                }
            }
        }
    }

    private fun getStoreName(bsid: Int) = "$bsid-$CACHE_FILE_NAME"

    private class CacheEntry(
            val avService: AVService,
            val location: Location
    )

    companion object {
        private val TAG: String = Atsc3Analytics::class.java.simpleName

        private const val LOGGING = false

        private const val BSID_REGISTRATION_COUNTRY = "us"
        private const val DEVICE_TYPE_PRIMARY = 0 // Content is presented on a Primary Device

        private val LOGGING_IO = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        private val SENDING_IO = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        private const val CACHE_FILE_NAME = "analytics.dat"
        private const val PROTOCOL_VERSION: Int = 0
        private const val MAX_CACHE_SIZE = 50
        private const val CACHE_FULL_SIZE = MAX_CACHE_SIZE * 0.8

        const val MAX_HOURS_BEFORE_SEND_REPORT = 24L
        const val MIN_MINUTES_BEFORE_RETRY_REPORT = 15L

        const val RETRY_DELAY_MINUTES = 1L
        const val MAX_RETRY_COUNT = 5

        const val CONTENT_TYPE_JSON = "application/json"
        val MEDIA_TYPE_JSON = "$CONTENT_TYPE_JSON; charset=utf-8".toMediaType()
    }
}

private class AVService(
        val country: String,
        val bsid: Int,
        val serviceID: Int,
        val globalServiceID: String?,
        val serviceType: Int
) {
    @SerializedName("reportInterval")
    val reportIntervals = mutableListOf<ReportInterval>()
}