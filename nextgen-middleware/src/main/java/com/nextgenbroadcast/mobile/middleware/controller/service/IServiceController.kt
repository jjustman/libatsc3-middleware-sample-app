package com.nextgenbroadcast.mobile.middleware.controller.service

import com.nextgenbroadcast.mobile.core.model.MediaUrl
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.model.PhyFrequency
import com.nextgenbroadcast.mobile.core.model.ReceiverState
import com.nextgenbroadcast.mobile.middleware.atsc3.source.IAtsc3Source
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

internal interface IServiceController {
    val receiverState: StateFlow<ReceiverState>
    val receiverFrequency: StateFlow<Int>
    val routeServices: StateFlow<List<AVService>>

    val errorFlow: SharedFlow<String>

    suspend fun openRoute(source: IAtsc3Source, force: Boolean = false): Boolean
    suspend fun closeRoute()
    suspend fun tune(frequency: PhyFrequency)
    suspend fun selectService(service: AVService): Boolean
    suspend fun cancelScanning()

    fun findServiceById(globalServiceId: String): AVService?
    fun getNearbyService(offset: Int): AVService?
    fun getCurrentService(): AVService?
    fun getCurrentRouteMediaUrl(): MediaUrl?
}