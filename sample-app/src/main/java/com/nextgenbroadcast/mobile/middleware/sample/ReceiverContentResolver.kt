package com.nextgenbroadcast.mobile.middleware.sample

import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.database.getDoubleOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.core.net.toUri
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants
import com.nextgenbroadcast.mobile.core.model.*
import java.nio.ByteBuffer

class ReceiverContentResolver(
    context: Context
) {
    private val contentResolver = context.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    private val observers = mutableListOf<ContentObserver>()

    private val receiverRouteListUri = getUriForPath(context, CONTENT_RECEIVER_ROUTE_LIST)
    private val receiverRouteUri = getUriForPath(context, CONTENT_RECEIVER_ROUTE)
    private val receiverFrequencyUri = getUriForPath(context, CONTENT_RECEIVER_FREQUENCY)
    private val receiverServiceUri = getUriForPath(context, CONTENT_RECEIVER_SERVICE)
    private val receiverServiceListUri = getUriForPath(context, CONTENT_RECEIVER_SERVICE_LIST)
    private val appDataUri = getUriForPath(context, CONTENT_APP_DATA)
    private val appStateUri = getUriForPath(context, CONTENT_APP_STATE)
    private val receiverPlayerUri = getUriForPath(context, CONTENT_RECEIVER_MEDIA_PLAYER)
    private val certificateUri = getUriForPath(context, CONTENT_SERVER_CERTIFICATE)
    private val receiverStateUri = getUriForPath(context, CONTENT_RECEIVER_STATE)
    private val receiverPhyInfoUri = getUriForPath(context, CONTENT_PHY_VERSION_INFO)

    private var providerClient: ContentProviderClient? = null

    fun register() {
        providerClient = contentResolver.acquireContentProviderClient(appDataUri)
    }

    fun unregister() {
        observers.forEach { observer ->
            contentResolver.unregisterContentObserver(observer)
        }
        providerClient?.close()
        providerClient = null
    }

    fun observeRouteList(block: (List<RouteUrl>) -> Unit) {
        block(queryRouteList())
        contentResolver.registerContentObserver(receiverRouteListUri) {
            block(queryRouteList())
        }
    }

    fun observeServiceList(block: (List<AVService>) -> Unit) {
        block(queryServiceList())
        contentResolver.registerContentObserver(receiverServiceListUri) {
            block(queryServiceList())
        }
    }

    fun observeServiceSelection(block: (AVService?) -> Unit) {
        block(querySelectedService())
        contentResolver.registerContentObserver(receiverServiceUri) {
            block(querySelectedService())
        }
    }

    fun observeApplicationData(block: (AppData?) -> Unit) {
        block(queryAppData())
        contentResolver.registerContentObserver(appDataUri) {
            block(queryAppData())
        }
    }

    fun observePlayerState(block: (Uri?, RPMParams, PlaybackState) -> Unit) {
        queryPlayerData()?.let { (mediaUri, layoutParams, state) ->
            block(mediaUri, layoutParams, state)
        }
        contentResolver.registerContentObserver(receiverPlayerUri) {
            queryPlayerData()?.let { (mediaUri, layoutParams, state) ->
                block(mediaUri, layoutParams, state)
            }
        }
    }

    fun observeReceiverState(block: (ReceiverState) -> Unit) {
        block(queryReceiverState() ?: ReceiverState.idle())
        contentResolver.registerContentObserver(receiverStateUri) {
            block(queryReceiverState() ?: ReceiverState.idle())
        }
    }

    fun openRoute(path: String) {
        receiverRouteUri.insert {
            it.put(COLUMN_ROUTE_PATH, path)
        }
    }

    fun closeRoute() {
        receiverRouteUri.delete()
    }

    fun tune(freqKhz: Int) {
        receiverFrequencyUri.insert {
            it.put(COLUMN_FREQUENCY, freqKhz)
        }
    }

    fun tune(freqKhzList: List<Int>) {
        val frequencyListBytes = ByteBuffer.allocate(freqKhzList.size * Int.SIZE_BYTES).apply {
            freqKhzList.forEach {
                putInt(it)
            }
            rewind()
        }.array()

        receiverFrequencyUri.insert {
            it.put(COLUMN_FREQUENCY_LIST, frequencyListBytes)
        }
    }

    fun selectService(service: AVService) {
        receiverServiceUri.insert {
            it.put(COLUMN_SERVICE_BSID, service.bsid)
            it.put(COLUMN_SERVICE_ID, service.id)
            it.put(COLUMN_SERVICE_GLOBAL_ID, service.globalId)
        }
    }

    fun applyPlayerState(state: PlaybackState) {
        receiverPlayerUri.insert {
            it.put(COLUMN_PLAYER_STATE, state.state)
        }
    }

    fun resetPlayerState() {
        receiverPlayerUri.delete()
    }

    fun publishApplicationState(state: ApplicationState) {
        appStateUri.insert {
            it.put(COLUMN_APP_STATE_VALUE, state.name)
        }
    }

    fun publishPlayerState(state: PlaybackState, position: Long, rate: Float) {
        receiverPlayerUri.update {
            it.put(COLUMN_PLAYER_STATE, state.state)
            it.put(COLUMN_PLAYER_POSITION, position)
            it.put(COLUMN_PLAYER_RATE, rate)
        }
    }

    fun queryRouteList(): List<RouteUrl> {
        return mutableListOf<RouteUrl>().apply {
            receiverRouteListUri.query { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getStringOrNull(COLUMN_ROUTE_ID) ?: continue
                    val path = cursor.getStringOrNull(COLUMN_ROUTE_PATH) ?: continue
                    val title = cursor.getStringOrNull(COLUMN_ROUTE_NAME) ?: id

                    add(RouteUrl(id, path, title))
                }
            }
        }
    }

    fun querySelectedService(): AVService? {
        return receiverServiceUri.queryFirst { cursor ->
            val bsid = cursor.getIntOrNull(COLUMN_SERVICE_BSID) ?: return null
            val serviceId = cursor.getIntOrNull(COLUMN_SERVICE_ID) ?: return null
            val serviceGlobalId = cursor.getStringOrNull(COLUMN_SERVICE_GLOBAL_ID)
            val shortName = cursor.getStringOrNull(COLUMN_SERVICE_SHORT_NAME)
            val category = cursor.getIntOrNull(COLUMN_SERVICE_CATEGORY) ?: return null
            val majorChannelNo = cursor.getIntOrNull(COLUMN_SERVICE_MAJOR_NO) ?: 0
            val minorChannelNo = cursor.getIntOrNull(COLUMN_SERVICE_MINOR_NO) ?: 0

            AVService(bsid, serviceId, shortName, serviceGlobalId, majorChannelNo, minorChannelNo, category)
        }
    }

    fun queryServiceList(): List<AVService> {
        return mutableListOf<AVService>().apply {
            receiverServiceListUri.query { cursor ->
                while (cursor.moveToNext()) {
                    val bsid = cursor.getIntOrNull(COLUMN_SERVICE_BSID) ?: continue
                    val serviceId = cursor.getIntOrNull(COLUMN_SERVICE_ID) ?: continue
                    val serviceGlobalId = cursor.getStringOrNull(COLUMN_SERVICE_GLOBAL_ID)
                    val shortName = cursor.getStringOrNull(COLUMN_SERVICE_SHORT_NAME)
                    val category = cursor.getIntOrNull(COLUMN_SERVICE_CATEGORY) ?: continue
                    val majorChannelNo = cursor.getIntOrNull(COLUMN_SERVICE_MAJOR_NO) ?: 0
                    val minorChannelNo = cursor.getIntOrNull(COLUMN_SERVICE_MINOR_NO) ?: 0
                    val isDefault = cursor.getIntOrNull(COLUMN_SERVICE_DEFAULT) == 1

                    add(AVService(bsid, serviceId, shortName, serviceGlobalId, majorChannelNo, minorChannelNo, category, default = isDefault))
                }
            }
        }
    }

    fun queryServerCertificate(): List<String>? {
        return certificateUri.query { cursor ->
            mutableListOf<String>().apply {
                while (cursor.moveToNext()) {
                    cursor.getStringOrNull(COLUMN_CERTIFICATE)?.let {
                        add(it)
                    }
                }
            }
        }
    }

    fun queryReceiverFrequency(): Int? {
        return receiverFrequencyUri.queryFirst { cursor ->
            cursor.getIntOrNull(COLUMN_FREQUENCY)
        }
    }

    fun queryAppData(): AppData? {
        return appDataUri.queryFirst { cursor ->
            val appContextID = cursor.getStringOrNull(COLUMN_APP_CONTEXT_ID)
            val appBaseUrl = cursor.getStringOrNull(COLUMN_APP_BASE_URL)
            val appBBandEntryPage = cursor.getStringOrNull(COLUMN_APP_BBAND_ENTRY_PAGE)
            val appBCastEntryPage = cursor.getStringOrNull(COLUMN_APP_BCAST_ENTRY_PAGE)
            val appServiceIds = cursor.getStringOrNull(COLUMN_APP_SERVICE_IDS)
            val appCachePath = cursor.getStringOrNull(COLUMN_APP_CACHE_PATH)

            if (appContextID != null && appBaseUrl != null) {
                val serviceIds = appServiceIds?.split(" ")?.mapNotNull {
                    it.toIntOrNull()
                } ?: emptyList()

                AppData(appContextID, appBaseUrl, appBBandEntryPage, appBCastEntryPage,  serviceIds, appCachePath)
            } else null
        }
    }

    //TODO: combine to one local model
    fun queryPlayerData(): Triple<Uri?, RPMParams, PlaybackState>? {
        return receiverPlayerUri.queryFirst { cursor ->
            val mediaUri = cursor.getStringOrNull(COLUMN_PLAYER_MEDIA_URL)?.toUri()
            val scale = cursor.getDoubleOrNull(COLUMN_PLAYER_LAYOUT_SCALE) ?: 100.0
            val x = cursor.getIntOrNull(COLUMN_PLAYER_LAYOUT_X) ?: 0
            val y = cursor.getIntOrNull(COLUMN_PLAYER_LAYOUT_Y) ?: 0
            val state = cursor.getIntOrNull(COLUMN_PLAYER_STATE)?.let {
                PlaybackState.valueOf(it)
            } ?: PlaybackState.IDLE

            Triple(mediaUri, RPMParams(scale, x, y), state)
        }
    }

    fun queryReceiverState(): ReceiverState? {
        return receiverStateUri.queryFirst { cursor ->
            val stateCode = cursor.getIntOrNull(COLUMN_STATE_CODE)
            val stateIndex = cursor.getIntOrNull(COLUMN_STATE_INDEX)
            val stateCount = cursor.getIntOrNull(COLUMN_STATE_COUNT)

            val state = ReceiverState.State.values().firstOrNull { it.code == stateCode }
            if (state != null) {
                ReceiverState(state, stateIndex ?: 0, stateCount ?: 0)
            } else null
        }
    }

    fun getPhyInfo(): Map<String, String?>? {
        return receiverPhyInfoUri.queryFirst { cursor ->
            mutableMapOf<String, String?>().apply {
                put(PhyInfoConstants.INFO_DEVICE_ID, cursor.getStringOrNull(PhyInfoConstants.INFO_DEVICE_ID))
                put(PhyInfoConstants.INFO_SDK_VERSION, cursor.getStringOrNull(PhyInfoConstants.INFO_SDK_VERSION))
                put(PhyInfoConstants.INFO_FIRMWARE_VERSION, cursor.getStringOrNull(PhyInfoConstants.INFO_FIRMWARE_VERSION))
                put(PhyInfoConstants.INFO_PHY_TYPE, cursor.getStringOrNull(PhyInfoConstants.INFO_PHY_TYPE))
            }
        }
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        return getColumnIndexOrNull(columnName)?.let { index ->
            getStringOrNull(index)
        }
    }

    private fun Cursor.getIntOrNull(columnName: String): Int? {
        return getColumnIndexOrNull(columnName)?.let { index ->
            getIntOrNull(index)
        }
    }

    private fun Cursor.getDoubleOrNull(columnName: String): Double? {
        return getColumnIndexOrNull(columnName)?.let { index ->
            getDoubleOrNull(index)
        }
    }

    private fun Cursor.getColumnIndexOrNull(columnName: String): Int? {
        return getColumnIndex(columnName).takeIf { it >= 0 }
    }

    private inline fun ContentResolver.registerContentObserver(uri: Uri, crossinline onChange: () -> Unit) {
        registerContentObserver(uri, false,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) = onChange()
            }.also {
                observers.add(it)
            }
        )
    }

    private inline fun <R> Uri.queryFirst(action: (cursor: Cursor) -> R?): R? {
        return providerClient?.query(this, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) action(cursor) else null
        }
    }

    private inline fun <R> Uri.query(action: (cursor: Cursor) -> R?): R? {
        return providerClient?.query(this, null, null, null)?.use { cursor ->
            action(cursor)
        }
    }

    private inline fun Uri.insert(action: (values: ContentValues) -> Unit) {
        providerClient?.insert(this, ContentValues().apply {
            action(this)
        })
    }

    private inline fun Uri.update(action: (values: ContentValues) -> Unit) {
        providerClient?.update(this, ContentValues().apply {
            action(this)
        }, null, null)
    }

    private fun Uri.delete() {
        providerClient?.delete(this, null, null)
    }

    companion object {
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

        fun getDeviceId(context: Context): String? {
            val uri = getUriForPath(context, CONTENT_PHY_VERSION_INFO)
            return context.contentResolver.query(uri, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getStringOrNull(cursor.getColumnIndex(PhyInfoConstants.INFO_DEVICE_ID))
                } else null
            }
        }

        fun openRoute(context: Context, uri: Uri) {
            context.contentResolver.insert(
                getUriForPath(context, CONTENT_RECEIVER_ROUTE),
                ContentValues().apply {
                    put(COLUMN_ROUTE_PATH, uri.toString())
                })
        }

        fun resetPlayerState(context: Context) {
            context.contentResolver.delete(
                getUriForPath(context, CONTENT_RECEIVER_MEDIA_PLAYER),
                null, null
            )
        }
    }
}