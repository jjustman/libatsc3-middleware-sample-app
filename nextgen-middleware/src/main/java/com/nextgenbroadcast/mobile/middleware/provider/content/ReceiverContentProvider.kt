package com.nextgenbroadcast.mobile.middleware.provider.content

import android.content.*
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import com.nextgenbroadcast.mobile.core.MiddlewareConfig
import com.nextgenbroadcast.mobile.core.model.*
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants.INFO_DEVICE_ID
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants.INFO_FIRMWARE_VERSION
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants.INFO_PHY_TYPE
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants.INFO_SDK_VERSION
import com.nextgenbroadcast.mobile.middleware.*
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverCore
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverStandalone
import com.nextgenbroadcast.mobile.middleware.dev.config.DevConfig
import com.nextgenbroadcast.mobile.middleware.server.ServerUtils
import com.nextgenbroadcast.mobile.middleware.server.cert.IUserAgentSSLContext
import com.nextgenbroadcast.mobile.middleware.server.cert.UserAgentSSLContext
import com.nextgenbroadcast.mobile.middleware.service.Atsc3ForegroundService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.ByteBuffer

class ReceiverContentProvider : ContentProvider() {

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    private val stateScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private lateinit var receiver: Atsc3ReceiverCore
    private lateinit var authority: String
    private lateinit var appData: StateFlow<AppData?>
    private lateinit var rmpMediaUri: StateFlow<Uri?>
    private lateinit var rmpLayoutParams: StateFlow<RPMParams>
    private lateinit var rmpState: StateFlow<PlaybackState>

