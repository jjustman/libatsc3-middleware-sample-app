package com.nextgenbroadcast.mobile.middleware.service.holder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.media.MediaBrowserServiceCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.model.PhyFrequency
import com.nextgenbroadcast.mobile.middleware.*
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverCore
import com.nextgenbroadcast.mobile.middleware.dev.config.DevConfig
import com.nextgenbroadcast.mobile.middleware.dev.nsd.NsdConfig
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.CertificateStore
import com.nextgenbroadcast.mobile.middleware.gateway.web.ConnectionType
import com.nextgenbroadcast.mobile.middleware.server.web.IMiddlewareWebServer
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.ReceiverTelemetry
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.RemoteControlBroker
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.TelemetryBroker
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.aws.AWSIoThing
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.control.AWSIoTelemetryControl
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.control.ITelemetryControl
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.control.UdpTelemetryControl
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.task.PongTelemetryTask
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.task.WiFiInfoTelemetryTask
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer.AWSIoTelemetryWriter
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer.FileTelemetryWriter
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer.VuzixPhyTelemetryWriter
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.control.WebTelemetryControl
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.*
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.task.IsOnlineTelemetryTask
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.writer.WebTelemetryWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.ngbp.libatsc3.middleware.android.Atsc3NdkCoreLogs
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

private typealias PrefsMap = Map<String, Boolean>

