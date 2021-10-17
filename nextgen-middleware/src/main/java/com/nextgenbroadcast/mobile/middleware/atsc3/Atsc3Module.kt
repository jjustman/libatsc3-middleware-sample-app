package com.nextgenbroadcast.mobile.middleware.atsc3

import android.util.Log
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants
import com.nextgenbroadcast.mobile.core.model.MediaUrl
import com.nextgenbroadcast.mobile.core.MiddlewareConfig
import com.nextgenbroadcast.mobile.core.atsc3.INtpClock
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.Atsc3ServiceLocationTable
import com.nextgenbroadcast.mobile.core.atsc3.SLTConstants
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts.LLSParserAEAT
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3Application
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3ApplicationFile
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.Atsc3Held
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.Atsc3HeldPackage
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.HeldXmlParser
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.service.Atsc3Service
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.service.LLSParserSLT
import com.nextgenbroadcast.mobile.middleware.atsc3.source.*
import com.nextgenbroadcast.mobile.middleware.dev.atsc3.PHYStatistics
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.ngbp.libatsc3.middleware.Atsc3NdkApplicationBridge
import org.ngbp.libatsc3.middleware.Atsc3NdkPHYBridge
import org.ngbp.libatsc3.middleware.android.a331.PackageExtractEnvelopeMetadataAndPayload
import org.ngbp.libatsc3.middleware.android.application.interfaces.IAtsc3NdkApplicationBridgeCallbacks
import org.ngbp.libatsc3.middleware.android.phy.interfaces.IAtsc3NdkPHYBridgeCallbacks
import org.ngbp.libatsc3.middleware.android.phy.models.BwPhyStatistics
import org.ngbp.libatsc3.middleware.android.phy.models.L1D_timePhyInformation
import org.ngbp.libatsc3.middleware.android.phy.models.RfPhyStatistics
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.schedule
import kotlin.math.max

