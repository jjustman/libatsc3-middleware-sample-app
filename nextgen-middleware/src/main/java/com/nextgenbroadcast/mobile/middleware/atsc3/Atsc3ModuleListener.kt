package com.nextgenbroadcast.mobile.middleware.atsc3

import com.nextgenbroadcast.mobile.core.model.MediaUrl
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts.AeaTable
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3Application
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.Atsc3HeldPackage
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.service.Atsc3Service

interface Atsc3ModuleListener {
    fun onStateChanged(state: Atsc3ModuleState)
    fun onConfigurationChanged(index: Int, count: Int, isKnown: Boolean)

    fun onApplicationPackageReceived(appPackage: Atsc3Application)

    fun onServiceLocationTableChanged(bsid: Int, services: List<Atsc3Service>, reportServerUrl: String?)
    fun onServicePackageChanged(pkg: Atsc3HeldPackage?)
    fun onServiceMediaReady(mediaUrl: MediaUrl, delayBeforePlayMs: Long)
    fun onServiceGuideUnitReceived(filePath: String, bsid: Int)

    fun onError(message: String)
    fun onAeatTableChanged(list: List<AeaTable>)
}