internal class TelemetryHolder(
    private val context: Context,
    private val receiver: Atsc3ReceiverCore
) {
    private val gson = Gson()
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val wifiManager: WifiManager by lazy {
        context.getSystemService(MediaBrowserServiceCompat.WIFI_SERVICE) as WifiManager
    }
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val atsc3NdkCoreLogs by lazy {
        Atsc3NdkCoreLogs().apply {
            init()
        }
    }

    @Volatile
    private var isClosed = false

    private var telemetryBroker: TelemetryBroker? = null
    private var remoteControl: RemoteControlBroker? = null
    private var awsIoThing: AWSIoThing? = null

    private val _debugInfoSettings: MutableStateFlow<PrefsMap>
    private val _logInfoSettings: MutableStateFlow<Map<String, Boolean>>

    init {
        _debugInfoSettings = MutableStateFlow(
            readPrefsMap(PREF_DEBUG_INFO_KEY).toMutableMap().apply {
                if (isEmpty()) {
                    put(ReceiverTelemetry.INFO_DEBUG, true)
                }
            }.toMap()
        ).also { flow ->
            flow.observeAndStore(PREF_DEBUG_INFO_KEY)
        }

        _logInfoSettings = MutableStateFlow(initializeLogsInfoSettings())
            .also { flow ->
                flow.observeAndStore(PREF_LOG_INFO_KEY)
            }
    }

    val debugInfoSettings = _debugInfoSettings.asStateFlow()
    val telemetryEnabled: StateFlow<Map<String, Boolean>>
        get() = telemetryBroker?.readersEnabled ?: MutableStateFlow(emptyMap())
    val telemetryDelay: StateFlow<Map<String, Long>>
        get() = telemetryBroker?.readersDelay ?: MutableStateFlow(emptyMap())
    val logInfoSettings = _logInfoSettings.asStateFlow()

    @MainThread
    fun open() {
        if (telemetryBroker != null) return

        telemetryBroker = TelemetryBroker(
                listOf(
                        BatteryTelemetryReader(context),
                        SensorTelemetryReader(sensorManager, Sensor.TYPE_LINEAR_ACCELERATION),
                        SensorTelemetryReader(sensorManager, Sensor.TYPE_GYROSCOPE),
                        SensorTelemetryReader(sensorManager, Sensor.TYPE_SIGNIFICANT_MOTION),
                        SensorTelemetryReader(sensorManager, Sensor.TYPE_STEP_DETECTOR),
                        SensorTelemetryReader(sensorManager, Sensor.TYPE_STEP_COUNTER),
                        SensorTelemetryReader(sensorManager, Sensor.TYPE_ROTATION_VECTOR),
                        GPSTelemetryReader(context),
                        RfPhyTelemetryReader(receiver.rfPhyMetricsFlow),
                        L1DPhyTelemetryReader(receiver.l1dPhyInfoFlow, receiver.ntpClock),
                        ErrorTelemetryReader(receiver.errorFlow)
                ),
                listOf(
                        //AWSIoTelemetryWriter(thing),
                        //FileTelemetryWriter(cacheDir, "telemetry.log")
                ),
                context.resources.getBoolean(R.bool.telemetryEnabled)
        ).apply {
            //start() do not start Telemetry with application, use switch in Settings dialog or AWS command
        }.also { broker ->
            // Enable telemetry readers that stored in preferences with enabled state or absent
            val prefReadersMap = readPrefsMap(PREF_PHY_INFO_KEY)
            val newReaders = broker.readerNames.subtract(prefReadersMap.keys).map { name ->
                name to true
            }
            val readersToEnable = (prefReadersMap + newReaders).filterValues { enabled ->
                enabled
            }.keys.toList()

            if (readersToEnable.isNotEmpty()) {
                broker.setReaderEnabled(true, readersToEnable)
            }

            CoroutineScope(Dispatchers.Main).launch {
                broker.readersEnabled.collect { readerPref ->
                    storePrefsMap(PREF_PHY_INFO_KEY, readerPref)
                }
            }
        }

        remoteControl = RemoteControlBroker(
            listOf(
                //AWSIoTelemetryControl(thing),
                //WebTelemetryControl(),
                UdpTelemetryControl(CONNECTION_HOST, CONNECTION_UDP_PORT)
            ),
            ::executeCommand
        ).apply {
            start()
        }

        val deviceId = receiver.settings.deviceId

        telemetryBroker?.addWriter(
            VuzixPhyTelemetryWriter(context, deviceId)
        )

        registerAWSIoThing(deviceId)
        registerNsdService(deviceId)
    }

    @MainThread
    fun close() {
        isClosed = true

        telemetryBroker?.close()
        telemetryBroker = null

        remoteControl?.stop()
        remoteControl = null

        unregisterAWSIoThing()
        unregisterNsdService()
    }

    fun setInfoVisible(enabled: Boolean, name: String) {
        _debugInfoSettings.value = _debugInfoSettings.value.toMutableMap().apply {
            put(name, enabled)
        }
    }

    fun setTelemetryEnabled(enabled: Boolean, name: String? = null) {
        if (name != null) {
            telemetryBroker?.setReaderEnabled(enabled, name)
        } else {
            telemetryBroker?.setReadersEnabled(enabled)
        }
    }

    fun setTelemetryDelay(delayMils: Long, name: String) {
        telemetryBroker?.setReaderDelay(name, delayMils)
    }

    fun notifyWebServerStarted(server: IMiddlewareWebServer) {
        server.addConnection(CONNECTION_TYPE, CONNECTION_HOST, CONNECTION_TCP_PORT)

        remoteControl?.addControl(WebTelemetryControl(server))
        telemetryBroker?.addWriter(WebTelemetryWriter(server))
    }

    fun notifyWebServerStopped() {
        remoteControl?.removeControl(WebTelemetryControl::class.java)
        telemetryBroker?.removeWriter(WebTelemetryWriter::class.java)
    }

    fun setAtsc3LogEnabledByName(name: String, enabled: Boolean) {
        val logEnabled = _logInfoSettings.value[name] ?: return
        if (logEnabled != enabled) {
            atsc3NdkCoreLogs.setLogEnabledByName(name, enabled)
            _logInfoSettings.value = _logInfoSettings.value.toMutableMap().apply {
                put(name, enabled)
            }
        }
    }

    private fun Flow<PrefsMap>.observeAndStore(key: String): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            collect { prefs ->
                storePrefsMap(key, prefs)
            }
        }
    }

    private fun initializeLogsInfoSettings(): PrefsMap {
        val logs = atsc3NdkCoreLogs.atsc3LogNames.map { name ->
            name to false
        }.sortedBy { it.first }.toMap().toMutableMap()
        val deviceEnabledLogs = readPrefsMap(PREF_LOG_INFO_KEY)
        val defaultEnabledLogs = atsc3NdkCoreLogs.atsc3LogEnabledNames

        deviceEnabledLogs.forEach { (key, enabled) ->
            logs[key] = enabled
            atsc3NdkCoreLogs.setLogEnabledByName(key, enabled)
        }

        if (deviceEnabledLogs.isEmpty()) {
            // saving default enabled logs
            defaultEnabledLogs.forEach { name ->
                logs[name] = true
            }
        } else {
            // disabling default enabled logs
            defaultEnabledLogs.filter {
                deviceEnabledLogs[it] != true
            }.forEach {
                atsc3NdkCoreLogs.setLogEnabledByName(it, false)
            }
        }

        return logs
    }

    private fun Atsc3NdkCoreLogs.setLogEnabledByName(name: String, enabled: Boolean) {
        setAtsc3LogEnabledByName(name, if (enabled) 1 else 0)
    }

    private fun registerAWSIoThing(serialNumber: String) {
        val thing = AWSIoThing(
            AWSIOT_RECEIVER_TEMPLATE_NAME,
            AWSIOT_CLIENT_ID_FORMAT,
            serialNumber,
            encryptedSharedPreferences(context, IoT_PREFERENCE),
        ) { keyPassword ->
            CertificateStore.getReceiverCert(context, keyPassword)
                ?: throw IOException("Failed to read certificate from resources")
        }.also {
            awsIoThing = it
        }
        thing.onConnectedCallback = {
            telemetryBroker?.runTask(IsOnlineTelemetryTask())
        }

        telemetryBroker?.addWriter(
            AWSIoTelemetryWriter(AWSIOT_EVENT_TOPIC_FORMAT, thing)
        )

        remoteControl?.addControl(
            AWSIoTelemetryControl(
                AWSIOT_TOPIC_CONTROL,
                AWSIOT_GLOBAL_TOPIC_CONTROL,
                thing
            )
        )
    }

    private fun unregisterAWSIoThing() {
        awsIoThing?.let { thing ->
            CoroutineScope(Dispatchers.Main).launch {
                thing.close()
            }
        }
        awsIoThing?.onConnectedCallback = null
        awsIoThing = null
    }

    private fun executeCommand(action: String, arguments: Map<String, String>) {
        LOG.d(TAG, "Control command received: $action, args: $arguments")

        when (action) {
            ITelemetryControl.CONTROL_ACTION_TUNE -> {
                arguments[ITelemetryControl.CONTROL_ARGUMENT_FREQUENCY]?.let { frequencyList ->
                    val frequencies = frequencyList
                        .split(ITelemetryControl.CONTROL_ARGUMENT_DELIMITER)
                        .mapNotNull { it.toIntOrNull() }
                    if (frequencies.isNotEmpty()) {
                        receiver.tune(PhyFrequency.user(frequencies))
                    }
                }
            }

            ITelemetryControl.CONTROL_ACTION_ACQUIRE_SERVICE -> {
                val service = arguments[ITelemetryControl.CONTROL_ARGUMENT_SERVICE_ID]?.toIntOrNull()?.let { serviceId ->
                    arguments[ITelemetryControl.CONTROL_ARGUMENT_SERVICE_BSID]?.toIntOrNull()?.let { bsid ->
                        receiver.findServiceById(bsid, serviceId)
                    } ?: let {
                        receiver.findActiveServiceById(serviceId)
                    }
                } ?: let {
                    arguments[ITelemetryControl.CONTROL_ARGUMENT_SERVICE_NAME]?.let { serviceName ->
                        receiver.findServiceByName(serviceName)
                    }
                }

                if (service != null) {
                    receiver.selectService(service)
                }
            }

            ITelemetryControl.CONTROL_ACTION_SET_TEST_CASE -> {
                telemetryBroker?.testCase = arguments[ITelemetryControl.CONTROL_ARGUMENT_CASE]?.ifBlank {
                    null
                }
            }

            ITelemetryControl.CONTROL_ACTION_RESTART_APP -> {
                val delay = max(arguments[ITelemetryControl.CONTROL_ARGUMENT_START_DELAY]?.toLongOrNull()
                    ?: 0, 100L)
                val intent = Intent(context.getString(R.string.defaultActionWatch)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                alarmManager[AlarmManager.RTC, System.currentTimeMillis() + delay] = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)

                exitProcess(0)
            }

            ITelemetryControl.CONTROL_ACTION_REBOOT_DEVICE -> {
                try {
                    Runtime.getRuntime().exec("reboot")
                } catch (e: Exception) {
                    LOG.d(TAG, "Can't reboot device", e)
                }
            }

            ITelemetryControl.CONTROL_ACTION_TELEMETRY_ENABLE -> {
                val telemetryNameList = arguments[ITelemetryControl.CONTROL_ARGUMENT_NAME]?.let { telemetryNameList ->
                    telemetryNameList.split(ITelemetryControl.CONTROL_ARGUMENT_DELIMITER).map { name ->
                        SensorTelemetryReader.getFullSensorName(name) ?: name
                    }
                }

                arguments[ITelemetryControl.CONTROL_ARGUMENT_ENABLE]?.let {
                    val enabled = it.toBoolean()

                    if (telemetryNameList != null) {
                        telemetryBroker?.setReaderEnabled(enabled, telemetryNameList)
                    } else {
                        telemetryBroker?.setReadersEnabled(enabled)
                    }
                }
            }

            ITelemetryControl.CONTROL_ACTION_SHOW_DEBUG_INFO -> {
                _debugInfoSettings.value = mutableMapOf<String, Boolean>().apply {
                    arguments.forEach { (key, value) ->
                        put(key, value.toBoolean())
                    }
                }
            }

            ITelemetryControl.CONTROL_ACTION_VOLUME -> {
                arguments[ITelemetryControl.CONTROL_ARGUMENT_VALUE]?.toIntOrNull()?.let { inputVolume ->
                    val volume = min(max(0, inputVolume), 100) / 100f
                    val maxStreamVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxStreamVolume * volume).toInt(), 0)
                }
            }

            ITelemetryControl.CONTROL_ACTION_WIFI_INFO -> {
                telemetryBroker?.runTask(WiFiInfoTelemetryTask(wifiManager))
            }

            ITelemetryControl.CONTROL_ACTION_PING -> {
                telemetryBroker?.runTask(PongTelemetryTask())
            }

            ITelemetryControl.CONTROL_ACTION_FILE_WRITER -> {
                val fileName = arguments[ITelemetryControl.CONTROL_ARGUMENT_NAME]
                val writeDuration = arguments[ITelemetryControl.CONTROL_ARGUMENT_DURATION]?.toIntOrNull()
                telemetryBroker?.removeWriter(FileTelemetryWriter::class.java)

                if (!fileName.isNullOrBlank()) {
                    telemetryBroker?.addWriter(FileTelemetryWriter(context.filesDir, fileName, writeDuration ?: 0), false)
                }
            }

            ITelemetryControl.CONTROL_ACTION_RESET_RECEIVER_DEMODE -> {
                /// TODO TBD
            }

            ITelemetryControl.CONTROL_BA_ENTRYPOINT -> {
                val entryPoint = arguments[ITelemetryControl.CONTROL_ARGUMENT_ENTRYPOINT]
                val certificateHash = arguments[ITelemetryControl.CONTROL_ARGUMENT_CERT_HASH]
                DevConfig.get(context).setBaEntrypoint(entryPoint, certificateHash)
                receiver.notifyNewSessionStarted()
            }
        }

    }

    private fun readPrefsMap(key: String): PrefsMap {
        return sharedPreferences.getString(key, null)?.let { prefs ->
            gson.fromJson(prefs, object : TypeToken<PrefsMap?>() {}.type)
        } ?: emptyMap()
    }

    private fun storePrefsMap(key: String, map: PrefsMap) {
        sharedPreferences.edit {
            putString(key, gson.toJson(map))
        }
    }

    private fun registerNsdService(deviceId: String) {
        try {
            nsdManager.registerService(
                NsdServiceInfo().apply {
                    serviceName = NsdConfig.SERVICE_NAME
                    serviceType = NsdConfig.SERVICE_TYPE
                    port = CONNECTION_TCP_PORT
                    setAttribute("id", deviceId)
                },
                NsdManager.PROTOCOL_DNS_SD,
                nsdRegistrationListener
            )
        } catch (e: Exception) {
            LOG.w(TAG, "Failed to register NSD service", e)
        }
    }

    private fun unregisterNsdService() {
        try {
            nsdManager.unregisterService(nsdRegistrationListener)
        } catch (e: Exception) {
            LOG.w(TAG, "Failed to unregister NSD service", e)
        }
    }

    private val nsdRegistrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
            LOG.d(TAG, "NSD Registered")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            LOG.d(TAG, "NSD RegistrationFailed errorCode: $errorCode")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            LOG.d(TAG, "NSD Unregistered")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            LOG.d(TAG, "NSD Unregistration Failed errorCode: $errorCode")
        }
    }

    companion object {
        val TAG: String = TelemetryHolder::class.java.simpleName

        private const val IoT_PREFERENCE = "${BuildConfig.LIBRARY_PACKAGE_NAME}.awsiot"

        private val CONNECTION_TYPE = ConnectionType.HTTP
        private const val CONNECTION_HOST = "0.0.0.0"
        private const val CONNECTION_TCP_PORT = 8081
        private const val CONNECTION_UDP_PORT = 6969

        private const val AWSIOT_RECEIVER_TEMPLATE_NAME = "ATSC3MobileReceiverProvisioning"
        private const val AWSIOT_CLIENT_ID_FORMAT = "ATSC3MobileReceiver_${AWSIoThing.AWSIOT_FORMAT_SERIAL}"
        private const val AWSIOT_EVENT_TOPIC_FORMAT = "telemetry/${AWSIoThing.AWSIOT_FORMAT_SERIAL}"
        private const val AWSIOT_TOPIC_CONTROL = "control/${AWSIoThing.AWSIOT_FORMAT_SERIAL}"
        private const val AWSIOT_GLOBAL_TOPIC_CONTROL = "global/command/request/#"

        const val SHARED_PREFERENCES_NAME = "telemetry_pref"
        const val PREF_DEBUG_INFO_KEY = "telemetry_info_debug"
        const val PREF_PHY_INFO_KEY = "telemetry_info_phy"
        const val PREF_LOG_INFO_KEY = "telemetry_info_log"
    }
}