internal class Atsc3Module(
        private val cacheDir: File,
        private val ntpClock: INtpClock?
) : IAtsc3Module, IAtsc3NdkApplicationBridgeCallbacks, IAtsc3NdkPHYBridgeCallbacks {

    private val atsc3NdkApplicationBridge = Atsc3NdkApplicationBridge(this)
    private val atsc3NdkPHYBridge = Atsc3NdkPHYBridge(this)
    private val systemProperties = atsc3NdkApplicationBridge.atsc3_slt_alc_get_system_properties()
    private val serviceLocationTable = ConcurrentHashMap<Int, Atsc3ServiceLocationTable>()
    private val serviceToSourceConfig = ConcurrentHashMap<Int, Int>()
    private val applicationPackageMap = ConcurrentHashMap<String, Atsc3Application>()
    private val stateLock = ReentrantLock()
    private val configurationTimer = Timer()
    private val stateScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())

    private var defaultConfiguration: Map<Any, Atsc3ServiceLocationTable>? = null

    @Volatile
    private var state = Atsc3ModuleState.IDLE

    @Volatile
    private var source: IAtsc3Source? = null

    @Volatile
    private var currentSourceConfiguration: Int = -1

    @Volatile
    private var selectedServiceBsid = -1

    @Volatile
    private var selectedServiceId = -1

    @Volatile
    private var suspendedServiceSelection: Boolean = false

    @Volatile
    private var selectedServiceSLSProtocol = -1

    @Volatile
    private var selectedServiceHeld: Atsc3Held? = null

    @Volatile
    private var selectedServicePackage: Atsc3HeldPackage? = null

    @Volatile
    private var selectedServiceHeldXml: String? = null

    @Volatile
    private var phyDemodLock: Boolean = false

    @Volatile
    private var isReconfiguring: Boolean = false

    @Volatile
    private var listener: Atsc3ModuleListener? = null

    @Volatile
    private var nextSourceConfigTuneTimeoutTask: TimerTask? = null

    override val rfPhyMetricsFlow = MutableSharedFlow<RfPhyStatistics>(0, 3, BufferOverflow.DROP_OLDEST)
    override val l1dPhyInfoFlow = MutableSharedFlow<L1D_timePhyInformation>(0, 3, BufferOverflow.DROP_OLDEST)

    override fun setListener(listener: Atsc3ModuleListener?) {
        if (this.listener != null) throw IllegalStateException("Atsc3Module listener already initialized")
        this.listener = listener
    }

    /**
     * Tune to one frequency or scan over all frequency in [frequencyList] and collect SLT from all of them.
     * The first one will be used as default. Tune can be ignored depending on force flag.
     * frequencyList - list of frequencies in KHz
     * force - if true always apply provided frequencies, otherwise apply only if source not tuned to
     * any frequency and not under demode lock.
     */
    override suspend fun tune(frequencyList: List<Int>, force: Boolean): Boolean {
        return withContext(stateScope.coroutineContext) {
            val freqKhz = frequencyList.firstOrNull() ?: 0
            val demodLock = phyDemodLock

            if (!force && demodLock) return@withContext false

            val src = source
            if (src is PhyAtsc3Source) {
                if (!force && src.getConfigCount() > 0) return@withContext false

                reset()

                src.setConfigs(frequencyList)

                if (USE_PERSISTED_CONFIGURATION) {
                    withStateLock {
                        applyDefaultConfiguration(src)
                    }
                }

                val config = if (USE_PERSISTED_CONFIGURATION) {
                    findNextConfigIndexToSniff(src.getCurrentConfigIndex())
                } else {
                    src.getCurrentConfigIndex()
                }
                val result = applySourceConfig(src, config, src.getConfigCount() > 1)

                return@withContext result != IAtsc3Source.RESULT_ERROR
            } else if (src is ITunableSource) {
                //TODO: add check
                //if (!force) return@withContext

                reset()

                src.tune(freqKhz)

                return@withContext true
            }

            return@withContext false
        }
    }

    override suspend fun open(source: IAtsc3Source, defaultConfig: Map<Any, Atsc3ServiceLocationTable>?): Boolean {
        return withContext(stateScope.coroutineContext) {
            log("Connecting to: $source")

            close()

            try {
                this@Atsc3Module.source = source

                return@withContext withStateLock {
                    defaultConfiguration = defaultConfig
                    if (USE_PERSISTED_CONFIGURATION) {
                        if (source.getConfigCount() > 0) {
                            applyDefaultConfiguration(source)

                            if (source is ConfigurableAtsc3Source<*>) {
                                val configIndex =
                                    findNextConfigIndexToSniff(source.getCurrentConfigIndex())
                                source.setInitialConfiguration(configIndex)
                            }
                        }
                    }

                    val result = source.open()
                    if (result == IAtsc3Source.RESULT_ERROR) {
                        close()
                    } else {
                        setSourceConfig(result)
                        val newState = if (result > 0) Atsc3ModuleState.SCANNING else Atsc3ModuleState.SNIFFING
                        setState(newState)
                        if (source.getConfigCount() > 1) {
                            startSourceConfigTimeoutTask()
                        }
                        return@withStateLock true
                    }
                    return@withStateLock false
                }
            } catch (e: Exception) {
                LOG.e(TAG, "Error when opening the source: $source", e)

                this@Atsc3Module.source = null
            }

            return@withContext false
        }
    }

    override suspend fun cancelScanning() {
        log("cancelScanning()")

        withContext(stateScope.coroutineContext) {
            cancelSourceConfigTimeoutTask()

            if (serviceLocationTable.isEmpty()) {
                setState(Atsc3ModuleState.SNIFFING)
                //setState(Atsc3ModuleState.IDLE) - don't move to IDLE to infinitely wait for a first SLT
            } else {
                finishReconfiguration()
            }
        }
    }

    private fun applyNextSourceConfig() {
        log("applyNextSourceConfig")

        withStateLock {
            val src = source
            if (src is ConfigurableAtsc3Source<*>) {
                val nextConfigIndex = if (USE_PERSISTED_CONFIGURATION) {
                    findNextConfigIndexToSniff(src.getCurrentConfigIndex() - 1)
                } else {
                    max(src.getCurrentConfigIndex() - 1, 0)
                }

                applySourceConfig(src, nextConfigIndex, true)
            }
        }
    }

    private inline fun <T> withStateLock(action: () -> T): T {
        try {
            stateLock.lock()

            return action()
        } finally {
            stateLock.unlock()
        }
    }

    private fun applySourceConfig(src: ConfigurableAtsc3Source<*>, config: Int, isScanning: Boolean): Int {
        log("applySourceConfig with src: $src, config: $config")

        cancelSourceConfigTimeoutTask()

        return withStateLock {
            if (src.getConfigCount() < 1) {
                setSourceConfig(-1)
                return@withStateLock IAtsc3Source.RESULT_ERROR
            }

            isReconfiguring = true
            val result = try {
                src.configure(config)
            } finally {
                isReconfiguring = false
            }

            if (result != IAtsc3Source.RESULT_ERROR) {
                //jjustman-2021-05-19 - this is not quite true, we shouldn't actually set our "new" state until either onSls or onSourceConfigTimeout occurs..
                //vmatiash-2021-05-28 - refactored - we apply new configuration but not switching to TUNED until SLT received
                setSourceConfig(result)
                val newState = if (result > 0 && isScanning) Atsc3ModuleState.SCANNING else Atsc3ModuleState.SNIFFING
                setState(newState)
                if (isScanning) {
                    startSourceConfigTimeoutTask()
                }
            } else {
                setSourceConfig(-1)
            }

            return@withStateLock result
        }
    }

    @Synchronized
    private fun startSourceConfigTimeoutTask() {
        //failsafe if we don't acquire SLT
        // either wait on this block this coroutine, or the onSltTablePresent will invoke nextSourceConfigTuneTimeoutJob.cancel()
        LOG.i(TAG, "nextSourceConfigTuneTimeoutJob: tune SLT timeout - scheduled for $SLT_ACQUIRE_TUNE_DELAY ms")

        nextSourceConfigTuneTimeoutTask = configurationTimer.schedule(SLT_ACQUIRE_TUNE_DELAY) {
            // Remove SLT data for unreachable  configurations
            //TODO: probably we don't want it
            /*withStateLock {
                val currentConfig = getSourceConfig()
                serviceToSourceConfig.filterValues { it == currentConfig }.keys.forEach { bsid ->
                    serviceLocationTable.remove(bsid)
                    serviceToSourceConfig.remove(bsid)
                }
            }*/

            val currentState = getState()
            log("nextSourceConfigTuneTimeoutJob: tune SLT timeout - currentState: $currentState, invoking applyNextSourceConfig")
            if (currentState == Atsc3ModuleState.SCANNING) {
                applyNextSourceConfig()
            } else if (currentState != Atsc3ModuleState.IDLE) {
                if (serviceLocationTable.isEmpty()) {
                    // stop() - don't stop phy here to let it being reconfigured
                    //setState(Atsc3ModuleState.IDLE) - don't move to IDLE to infinitely wait for a first SLT
                } else {
                    finishReconfiguration()
                }
            }
        }
    }

    private fun findNextConfigIndexToSniff(startConfigIndex: Int): Int {
        var nextConfigIndex = startConfigIndex
        while (nextConfigIndex >= 0 && serviceToSourceConfig.values.contains(nextConfigIndex)) {
            nextConfigIndex--
        }
        nextConfigIndex = max(nextConfigIndex, 0)
        return nextConfigIndex
    }

    private fun cancelSourceConfigTimeoutTask() {
        synchronized(this) {
            val task = nextSourceConfigTuneTimeoutTask
            nextSourceConfigTuneTimeoutTask = null
            task
        }?.let {
            LOG.i(TAG, "nextSourceConfigTuneTimeoutJob: canceling")
            it.cancel()
        }
    }

    private fun getState(): Atsc3ModuleState {
        return withStateLock {
            state
        }
    }

    private fun setState(newState: Atsc3ModuleState) {
        withStateLock {
            state = newState
        }
        listener?.onStateChanged(newState)
    }

    private fun setSourceConfig(config: Int) {
        val (configCount, isKnown) = synchronized(this) {
            currentSourceConfiguration = config

            val src = source
            val configCount = src?.getConfigCount() ?: 0
            val isKnown = serviceToSourceConfig.values.contains(config)

            Pair(configCount, isKnown)
        }

        listener?.onConfigurationChanged(config, configCount, isKnown)
    }

    @Synchronized
    private fun getSelectedServiceIdPair() = Pair(selectedServiceBsid, selectedServiceId)

    override suspend fun selectService(bsid: Int, serviceId: Int): Boolean {
        return withContext(stateScope.coroutineContext) {
            synchronized(this) {
                if (selectedServiceBsid == bsid && (selectedServiceId == serviceId || state != Atsc3ModuleState.TUNED)) return@withContext false

                clearHeld()

                selectedServiceSLSProtocol = -1
                selectedServiceBsid = bsid
                selectedServiceId = serviceId
            }

            serviceToSourceConfig[bsid]?.let { serviceConfig ->
                if (serviceConfig != currentSourceConfiguration) {
                    val src = source
                    if (src is ConfigurableAtsc3Source<*>) {
                        withStateLock {
                            applySourceConfig(src, serviceConfig, false)
                            suspendedServiceSelection = true
                        }
                        return@withContext true
                    }
                }
            } ?: log("selectService - source configuration for bsid: $bsid NOT FOUND")

            return@withContext internalSelectService(bsid, serviceId)
        }
    }

    private var tmpAdditionalServiceOpened = false

    private fun internalSelectService(bsid: Int, serviceId: Int): Boolean {
        log("internalSelectService: enter: with bsid: $bsid, serviceId: $serviceId")

        val slsProtocol = atsc3NdkApplicationBridge.atsc3_slt_selectService(serviceId).also {
            selectedServiceSLSProtocol = it
        }
        log("internalSelectService: after atsc3NdkApplicationBridge.atsc3_slt_selectService with serviceId: $serviceId, selectedServiceSLSProtocol is: $slsProtocol")

        //TODO: temporary test solution
        if (!tmpAdditionalServiceOpened) {
            serviceLocationTable[bsid]?.services?.firstOrNull {
                it.serviceCategory == SLTConstants.SERVICE_CATEGORY_ESG
            }?.let { service ->
                log("internalSelectService, calling atsc3_slt_alc_select_additional_service with service.serviceId: $service.serviceId")

                tmpAdditionalServiceOpened = atsc3NdkApplicationBridge.atsc3_slt_alc_select_additional_service(service.serviceId) > 0
            }
        }

        if (slsProtocol == SLS_PROTOCOL_MMT) {
            log("internalSelectService, calling listener?.onServiceMediaReady for MMT, listener: $listener, serviceId: $serviceId, bsid: $bsid")

            listener?.onServiceMediaReady(MediaUrl(SCHEME_MMT + serviceId, bsid, serviceId), 0)
        }

        return slsProtocol > 0
    }

    @Deprecated("Do not work because additional service persistence not implemented in libatsc3")
    override suspend fun selectAdditionalService(serviceId: Int): Boolean {
//        atsc3Scope.launch {
//        //atsc3NdkApplicationBridge.atsc3_slt_alc_clear_additional_service_selections()
//        val protocol = atsc3NdkApplicationBridge.atsc3_slt_alc_select_additional_service(serviceId)
//        return protocol > 0
        return false
    }

    override suspend fun close() {
        withContext(stateScope.coroutineContext) {
            source?.let { src ->
                src.stop() // call to stopRoute is not a mistake. We use it to close previously opened file
                src.close()
            }
            source = null

            defaultConfiguration = null
            setSourceConfig(-1)

            reset()
        }
    }

    override fun isServiceSelected(bsid: Int, serviceId: Int): Boolean {
        return getSelectedServiceIdPair().let { (selectedServiceBsid, selectedServiceId) ->
            selectedServiceBsid == bsid && selectedServiceId == serviceId
        }
    }

    override fun isIdle(): Boolean {
        // We can't use sync state because it could lock Main thread. But state changing operations
        // are sequential on ServiceController level.
        return /*getState()*/state == Atsc3ModuleState.IDLE
    }

    override fun getSelectedBSID(): Int {
        return getSelectedServiceIdPair().let { (selectedServiceBsid) -> selectedServiceBsid }
    }

    private fun reset() {
        setState(Atsc3ModuleState.IDLE)
        isReconfiguring = false
        suspendedServiceSelection = false
        clear()
        cancelSourceConfigTimeoutTask()
    }

    private fun getServiceMediaUri(serviceId: Int): String? {
        val slsProtocol = selectedServiceSLSProtocol
        return if (slsProtocol == SLS_PROTOCOL_DASH) {
            val routeMPDFileName = atsc3NdkApplicationBridge.atsc3_slt_alc_get_sls_metadata_fragments_content_locations_from_monitor_service_id(serviceId, CONTENT_TYPE_DASH)
            if (routeMPDFileName.isNotEmpty()) {
                String.format("%s/%s", jni_getCacheDir(), routeMPDFileName[0])
            } else {
                log("Unable to resolve Dash MPD path from MBMS envelope, service_id: %d", serviceId)
                null
            }
        } else if (slsProtocol == SLS_PROTOCOL_MMT) {
            SCHEME_MMT + serviceId
        } else {
            log("unsupported service protocol: %d", slsProtocol)
            null
        }
    }

    private fun clear() {
        withStateLock {
            clearHeld()
            clearService()
            serviceLocationTable.clear()
            serviceToSourceConfig.clear()

            //TODO: temporary test solution
            if (tmpAdditionalServiceOpened) {
                atsc3NdkApplicationBridge.atsc3_slt_alc_clear_additional_service_selections()
                tmpAdditionalServiceOpened = false
            }
        }
    }

    @Synchronized
    private fun clearService() {
        suspendedServiceSelection = false
        selectedServiceBsid = -1
        selectedServiceId = -1
        selectedServiceSLSProtocol = -1
    }

    @Synchronized
    private fun clearHeld() {
        selectedServiceHeld = null
        selectedServicePackage = null
        selectedServiceHeldXml = null

        /* it reconnects additional service on service selection that leads to ESG reloading - that's bad.
        But probably we need it when switching between services with different BSID. Lat's store BSID in ESG data instead
        //TODO: temporary test solution
        if (tmpAdditionalServiceOpened) {
            atsc3NdkApplicationBridge.atsc3_slt_alc_clear_additional_service_selections()
            tmpAdditionalServiceOpened = false
        }*/
    }

    private fun finishReconfiguration() {
        withStateLock {
            setState(Atsc3ModuleState.TUNED)

            processServiceLocationTableAndNotifyListener()

            if (suspendedServiceSelection) {
                getSelectedServiceIdPair().let { (selectedServiceBsid, selectedServiceId) ->
                    internalSelectService(selectedServiceBsid, selectedServiceId)
                }
            }
        }
    }

    private fun processServiceLocationTableAndNotifyListener() {
        val services = serviceLocationTable
            .toSortedMap(compareBy { serviceToSourceConfig[it] })
            .values.flatMap { it.services }

        val urls = getSelectedServiceIdPair().let { (selectedServiceBsid) ->
            serviceLocationTable[selectedServiceBsid]?.urls ?: emptyMap()
        }

        fireServiceLocationTableChanged(services, urls)
    }

    //////////////////////////////////////////////////////////////
    /// IAtsc3NdkApplicationBridgeCallbacks
    //////////////////////////////////////////////////////////////

    override fun showMsgFromNative(message: String) = log(message)

    override fun jni_getCacheDir(): File = cacheDir

    override fun onSltTablePresent(slt_payload_xml: String) {
        val shouldSkip = isReconfiguring

        cancelSourceConfigTimeoutTask()

        if (shouldSkip) {
            log("onSltTablePresent, currentState: uncertain, skip: $shouldSkip, slt_xml:\n$slt_payload_xml")
            return
        }

        // getState() is blocking method and must not be called if isReconfiguring = true
        // this call lock until state changing is finished.
        // It's also important to have it before reading currentSourceConfiguration to get correct state.
        val currentState = getState()

        val slt = LLSParserSLT().parseXML(slt_payload_xml)
        synchronized(this) {
            serviceLocationTable[slt.bsid] = slt
            serviceToSourceConfig[slt.bsid] = currentSourceConfiguration
        }

        log("onSltTablePresent, currentState: $currentState, skip: $shouldSkip, slt_xml:\n$slt_payload_xml")

        stateScope.launch {
            if (currentState == Atsc3ModuleState.SCANNING) {
                applyNextSourceConfig()
            } else if (currentState != Atsc3ModuleState.IDLE) {
                if (!suspendedServiceSelection) {
                    synchronized(this) {
                        selectedServiceBsid = slt.bsid
                    }
                }
                finishReconfiguration()
            }
        }
    }

    override fun onAeatTablePresent(aeatPayloadXML: String) {
        if (getState() == Atsc3ModuleState.SCANNING) return

        listener?.onAeatTableChanged(LLSParserAEAT().parseAeaTable(aeatPayloadXML))
    }

    override fun onSlsHeldEmissionPresent(serviceId: Int, heldPayloadXML: String) {
        if (getState() == Atsc3ModuleState.SCANNING) return

        val selectedServiceId = getSelectedServiceIdPair().let { (_, selectedServiceId) -> selectedServiceId }

        log("onSlsHeldEmissionPresent, $serviceId, selectedServiceID: $selectedServiceId, HELD: $heldPayloadXML")

        if (serviceId == selectedServiceId) {
            if (heldPayloadXML != selectedServiceHeldXml) {
                val held = HeldXmlParser().parseXML(heldPayloadXML)
                val pkg = held?.findActivePackage()
                val notify = synchronized(this) {
                    if (heldPayloadXML != selectedServiceHeldXml) {
                        selectedServiceHeldXml = heldPayloadXML
                        selectedServiceHeld = held

                        if (held != null) {
                            log("onSlsHeldEmissionPresent, pkg: $pkg")

                            if (pkg != selectedServicePackage) {
                                selectedServicePackage = pkg
                                return@synchronized true
                            }
                        }
                    }
                    false
                }

                if (notify) {
                    listener?.onServicePackageChanged(pkg)
                }
            }
        }
    }

    override fun onAlcObjectStatusMessage(alc_object_status_message: String) {
        //TODO: notify value changed
    }

    override fun onAlcObjectClosed(service_id: Int, tsi: Int, toi: Int, s_tsid_content_location: String?, s_tsid_content_type: String?, cache_file_path: String?) {
        //if (getState() == State.SCANNING) return we do not open an additional service when scanning

        when (s_tsid_content_type) {
            CONTENT_TYPE_SGDD -> {
                // skip
            }

            CONTENT_TYPE_SGDU -> {
                //TODO: check that data from an actual source configuration or share current bsid/serviceId with SGDU
                cache_file_path?.let {
                    getSelectedServiceIdPair().let { (selectedServiceBsid) ->
                        listener?.onServiceGuideUnitReceived(getFullPath(cache_file_path), selectedServiceBsid)
                    }
                }
            }
        }
    }

    override fun onPackageExtractCompleted(packageMetadata: PackageExtractEnvelopeMetadataAndPayload) {
        if (getState() == Atsc3ModuleState.SCANNING) return

        log("onPackageExtractCompleted packageExtractPath: ${packageMetadata.packageExtractPath} packageName: ${packageMetadata.packageName}, appContextIdList: ${packageMetadata.appContextIdList}, files: [${packageMetadata.multipartRelatedPayloadList.map { it.contentLocation }}]")

        if (!packageMetadata.isValid()) {
            log("onPackageExtractCompleted INVALID: $packageMetadata")
            return
        }

        val pkgUID = "${packageMetadata.packageExtractPath}/${packageMetadata.packageName}"
        val pkg = packageMetadata.toApplication(pkgUID)
        val appPackage = applicationPackageMap[pkgUID]
        if (appPackage != pkg) {
            applicationPackageMap[pkgUID] = pkg
            listener?.onApplicationPackageReceived(pkg)
        }
    }

    override fun routeDash_force_player_reload_mpd(serviceID: Int) {
        LOG.i(TAG, "routeDash_force_player_reload_mpd with serviceId: $serviceID")
        if (getState() == Atsc3ModuleState.SCANNING) return

        getSelectedServiceIdPair().let { (selectedServiceBsid, selectedServiceId) ->
            if (serviceID == selectedServiceId) {
                getServiceMediaUri(serviceID)?.let { mpdPath ->
                    listener?.onServiceMediaReady(MediaUrl(mpdPath, selectedServiceBsid, selectedServiceId), MPD_UPDATE_DELAY)
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////
    /// IAtsc3NdkPHYBridgeCallbacks
    //////////////////////////////////////////////////////////////

    override fun onPhyLogMessage(message: String) = log(message)

    override fun onPhyError(message: String) {
        listener?.onError("PHY Error: $message")
        log("PHY Error: $message")
    }

    override fun pushRfPhyStatisticsUpdate(rfPhyStatistics: RfPhyStatistics) {
        phyDemodLock = rfPhyStatistics.demod_lock != 0

        if (USE_DEV_STATISTIC) {
            try {
                PHYStatistics.PHYRfStatistics = "PHY: $rfPhyStatistics".also {
                    log(it)
                }
            } catch (ex: Exception) {
                Log.w(TAG, "exception when dumping PHYRFStatistics: $ex")
            }
            PHYStatistics.rfMetricsFlow.tryEmit(rfPhyStatistics)
        }

        rfPhyMetricsFlow.tryEmit(rfPhyStatistics)
    }

    override fun pushBwPhyStatistics(bwPhyStatistics: BwPhyStatistics) {
        try {
            log(bwPhyStatistics.toString())
        } catch (ex: Exception) {
            log(ex.toString())
        }

        if (USE_DEV_STATISTIC) {
            PHYStatistics.PHYBWStatistics = "$bwPhyStatistics".also {
                log(it)
            }
        }
    }

    override fun pushL1d_TimeInfo(l1dTimeInfo: L1D_timePhyInformation) {
        if (USE_DEV_STATISTIC) {
            val anchorNtpTimestamp = l1dTimeInfo.toStringFromAnchorNtpTimestamp(
                ntpClock?.getCurrentNtpTimeMs() ?: -1
            )
            PHYStatistics.PHYL1dTimingStatistics = "SFN: $anchorNtpTimestamp".also {
                log(it)
            }
        }

        l1dPhyInfoFlow.tryEmit(l1dTimeInfo)
    }

    //////////////////////////////////////////////////////////////

    override suspend fun getCurrentConfiguration(): Pair<String, Map<Any, Atsc3ServiceLocationTable>>? {
        return withContext(stateScope.coroutineContext) {
            val src = source ?: return@withContext null
            Pair(
                src::class.java.simpleName,
                serviceToSourceConfig.mapNotNull { (bsid, configIndex) ->
                    src.getConfigByIndex(configIndex).let { config ->
                        serviceLocationTable[bsid]?.let { slt ->
                            config to slt
                        }
                    }
                }.toMap()
            )
        }
    }

    override fun getVersionInfo(): Map<String, String?> {
        return mutableMapOf<String, String?>().apply {
            put(PhyInfoConstants.INFO_SERIAL_NUMBER, getSerialNum())
            source?.let { src ->
                put(PhyInfoConstants.INFO_SDK_VERSION, src.getSdkVersion())
                put(PhyInfoConstants.INFO_FIRMWARE_VERSION, src.getFirmwareVersion())
                if (src is UsbPhyAtsc3Source) {
                    val deviceType = when (src.type) {
                        Atsc3Source.DEVICE_TYPE_KAILASH -> "KAILASH"
                        Atsc3Source.DEVICE_TYPE_YOGA -> "YOGA"
                        Atsc3Source.DEVICE_TYPE_AUTO -> "MARKONE"
                        else -> src.type.toString()
                    }
                    put(PhyInfoConstants.INFO_PHY_TYPE, deviceType)
                }
            }
        }
    }

    override fun getSerialNum(): String? {
        var serialId = systemProperties.serialno_str
        if (serialId.isNullOrBlank()) {
            serialId = systemProperties.boot_serialno_str
        }
        return if (serialId.isNullOrBlank()) null else serialId
    }

    override fun getNtpClock() = ntpClock

    private fun applyDefaultConfiguration(src: IAtsc3Source) {
        val srcConfigs = src.getAllConfigs().map { it.toString() }
        defaultConfiguration?.forEach { (config, slt) ->
            val configIndex = srcConfigs.indexOf(config)
            if (configIndex >= 0) {
                serviceLocationTable[slt.bsid] = slt
                serviceToSourceConfig[slt.bsid] = configIndex
            }
        }
    }

    //jjustman-2021-05-19 - TODO: fix me to pass a proper collection of urls for <frequency, bsid, slt.groupId, SLTConstants.URL_TYPE_REPORT_SERVER> := { <XML> }
    private fun fireServiceLocationTableChanged(services: List<Atsc3Service>, urls: Map<Int, String>) {
        log("fireServiceLocationTableChanged, services: $services, urls: $urls")

        getSelectedServiceIdPair().let { (selectedServiceBsid) ->
            listener?.onServiceLocationTableChanged(
                selectedServiceBsid,
                Collections.unmodifiableList(services),
                urls[SLTConstants.URL_TYPE_REPORT_SERVER]
            )
        }
    }

    private fun PackageExtractEnvelopeMetadataAndPayload.isValid(): Boolean {
        return !packageName.isNullOrEmpty()
                && !appContextIdList.isNullOrEmpty()
                && !packageExtractPath.isNullOrEmpty()
    }

    private fun PackageExtractEnvelopeMetadataAndPayload.toApplication(uid: String): Atsc3Application {
        val files = multipartRelatedPayloadList?.map { file ->
            file.contentLocation to Atsc3ApplicationFile(file.contentLocation, file.contentType, file.version)
        }?.toMap() ?: emptyMap<String, Atsc3ApplicationFile>()

        return Atsc3Application(
            uid,
            packageName,
            appContextIdList.split(" "),
            getFullPath(packageExtractPath),
            files
        )
    }

    private fun getFullPath(cachePath: String) = String.format("%s/%s", jni_getCacheDir(), cachePath)

    private fun log(text: String, vararg params: Any) {
        val msg = if (params.isNotEmpty()) {
            String.format(Locale.US, text, *params)
        } else {
            text
        }

        LOG.d(TAG, msg)
    }

    companion object {
        val TAG: String = Atsc3Module::class.java.simpleName

        private val SLT_ACQUIRE_TUNE_DELAY = TimeUnit.SECONDS.toMillis(20)

        private const val CONTENT_TYPE_DASH = "application/dash+xml"
        private const val CONTENT_TYPE_SGDD = "application/vnd.oma.bcast.sgdd+xml"
        private const val CONTENT_TYPE_SGDU = "application/vnd.oma.bcast.sgdu"

        private const val MPD_UPDATE_DELAY = 2000L

        const val RES_OK = 0

        const val SLS_PROTOCOL_DASH = 1
        const val SLS_PROTOCOL_MMT = 2

        const val SCHEME_MMT = "mmt://"

        private const val USE_PERSISTED_CONFIGURATION = true

        val USE_DEV_STATISTIC = MiddlewareConfig.DEV_TOOLS
    }
}