    private val appContext: Context  by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requireContext()
        } else {
            context ?: throw IllegalStateException("Provider $this not attached to a context.")
        }
    }

    private val sslContext: IUserAgentSSLContext by lazy {
        UserAgentSSLContext.newInstance(appContext)
    }

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false

        authority = appContext.getString(R.string.receiverContentProvider)
        receiver = Atsc3ReceiverStandalone.get(appContext)

        with(uriMatcher) {
            addURI(authority, CONTENT_RECEIVER_ROUTE_LIST, QUERY_RECEIVER_ROUTE_LIST)
            addURI(authority, CONTENT_RECEIVER_ROUTE, QUERY_RECEIVER_ROUTE)
            addURI(authority, CONTENT_RECEIVER_FREQUENCY, QUERY_RECEIVER_FREQUENCY)
            addURI(authority, CONTENT_RECEIVER_SERVICE, QUERY_RECEIVER_SERVICE)
            addURI(authority, CONTENT_RECEIVER_SERVICE_LIST, QUERY_RECEIVER_SERVICE_LIST)
            addURI(authority, CONTENT_PHY_VERSION_INFO, QUERY_PHY_VERSION_INFO)
            addURI(authority, CONTENT_APP_DATA, QUERY_APP_DATA)
            addURI(authority, CONTENT_APP_STATE, QUERY_APP_STATE)
            addURI(authority, CONTENT_SERVER_CERTIFICATE, QUERY_CERTIFICATE)
            addURI(authority, CONTENT_RECEIVER_STATE, QUERY_RECEIVER_STATE)
            addURI(authority, CONTENT_RECEIVER_MEDIA_PLAYER, QUERY_RECEIVER_MEDIA_PLAYER)
        }

        val contentResolver = appContext.contentResolver
        val repository = receiver.repository

        appData = repository.appData.stateIn(stateScope, SharingStarted.Eagerly, null)
        rmpMediaUri = repository.routeMediaUri.stateIn(stateScope, SharingStarted.Eagerly, null)
        rmpLayoutParams = repository.layoutParams
        rmpState = repository.requestedMediaState

        val receiverRouteListUri = getUriForPath(appContext, CONTENT_RECEIVER_ROUTE_LIST)
        stateScope.launch {
            repository.routes.collect {
                contentResolver.notifyChange(receiverRouteListUri, null)
            }
        }

        val receiverServiceUri = getUriForPath(appContext, CONTENT_RECEIVER_SERVICE)
        stateScope.launch {
            repository.selectedService.collect {
                contentResolver.notifyChange(receiverServiceUri, null)
            }
        }

        val receiverServiceListUri = getUriForPath(appContext, CONTENT_RECEIVER_SERVICE_LIST)
        stateScope.launch {
            receiver.observeRouteServices {
                contentResolver.notifyChange(receiverServiceListUri, null)
            }
        }

        val appDataUri = getUriForPath(appContext, CONTENT_APP_DATA)
        stateScope.launch {
            appData.collect {
                contentResolver.notifyChange(appDataUri, null)
            }
        }

        val mediaPlayerUri = getUriForPath(appContext, CONTENT_RECEIVER_MEDIA_PLAYER)
        stateScope.launch {
            rmpMediaUri.collect {
                contentResolver.notifyChange(mediaPlayerUri, null)
            }
        }
        stateScope.launch {
            rmpLayoutParams.collect {
                contentResolver.notifyChange(mediaPlayerUri, null)
            }
        }
        stateScope.launch {
            rmpState.collect {
                contentResolver.notifyChange(mediaPlayerUri, null)
            }
        }

        val receiverStateUri = getUriForPath(appContext, CONTENT_RECEIVER_STATE)
        stateScope.launch {
            receiver.observeReceiverState {
                contentResolver.notifyChange(receiverStateUri, null)
            }
        }

        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        return when (uriMatcher.match(uri)) {
            QUERY_RECEIVER_ROUTE_LIST -> {
                MatrixCursor(arrayOf(COLUMN_ROUTE_ID, COLUMN_ROUTE_PATH, COLUMN_ROUTE_NAME)).apply {
                    receiver.getSourceList().forEach { source ->
                        with(source) {
                            newRow()
                                .add(COLUMN_ROUTE_ID, id)
                                .add(COLUMN_ROUTE_PATH, path)
                                .add(COLUMN_ROUTE_NAME, title)
                        }
                    }
                }
            }

            QUERY_RECEIVER_FREQUENCY -> {
                MatrixCursor(arrayOf(COLUMN_FREQUENCY)).apply {
                    newRow().add(COLUMN_FREQUENCY, receiver.getFrequency())
                }
            }

            QUERY_RECEIVER_SERVICE -> {
                MatrixCursor(arrayOf(COLUMN_SERVICE_BSID, COLUMN_SERVICE_ID, COLUMN_SERVICE_GLOBAL_ID,
                    COLUMN_SERVICE_SHORT_NAME, COLUMN_SERVICE_CATEGORY, COLUMN_SERVICE_MAJOR_NO, COLUMN_SERVICE_MINOR_NO)).apply {
                    receiver.getSelectedService()?.let { service ->
                        with(service) {
                            newRow()
                                .add(COLUMN_SERVICE_BSID, bsid)
                                .add(COLUMN_SERVICE_ID, id)
                                .add(COLUMN_SERVICE_GLOBAL_ID, globalId)
                                .add(COLUMN_SERVICE_SHORT_NAME, shortName)
                                .add(COLUMN_SERVICE_CATEGORY, category)
                                .add(COLUMN_SERVICE_MAJOR_NO, majorChannelNo)
                                .add(COLUMN_SERVICE_MINOR_NO, minorChannelNo)
                        }
                    }
                }
            }

            QUERY_RECEIVER_SERVICE_LIST -> {
                MatrixCursor(arrayOf(COLUMN_SERVICE_BSID, COLUMN_SERVICE_ID, COLUMN_SERVICE_GLOBAL_ID,
                    COLUMN_SERVICE_SHORT_NAME, COLUMN_SERVICE_CATEGORY, COLUMN_SERVICE_MAJOR_NO,
                    COLUMN_SERVICE_MINOR_NO, COLUMN_SERVICE_DEFAULT)).apply {

                    val defaultService = if (MiddlewareConfig.DEV_TOOLS) {
                        DevConfig.get(appContext).service
                    } else null

                    receiver.getServiceList().forEach { service ->
                        with(service) {
                            newRow()
                                .add(COLUMN_SERVICE_BSID, bsid)
                                .add(COLUMN_SERVICE_ID, id)
                                .add(COLUMN_SERVICE_GLOBAL_ID, globalId)
                                .add(COLUMN_SERVICE_SHORT_NAME, shortName)
                                .add(COLUMN_SERVICE_CATEGORY, category)
                                .add(COLUMN_SERVICE_MAJOR_NO, majorChannelNo)
                                .add(COLUMN_SERVICE_MINOR_NO, minorChannelNo)
                                .add(COLUMN_SERVICE_DEFAULT, if (service.isServiceEquals(defaultService)) 1 else 0)
                        }
                    }
                }
            }

            QUERY_APP_DATA -> {
                MatrixCursor(arrayOf(COLUMN_APP_CONTEXT_ID, COLUMN_APP_BASE_URL, COLUMN_APP_BBAND_ENTRY_PAGE,
                    COLUMN_APP_BCAST_ENTRY_PAGE, COLUMN_APP_SERVICE_IDS, COLUMN_APP_CACHE_PATH)
                ).apply {
                    appData.value?.let { data ->
                        var d = data
                        if (MiddlewareConfig.DEV_TOOLS) {
                            DevConfig.get(appContext).applicationEntryPoint?.let { entryPage ->
                                d = d.copy(bBandEntryPageUrl = ServerUtils.appendSocketPathOrNull(entryPage, receiver.settings), bCastEntryPageUrl = null)
                            }
                        }

                        with(d) {
                            newRow().add(COLUMN_APP_CONTEXT_ID, contextId)
                                .add(COLUMN_APP_BASE_URL, baseUrl)
                                .add(COLUMN_APP_BBAND_ENTRY_PAGE, bBandEntryPageUrl)
                                .add(COLUMN_APP_BCAST_ENTRY_PAGE, bCastEntryPageUrl)
                                .add(COLUMN_APP_SERVICE_IDS, compatibleServiceIds.joinToString(" ") { it.toString() })
                                .add(COLUMN_APP_CACHE_PATH, cachePath)
                        }
                    }
                }
            }

            QUERY_RECEIVER_MEDIA_PLAYER -> {
                MatrixCursor(arrayOf(COLUMN_PLAYER_MEDIA_URL, COLUMN_PLAYER_LAYOUT_SCALE, COLUMN_PLAYER_LAYOUT_X, COLUMN_PLAYER_LAYOUT_Y, COLUMN_PLAYER_STATE)).apply {
                    with(rmpLayoutParams.value) {
                        newRow().add(COLUMN_PLAYER_MEDIA_URL, rmpMediaUri.value?.toString())
                            .add(COLUMN_PLAYER_LAYOUT_SCALE, scale)
                            .add(COLUMN_PLAYER_LAYOUT_X, x)
                            .add(COLUMN_PLAYER_LAYOUT_Y, y)
                            .add(COLUMN_PLAYER_STATE, rmpState.value.state)
                    }
                }
            }

            QUERY_CERTIFICATE -> {
                MatrixCursor(arrayOf(COLUMN_CERTIFICATE)).apply {
                    newRow().add(COLUMN_CERTIFICATE, sslContext.getCertificateHash())

                    if (MiddlewareConfig.DEV_TOOLS) {
                        DevConfig.get(appContext).serverCertHash?.let {
                            newRow().add(COLUMN_CERTIFICATE, it)
                        }
                    }
                }
            }

            QUERY_RECEIVER_STATE -> {
                MatrixCursor(arrayOf(COLUMN_STATE_CODE, COLUMN_STATE_INDEX, COLUMN_STATE_COUNT)).apply {
                    with(receiver.getReceiverState()) {
                        newRow().add(COLUMN_STATE_CODE, state.code)
                            .add(COLUMN_STATE_INDEX, configIndex)
                            .add(COLUMN_STATE_COUNT, configCount)
                    }
                }
            }

            QUERY_PHY_VERSION_INFO -> {
                val phyVersionInfo = receiver.getPhyVersionInfo()
                val deviceId = receiver.settings.deviceId
                MatrixCursor(arrayOf(INFO_DEVICE_ID, INFO_SDK_VERSION, INFO_FIRMWARE_VERSION, INFO_PHY_TYPE)).apply {
                    newRow().add(INFO_DEVICE_ID, deviceId)
                            .add(INFO_SDK_VERSION, phyVersionInfo[INFO_SDK_VERSION])
                            .add(INFO_FIRMWARE_VERSION, phyVersionInfo[INFO_FIRMWARE_VERSION])
                            .add(INFO_PHY_TYPE, phyVersionInfo[INFO_PHY_TYPE])
                }
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String {
        return "application/data"
    }

    override fun insert(uri: Uri, value: ContentValues?): Uri? {
        if (value != null) {
            when (uriMatcher.match(uri)) {
                QUERY_RECEIVER_ROUTE -> {
                    value.getAsString(COLUMN_ROUTE_PATH)?.let { path ->
                        context?.let { cxt ->
                            Atsc3ForegroundService.openRoute(cxt, path)
                        }
                    }
                }

                QUERY_RECEIVER_FREQUENCY -> {
                    if (value.containsKey(COLUMN_FREQUENCY)) {
                        value.getAsInteger(COLUMN_FREQUENCY)?.let { frequency ->
                            if (frequency >= 0) {
                                receiver.tune(PhyFrequency.user(listOf(frequency)))
                            } else {
                                receiver.cancelScanning()
                            }
                        }
                    } else if (value.containsKey(COLUMN_FREQUENCY_LIST)) {
                        value.getAsByteArray(COLUMN_FREQUENCY_LIST)?.let { frequencyArray ->
                            val buff = ByteBuffer.allocate(frequencyArray.size).apply {
                                put(frequencyArray)
                                rewind()
                            }
                            val frequencyList = mutableListOf<Int>().apply {
                                while (buff.remaining() >= Int.SIZE_BYTES) {
                                    add(buff.int)
                                }
                            }
                            receiver.tune(PhyFrequency.user(frequencyList))
                        }
                    }
                }

                QUERY_RECEIVER_SERVICE -> {
                    val service = if (value.containsKey(COLUMN_SERVICE_BSID) && value.containsKey(COLUMN_SERVICE_ID)) {
                        val bsid = value.getAsInteger(COLUMN_SERVICE_BSID)
                        val serviceId = value.getAsInteger(COLUMN_SERVICE_ID)
                        receiver.findServiceById(bsid, serviceId)
                    } else if (value.containsKey(COLUMN_SERVICE_GLOBAL_ID)) {
                        val globalServiceId = value.getAsString(COLUMN_SERVICE_GLOBAL_ID)
                        receiver.findServiceById(globalServiceId)
                    } else null

                    if (service != null) {
                        receiver.selectService(service)
                    }
                }

                QUERY_APP_STATE -> {
                    value.getAsString(COLUMN_APP_STATE_VALUE)?.let { stateStr ->
                        receiver.viewController.setApplicationState(ApplicationState.valueOf(stateStr))
                    }
                }

                QUERY_RECEIVER_MEDIA_PLAYER -> {
                    value.getAsInteger(COLUMN_PLAYER_STATE)?.let { stateCode ->
                        PlaybackState.valueOf(stateCode)
                    }?.let { playbackState ->
                        receiver.viewController.requestPlayerState(playbackState)
                    }
                }

                else -> throw IllegalArgumentException("Wrong URI: $uri")
            }
        }

        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        when (uriMatcher.match(uri)) {
            QUERY_RECEIVER_ROUTE -> {
                context?.let { cxt ->
                    Atsc3ForegroundService.closeRoute(cxt)
                    return 1
                }
            }

            QUERY_RECEIVER_MEDIA_PLAYER -> {
                receiver.repository.resetMediaSate()
                receiver.viewController.rmpPlaybackChanged(PlaybackState.IDLE)
            }
        }

        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (values != null) {
            when (uriMatcher.match(uri)) {
                QUERY_RECEIVER_MEDIA_PLAYER -> {
                    // Playback State
                    values.getAsInteger(COLUMN_PLAYER_STATE)?.let { stateCode ->
                        PlaybackState.valueOf(stateCode)
                    }?.let { playbackState ->
                        receiver.viewController.rmpPlaybackChanged(playbackState)
                    }

                    // Playback Rate
                    values.getAsFloat(COLUMN_PLAYER_RATE)?.let { playbackRate ->
                        receiver.viewController.rmpPlaybackRateChanged(playbackRate)
                    }

                    // Media Time
                    values.getAsLong( COLUMN_PLAYER_POSITION)?.let { mediaTime ->
                        receiver.viewController.rmpMediaTimeChanged(mediaTime)
                    }
                }
            }
        }

        return 0
    }

    companion object {
        private const val QUERY_RECEIVER_ROUTE_LIST = 1
        private const val QUERY_RECEIVER_ROUTE = 2
        private const val QUERY_RECEIVER_FREQUENCY = 3
        private const val QUERY_RECEIVER_SERVICE = 4
        private const val QUERY_RECEIVER_SERVICE_LIST = 5
        private const val QUERY_PHY_VERSION_INFO = 6
        private const val QUERY_APP_DATA = 7
        private const val QUERY_APP_STATE = 8
        private const val QUERY_CERTIFICATE = 9
        private const val QUERY_RECEIVER_STATE = 10
        private const val QUERY_RECEIVER_MEDIA_PLAYER = 11

        const val CONTENT_RECEIVER_ROUTE_LIST = "receiverRouteList"
        const val CONTENT_RECEIVER_ROUTE = "receiverOpenRoute"
        const val CONTENT_RECEIVER_FREQUENCY = "receiverFrequency"
        const val CONTENT_RECEIVER_SERVICE = "receiverService"
        const val CONTENT_RECEIVER_SERVICE_LIST = "receiverServiceList"
        const val CONTENT_PHY_VERSION_INFO = "receiverPhyInfo"
        const val CONTENT_APP_DATA = "appData"
        const val CONTENT_APP_STATE = "appState"
        const val CONTENT_SERVER_CERTIFICATE = "serverCertificate"
        const val CONTENT_RECEIVER_STATE = "receiverState"
        const val CONTENT_RECEIVER_MEDIA_PLAYER = "receiverMediaPlayer"

        const val COLUMN_APP_CONTEXT_ID = "appContextId"
        const val COLUMN_APP_BASE_URL = "appBaseUrl"
        const val COLUMN_APP_BBAND_ENTRY_PAGE = "appBBandEntryPage"
        const val COLUMN_APP_BCAST_ENTRY_PAGE = "appBCastEntryPage"
        const val COLUMN_APP_STATE_VALUE = "appStateValue"
        const val COLUMN_APP_SERVICE_IDS = "appServiceIds"
        const val COLUMN_APP_CACHE_PATH = "appCachePath"

        const val COLUMN_CERTIFICATE = "certificate"

        const val COLUMN_STATE_CODE = "receiverStateValue"
        const val COLUMN_STATE_INDEX = "receiverStateIndex"
        const val COLUMN_STATE_COUNT = "receiverStateCount"
        const val COLUMN_ROUTE_ID = "receiverRouteId"
        const val COLUMN_ROUTE_PATH = "receiverRoutePath"
        const val COLUMN_ROUTE_NAME = "receiverRouteName"
        const val COLUMN_FREQUENCY = "receiverFrequency"
        const val COLUMN_FREQUENCY_LIST = "receiverFrequencyList"

        const val COLUMN_SERVICE_BSID = "receiverServiceBsid"
        const val COLUMN_SERVICE_ID = "receiverServiceId"
        const val COLUMN_SERVICE_GLOBAL_ID = "receiverServiceGlobalId"
        const val COLUMN_SERVICE_SHORT_NAME = "receiverServiceShortName"
        const val COLUMN_SERVICE_CATEGORY = "receiverServiceCategory"
        const val COLUMN_SERVICE_MAJOR_NO = "receiverServiceMajorNo"
        const val COLUMN_SERVICE_MINOR_NO = "receiverServiceMinorNo"
        const val COLUMN_SERVICE_DEFAULT = "receiverServiceDefault"

        const val COLUMN_PLAYER_MEDIA_URL = "receiverPlayerMediaUrl"
        const val COLUMN_PLAYER_LAYOUT_SCALE = "receiverPlayerLayoutScale"
        const val COLUMN_PLAYER_LAYOUT_X = "receiverPlayerLayoutX"
        const val COLUMN_PLAYER_LAYOUT_Y = "receiverPlayerLayoutY"
        const val COLUMN_PLAYER_STATE = "receiverPlayerState"
        const val COLUMN_PLAYER_POSITION = "receiverPlayerPosition"
        const val COLUMN_PLAYER_RATE = "receiverPlayerRate"

        fun getUriForPath(context: Context, path: String): Uri {
            return Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(context.getString(R.string.receiverContentProvider))
                    .encodedPath(path).build()
        }
    }
}