package com.nextgenbroadcast.mobile.middleware.controller.service

import android.location.Location
import android.util.Log
import com.nextgenbroadcast.mobile.core.model.MediaUrl
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.model.PhyFrequency
import com.nextgenbroadcast.mobile.core.model.ReceiverState
import com.nextgenbroadcast.mobile.middleware.analytics.IAtsc3Analytics
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3ModuleListener
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3ModuleState
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3Profile
import com.nextgenbroadcast.mobile.middleware.atsc3.IAtsc3Module
import com.nextgenbroadcast.mobile.core.atsc3.SLTConstants
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts.AeaTable
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3Application
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.Atsc3HeldPackage
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.service.Atsc3Service
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.IServiceGuideDeliveryUnitReader
import com.nextgenbroadcast.mobile.middleware.atsc3.source.*
import com.nextgenbroadcast.mobile.middleware.cache.IApplicationCache
import com.nextgenbroadcast.mobile.middleware.repository.IRepository
import com.nextgenbroadcast.mobile.middleware.settings.IMiddlewareSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit


internal class ServiceControllerImpl(
        private val repository: IRepository,
        private val settings: IMiddlewareSettings,
        private val atsc3Module: IAtsc3Module,
        private val atsc3Analytics: IAtsc3Analytics,
        private val serviceGuideReader: IServiceGuideDeliveryUnitReader,
        private val appCache: IApplicationCache,
        private val stateScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : IServiceController, Atsc3ModuleListener {

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val atsc3ScopeLock = Mutex()

    private var heldResetJob: Job? = null
    private var mediaUrlAssignmentJob: Job? = null

    private val atsc3State = MutableStateFlow<Atsc3ModuleState?>(Atsc3ModuleState.IDLE)
    private val atsc3Configuration = MutableStateFlow<Triple<Int, Int, Boolean>?>(null)

    override val receiverState: StateFlow<ReceiverState> = combine(atsc3State, repository.selectedService, repository.services, atsc3Configuration) { state, service, services, config ->
        val (configIndex, configCount, configKnown) = config ?: Triple(-1, -1, false)
        if (state == null || state == Atsc3ModuleState.IDLE) {
            ReceiverState.idle()
        } else if (state == Atsc3ModuleState.SCANNING
            // converts last config index in scanning series (ignoring known configs) to scanning state
            || (state == Atsc3ModuleState.SNIFFING && !configKnown && configCount > 1)
        ) {
            ReceiverState.scanning(configIndex, configCount)
        } else if (services.isEmpty()) {
            ReceiverState.tuning(configIndex, configCount)
        } else if (service == null) {
            ReceiverState.ready(configIndex, configCount)
        } else {
            ReceiverState.buffering(configIndex, configCount)
        }
    }.stateIn(stateScope, SharingStarted.Eagerly, ReceiverState.idle())

    override val receiverFrequency = MutableStateFlow(0)

    override val routeServices: StateFlow<List<AVService>> = repository.services.map { services ->
        services.filter { !it.hidden }
    }.stateIn(stateScope, SharingStarted.Eagerly, emptyList())

    override val errorFlow = MutableSharedFlow<String>(0, 10, BufferOverflow.DROP_OLDEST)

    init {
        atsc3Module.setListener(this)
    }

    override fun onStateChanged(state: Atsc3ModuleState) {
        mainScope.launch {
            if (state == Atsc3ModuleState.IDLE) {
                repository.reset()
            }
            atsc3State.value = state
        }
    }

    override fun onConfigurationChanged(index: Int, count: Int, isKnown: Boolean) {
        atsc3Configuration.value = Triple(index, count, isKnown)
    }

    override fun onApplicationPackageReceived(appPackage: Atsc3Application) {
        Log.d("ServiceControllerImpl", "onPackageReceived - appPackage: $appPackage")
        mainScope.launch {
            repository.addOrUpdateApplication(appPackage)
        }
    }

    override fun onServiceLocationTableChanged(bsid: Int, services: List<Atsc3Service>, reportServerUrl: String?) {
        // store A/V services
        val avServices = services.filter { service ->
            service.serviceCategory == SLTConstants.SERVICE_CATEGORY_AV
                    || service.serviceCategory == SLTConstants.SERVICE_CATEGORY_AO
                    || service.serviceCategory == SLTConstants.SERVICE_CATEGORY_ABS
        }.map {
            AVService(
                    it.bsid,
                    it.serviceId,
                    it.shortServiceName,
                    it.globalServiceId,
                    it.majorChannelNo,
                    it.minorChannelNo,
                    it.serviceCategory,
                    it.hidden
            )
        }

        mainScope.launch {
            atsc3Analytics.setReportServerUrl(bsid, reportServerUrl)

            val oldServices = repository.services.value
            if (oldServices.size != avServices.size || !oldServices.containsAll(avServices)) {
                storeCurrentProfile()
            }

            repository.setServices(avServices)

            // select ESG service
            services.firstOrNull { service ->
                service.serviceCategory == SLTConstants.SERVICE_CATEGORY_ESG
            }?.let { service ->
                atsc3Module.selectAdditionalService(service.serviceId)
            }
        }
    }

    override fun onServicePackageChanged(pkg: Atsc3HeldPackage?) {
        mainScope.launch {
            cancelHeldReset()
            val updated = repository.setHeldPackage(pkg)
            if (updated) {
                repository.resetMediaSate()
            }
        }
    }

    override fun onServiceMediaReady(mediaUrl: MediaUrl, delayBeforePlayMs: Long) {
        Log.e(TAG, "onServiceMediaReady with mediaUrl: $mediaUrl, delayBeforePlayMs: $delayBeforePlayMs")
        mainScope.launch {
            if (delayBeforePlayMs > 0) {
                setMediaUrlWithDelay(mediaUrl, delayBeforePlayMs)
            } else {
                repository.setMediaUrl(mediaUrl)
            }
        }
    }

    override fun onServiceGuideUnitReceived(filePath: String, bsid: Int) {
        mainScope.launch {
            serviceGuideReader.readDeliveryUnit(filePath, bsid)
        }
    }

    override fun onError(message: String) {
        mainScope.launch {
            errorFlow.emit(message)
        }
    }

    override fun onAeatTableChanged(list: List<AeaTable>) {
        mainScope.launch {
            repository.setAlertList(list)
            list.mapNotNull { aeaTable ->
                aeaTable.alternateUrlList
            }.flatten().also { urlList ->
                appCache.prefetchFiles(urlList)
            }
        }
    }

    override suspend fun openRoute(source: IAtsc3Source, force: Boolean): Boolean {
        return withContext(Dispatchers.Main) {
            val opened = atsc3ScopeLock.withLock {
                if (force || atsc3Module.isIdle()) {
                    clearRouteData()

                    val lastProfile = getLastProfileForSource(source)
                    if (atsc3Module.open(source, lastProfile?.configs)) {
                        return@withLock true
                    }
                }
                return@withLock false
            }

            if (opened && source is ITunableSource) {
                tune(PhyFrequency.default(PhyFrequency.Source.AUTO))
            }

            opened
        }
    }

    override suspend fun closeRoute() {
        atsc3Module.close()

        withContext(Dispatchers.Main) {
            clearRouteData()
        }
    }

    private fun clearRouteData() {
        atsc3Analytics.finishSession()

        cancelMediaUrlAssignment()

        serviceGuideReader.clearAll()
        repository.reset()
    }

    override suspend fun selectService(service: AVService): Boolean {
        return withContext(Dispatchers.Main) {
            if (atsc3Module.isServiceSelected(service.bsid, service.id)) return@withContext true

            val res = atsc3ScopeLock.withLock {
                // Reset current media. New media url will be received after service selection.
                resetMediaUrl()

                // Reset the current HELD if it's not compatible new service or start delayed reset otherwise.
                // Delayed reset will be canceled when a new HELD been received for selected service.
                repository.heldPackage.value?.let { currentHeld ->
                    // Is new service compatible with current HELD?
                    //TODO: we should check held broadcaster, but don't have it in held
                    if (currentHeld.coupledServices?.contains(service.id) == true) {
                        resetHeldWithDelay()
                    } else {
                        repository.setHeldPackage(null)
                    }
                }

                atsc3Module.selectService(service.bsid, service.id)
            }

            if (res) {
                atsc3Analytics.startSession(service.bsid, service.id, service.globalId, service.category)

                // Store successfully selected service. This will lead to RMP reset
                repository.setSelectedService(service)
            } else {
                cancelHeldReset()
                // Reset HELD and service if service can't be selected
                repository.setHeldPackage(null)
                repository.setSelectedService(null)
            }
            repository.resetMediaSate()

            res
        }
    }

    override suspend fun cancelScanning() {
        atsc3Module.cancelScanning()
    }

    override suspend fun tune(frequency: PhyFrequency) {
        withContext(Dispatchers.Main) {
            atsc3ScopeLock.withLock {
                val frequencyList: List<Int> = if (frequency.list.isEmpty()) {
                    settings.lastFrequency
                } else {
                    frequency.list
                }

                val freqKhz = frequencyList.firstOrNull() ?: return@withContext

                val forceTune = (frequency.source == PhyFrequency.Source.USER)
                if (forceTune) {
                    // Reset current media. New media url will be received after service selection.
                    resetMediaUrl()
                }

                val tuned = atsc3Module.tune(frequencyList, forceTune)
                if (tuned) {
                    // Store the first one because it will be used as default
                    receiverFrequency.value = freqKhz
                    settings.lastFrequency = frequencyList
                }
            }
        }
    }

    override fun findServiceById(globalServiceId: String): AVService? {
        return repository.findServiceBy(globalServiceId)
    }

    override fun getNearbyService(offset: Int): AVService? {
        return repository.selectedService.value?.let { activeService ->
            routeServices.value.let { services ->
                val activeServiceIndex = services.indexOf(activeService)
                services.getOrNull(activeServiceIndex + offset)
            }
        }
    }

    override fun getCurrentService(): AVService? {
        return repository.selectedService.value
    }

    override fun getCurrentRouteMediaUrl(): MediaUrl? {
        return repository.routeMediaUrl.value
    }

    private fun resetMediaUrl() {
        cancelMediaUrlAssignment()
        repository.setMediaUrl(null)
    }

    private fun resetHeldWithDelay() {
        cancelHeldReset()
        heldResetJob = mainScope.launch {
            delay(BA_LOADING_TIMEOUT)
            repository.setHeldPackage(null)
            repository.resetMediaSate()
            heldResetJob = null
        }
    }

    private fun cancelHeldReset() {
        heldResetJob?.let {
            it.cancel()
            heldResetJob = null
        }
    }

    private fun setMediaUrlWithDelay(mediaUrl: MediaUrl, delayMs: Long) {
        cancelMediaUrlAssignment()
        mediaUrlAssignmentJob = mainScope.launch {
            delay(delayMs)
            repository.setMediaUrl(mediaUrl)
            mediaUrlAssignmentJob = null
        }
    }

    private fun cancelMediaUrlAssignment() {
        mediaUrlAssignmentJob?.let {
            it.cancel()
            mediaUrlAssignmentJob = null
        }
    }

    private fun getLastProfileForSource(source: IAtsc3Source): Atsc3Profile? {
        val profile = settings.receiverProfile
        return profile?.let {
            val elapsedTime = System.currentTimeMillis() - profile.timestamp
            val deviceLocation = repository.lastLocation.value
            val profileLocation = Location("unknown").apply {
                latitude = profile.location.lat
                longitude = profile.location.lng
            }
            if (source::class.java.simpleName == profile.sourceType
                    && deviceLocation != null
                    && deviceLocation.distanceTo(profileLocation) < PROFILE_LOCATION_RADIUS
                    && elapsedTime > 0 && elapsedTime < PROFILE_LIFE_TIME) {
                profile
            } else {
                null
            }
        }
    }

    private suspend fun storeCurrentProfile() {
        val location = repository.lastLocation.value
        if (location != null && location.latitude != 0.0 && location.longitude != 0.0) {
            settings.receiverProfile = atsc3Module.getCurrentConfiguration()?.let { (srcType, config) ->
                    Atsc3Profile(
                        srcType,
                        config,
                        System.currentTimeMillis(),
                        Atsc3Profile.SimpleLocation(location.latitude, location.longitude)
                    )
                }
        }
    }

    companion object {
        val TAG: String = ServiceControllerImpl::class.java.simpleName

        private val BA_LOADING_TIMEOUT = TimeUnit.SECONDS.toMillis(5)
        private val PROFILE_LIFE_TIME = TimeUnit.DAYS.toMillis(30)

        private const val PROFILE_LOCATION_RADIUS = 24 * 1000 // about 15 mile
    }
}