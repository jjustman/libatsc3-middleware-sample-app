package com.nextgenbroadcast.mobile.middleware.repository

import android.location.Location
import android.net.Uri
import com.nextgenbroadcast.mobile.core.model.*
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts.AeaTable
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3Application
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.Atsc3HeldPackage
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.SGUrl
import com.nextgenbroadcast.mobile.middleware.controller.PlaybackSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface IRepository {
    // Receiver
    val routes: StateFlow<List<RouteUrl>>
    val services: StateFlow<List<AVService>>
    val selectedService: StateFlow<AVService?>
    val serviceGuideUrls: StateFlow<List<SGUrl>>
    val alertsForNotify: StateFlow<List<AeaTable>>
    val lastLocation: StateFlow<Location?>

    // Media Player
    val routeMediaUrl: StateFlow<MediaUrl?>
    val externalMediaUrl: StateFlow<String?>
    val playbackSource: StateFlow<PlaybackSource>
    val layoutParams: StateFlow<RPMParams>
    val requestedMediaState: StateFlow<PlaybackState>
    val routeMediaUri: Flow<Uri?>

    // User Agent
    val applications: StateFlow<List<Atsc3Application>>
    val heldPackage: StateFlow<Atsc3HeldPackage?>
    val appData: Flow<AppData?>

    fun setAlertList(newAlerts: List<AeaTable>)
    fun updateLastLocation(location: Location?)
    fun reset()

    fun addOrUpdateApplication(application: Atsc3Application)
    fun findApplication(appContextId: String): Atsc3Application?

    fun setRoutes(soutes: List<RouteUrl>)
    fun setServices(services: List<AVService>)
    fun setSelectedService(service: AVService?)
    fun findServiceBy(globalServiceId: String): AVService?
    fun findServiceBy(bsid: Int, serviceId: Int): AVService?
    fun findServiceOrNull(predicate: (AVService) -> Boolean): AVService?

    fun setHeldPackage(data: Atsc3HeldPackage?): Boolean
    fun setMediaUrl(mediaUrl: MediaUrl?)
    fun setLayoutParams(params: RPMParams)
    fun setExternalMediaUrl(mediaUrl: String?)
    fun setRequestedMediaState(state: PlaybackState)
    fun resetMediaSate()

    fun incSessionNum()
    fun getRouteMediaUri(routeMediaUrl: MediaUrl): Uri
}