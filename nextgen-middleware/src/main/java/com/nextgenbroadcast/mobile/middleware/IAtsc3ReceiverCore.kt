package com.nextgenbroadcast.mobile.middleware

import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.model.PhyFrequency
import com.nextgenbroadcast.mobile.core.model.ReceiverState
import com.nextgenbroadcast.mobile.core.model.RouteUrl
import com.nextgenbroadcast.mobile.middleware.atsc3.source.IAtsc3Source

internal interface IAtsc3ReceiverCore {
    fun setRouteList(routes: List<RouteUrl>)
    fun openRoute(source: IAtsc3Source, force: Boolean = false, onOpen: suspend (result: Boolean) -> Unit = {})
    fun closeRoute()
    fun tune(frequency: PhyFrequency)
    fun selectService(service: AVService, block: suspend (result: Boolean) -> Unit = {})
    fun cancelScanning()

    fun getReceiverState(): ReceiverState

    fun getPhyVersionInfo(): Map<String, String?>
}