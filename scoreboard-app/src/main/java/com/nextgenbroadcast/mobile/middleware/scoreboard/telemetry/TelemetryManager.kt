package com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.middleware.dev.nsd.NsdConfig
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.CertificateStore
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.TelemetryClient2
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws.AWSIoThing
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer.*
import com.nextgenbroadcast.mobile.middleware.scoreboard.BuildConfig
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.TelemetryDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import okio.internal.commonToUtf8String
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.scheduleAtFixedRate

class TelemetryManager(
    private val context: Context,
    private val serialNum: String,
    private val onDeviceListUpdated: (devices: List<TelemetryDevice>) -> Unit
) {
    private val nsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val timer = Timer()
    private val telemetryClient = TelemetryClient2(100)
    private val awsIoThing: AWSIoThing

    private val nsdServices = mutableMapOf<String, NsdServiceInfo?>()
    private val awsDevices = mutableSetOf<String>()

    private val devices = linkedMapOf<String, TelemetryDevice>()
    private val deviceObservers = mutableMapOf<String, ITelemetryObserver>()

    private var deviceLocationJob: Job? = null
    private var awsUpdateTask: TimerTask? = null

    init {
        awsIoThing = AWSIoThing(
            AWSIOT_MANAGER_TEMPLATE_NAME,
            AWSIOT_MANAGER_ID_FORMAT,
            serialNum,
            encryptedSharedPreferences(context, IoT_PREFERENCE),
        ) { keyPassword ->
            CertificateStore.getManagerCert(context, keyPassword)
                ?: throw IOException("Failed to read certificate from resources")
        }
    }

    fun getGlobalEventFlow() = addAutocompleteObserver(
        observer = AWSLoggingObserver(
            AWSIOT_EVENT_TOPIC_FORMAT,
            awsIoThing,
            AWSIOT_CLIENT_ID_ANY,
            listOf(AWSIOT_TOPIC_ANY)
        ),
        replay = 50
    )

    fun getGlobalEventFlow(topics: List<String>, replay: Int = 0, deviceId: String = AWSIOT_CLIENT_ID_ANY) = addAutocompleteObserver(
        observer = AWSTelemetryObserver(
            AWSIOT_EVENT_TOPIC_FORMAT,
            awsIoThing,
            deviceId,
            topics
        ),
        replay = replay
    )

    private fun addAutocompleteObserver(observer: ITelemetryObserver, replay: Int = 1, buffer: Int = 10): Flow<ClientTelemetryEvent>? {
        telemetryClient.addObserver(observer, MutableSharedFlow(
            replay = replay,
            extraBufferCapacity = buffer,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        ))

        return telemetryClient.getFlow(observer)?.onCompletion {
            telemetryClient.removeObserver(observer)
        }
    }

    fun start() {
        try {
            nsdManager.discoverServices(NsdConfig.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            LOG.d(TAG, "Failed to start NSD service discovering", e)
        }

        telemetryClient.start()

        deviceLocationJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                getGlobalEventFlow(listOf(AWSIOT_TOPIC_PING, AWSIOT_TOPIC_IS_ONLINE))?.collect { event ->
                    event.getClientId()?.let { clientId ->
                        awsDevices.add(clientId)
                        addDevice(
                            deviceId = clientId,
                            availableOnAWS = true
                        )
                    }
                }
            } catch (e: Exception) {
                LOG.d(TAG, "Failed to observe devices on AWS", e)
            }
        }

        awsUpdateTask = timer.scheduleAtFixedRate(5_000, AWSIOT_PING_PERIOD) {
            sendGlobalCommand(AWSIOT_TOPIC_PING, "")
        }
    }

    fun stop() {
        scope.cancel()

        awsUpdateTask?.cancel()
        awsUpdateTask = null

        deviceLocationJob?.cancel()
        deviceLocationJob = null

        telemetryClient.stop()

        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            LOG.d(TAG, "Failed to stop NSD service discovering", e)
        }

        CoroutineScope(Dispatchers.Main).launch {
            awsIoThing.close()
        }
    }

    fun sendGlobalCommand(topic: String, payload: String) {
        scope.launch {
            awsIoThing.publish("$AWSIOT_COMMAND_GLOBAL/$topic", payload)
        }
    }

    fun sendDeviceCommand(devices: List<String>, payload: String) {
        scope.launch {
            devices.forEach { deviceId ->
                awsIoThing.publish(
                    AWSIOT_COMMAND_SINGLE_DEVICE.replace(AWSIoThing.AWSIOT_FORMAT_SERIAL, deviceId),
                    payload
                )
            }
        }
    }

    fun getDeviceById(deviceId: String): TelemetryDevice? {
        return devices[deviceId]
    }

    fun getFlow(device: TelemetryDevice): Flow<ClientTelemetryEvent>? {
        return deviceObservers[device.id]?.let { observer ->
            telemetryClient.getFlow(observer)
        }
    }

    fun connectDevice(device: TelemetryDevice): Boolean {
        val topics = listOf(
            TelemetryEvent.EVENT_TOPIC_PHY,
            TelemetryEvent.EVENT_TOPIC_BATTERY
        )
        val observer = when {
            device.availableOnNSD -> WebTelemetryObserver(
                device.host,
                device.port,
                topics
            )
            device.availableOnAWS -> AWSTelemetryObserver(
                AWSIOT_EVENT_TOPIC_FORMAT,
                awsIoThing,
                AWSIOT_CLIENT_ID_FORMAT.replace(AWSIoThing.AWSIOT_FORMAT_SERIAL, device.id),
                topics
            )
            else -> null
        } ?: return false

        telemetryClient.addObserver(observer)
        deviceObservers[device.id] = observer

        return true
    }

    fun disconnectDevice(device: TelemetryDevice) {
        deviceObservers.remove(device.id)?.let { observer ->
            telemetryClient.removeObserver(observer)
        }
    }

    fun getConnectedDevices(): List<TelemetryDevice> {
        return devices.filterKeys { id ->
            deviceObservers.containsKey(id)
        }.values.toList()
    }

    private fun addDevice(deviceId: String, ip: String = "", port: Int = 0, availableOnNSD: Boolean = false, availableOnAWS: Boolean = false) {
        CoroutineScope(Dispatchers.Main).launch {
            val oldDevice = devices[deviceId]
            val newDevice = oldDevice?.let {
                when {
                    availableOnNSD -> it.copy(availableOnNSD = true)
                    availableOnAWS -> it.copy(availableOnAWS = true)
                    else -> it.copy(availableOnNSD = false, availableOnAWS = false)
                }
            } ?: TelemetryDevice(deviceId, ip, port, availableOnNSD, availableOnAWS)

            if (oldDevice != newDevice) {
                devices[deviceId] = newDevice
                notifyDevicesChanged()
            }
        }
    }

    private fun deleteDevice(deviceId: String, lostOnNSD: Boolean = false, lostOnAWS: Boolean = false) {
        CoroutineScope(Dispatchers.Main).launch {
            val oldDevice = devices[deviceId]
            if (oldDevice != null) {
                devices[deviceId] = when {
                    lostOnNSD -> oldDevice.copy(availableOnNSD = false)
                    lostOnAWS -> oldDevice.copy(availableOnAWS = false)
                    else -> oldDevice.copy(availableOnNSD = false, availableOnAWS = false)
                }
                notifyDevicesChanged()
            }
        }
    }

    private fun notifyDevicesChanged() {
        onDeviceListUpdated(devices.filterValues { !it.isLost }.values.toList())
    }

    private fun encryptedSharedPreferences(context: Context, fileName: String): SharedPreferences {
        val appContext = context.applicationContext
        return EncryptedSharedPreferences.create(
            appContext,
            fileName,
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {
            LOG.d(TAG, "NSD Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            LOG.d(TAG, "Service discovery success $service")

            if (service.serviceName.startsWith(NsdConfig.SERVICE_NAME)) {
                if (!nsdServices.containsKey(service.serviceName)) {
                    nsdServices[service.serviceName] = null
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            LOG.e(TAG, "Failed to resolve NSD service: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            LOG.e(TAG, "NSD service resolved. $serviceInfo")

                            val deviceId = serviceInfo.getId()
                            nsdServices[serviceInfo.serviceName] = if (!deviceId.isNullOrBlank()) {
                                serviceInfo
                            } else null

                            deviceId?.let {
                                addDevice(
                                    deviceId = deviceId,
                                    ip = serviceInfo.host.hostAddress,
                                    port = serviceInfo.port,
                                    availableOnNSD = true
                                )
                            }
                        }
                    })
                }
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            LOG.e(TAG, "service lost: $serviceInfo")

            nsdServices.remove(serviceInfo.serviceName)

            serviceInfo.getId()?.let { deviceId ->
                deleteDevice(deviceId, lostOnNSD = true)
            }
        }

        override fun onDiscoveryStopped(serviceType: String) {
            LOG.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            LOG.e(TAG, "Discovery failed: Error code:$errorCode")

            nsdManager.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            LOG.e(TAG, "Discovery failed: Error code:$errorCode")

            nsdManager.stopServiceDiscovery(this)
        }
    }

    fun NsdServiceInfo.getId(): String? {
        return attributes?.get("id")?.commonToUtf8String()
    }

    companion object {
        val TAG: String = TelemetryManager::class.java.simpleName

        private const val IoT_PREFERENCE = "${BuildConfig.APPLICATION_ID}.awsiot"

        private const val AWSIOT_MANAGER_TEMPLATE_NAME = "ATSC3MobileManagerProvisioning"
        private const val AWSIOT_MANAGER_ID_FORMAT = "ATSC3MobileManager_${AWSIoThing.AWSIOT_FORMAT_SERIAL}"
        private const val AWSIOT_EVENT_TOPIC_FORMAT = "telemetry/${AWSIoThing.AWSIOT_FORMAT_SERIAL}"
        private const val AWSIOT_COMMAND_GLOBAL = "global/command/request"
        private const val AWSIOT_COMMAND_SINGLE_DEVICE = "control/ATSC3MobileReceiver_${AWSIoThing.AWSIOT_FORMAT_SERIAL}"
        private const val AWSIOT_CLIENT_ID_FORMAT = "ATSC3MobileReceiver_${AWSIoThing.AWSIOT_FORMAT_SERIAL}"
        private const val AWSIOT_CLIENT_ID_ANY = "+"

        private const val AWSIOT_TOPIC_ANY = "#"
        private const val AWSIOT_TOPIC_PING = TelemetryEvent.EVENT_TOPIC_PING
        private const val AWSIOT_TOPIC_IS_ONLINE = TelemetryEvent.EVENT_TOPIC_IS_ONLINE

        private val AWSIOT_PING_PERIOD = TimeUnit.MINUTES.toMillis(1)
    }
}