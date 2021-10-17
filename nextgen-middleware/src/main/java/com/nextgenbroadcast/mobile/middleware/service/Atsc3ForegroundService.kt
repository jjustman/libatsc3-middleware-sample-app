package com.nextgenbroadcast.mobile.middleware.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.*
import android.os.*
import android.os.PowerManager.WakeLock
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.util.TimeUtils
import com.nextgenbroadcast.mobile.core.MiddlewareConfig
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.core.model.ReceiverState
import com.nextgenbroadcast.mobile.middleware.*
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts.AeaTable
import com.nextgenbroadcast.mobile.middleware.atsc3.source.Atsc3Source
import com.nextgenbroadcast.mobile.middleware.atsc3.source.UsbPhyAtsc3Source
import com.nextgenbroadcast.mobile.middleware.notification.AlertNotificationHelper
import com.nextgenbroadcast.mobile.middleware.phy.Atsc3DeviceReceiver
import com.nextgenbroadcast.mobile.middleware.server.web.IMiddlewareWebServer
import com.nextgenbroadcast.mobile.middleware.service.holder.*
import com.nextgenbroadcast.mobile.middleware.service.holder.SrtListHolder.Companion.SRT_PATH_SEPARATOR
import com.nextgenbroadcast.mobile.middleware.service.init.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.random.Random

abstract class Atsc3ForegroundService : BindableForegroundService() {
    private val usbManager: UsbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }
    private val powerManager: PowerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private val alertNotificationHelper by lazy {
        AlertNotificationHelper(this)
    }

    //TODO: create internal scope?
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private lateinit var playbackState: StateFlow<PlaybackState>

    // Receiver Core
    private lateinit var atsc3Receiver: Atsc3ReceiverCore
    private lateinit var webServer: WebServerHolder
    private lateinit var wakeLock: WakeLock

    // Media Service
    private lateinit var media: MediaHolder

    // Telemetry
    internal lateinit var telemetryHolder: TelemetryHolder

    // Srt
    private lateinit var srtListHolder: SrtListHolder

    // Location
    private lateinit var locationHolder: LocationHolder

    // Initialization from Service metadata
    private val initializer = ArrayList<WeakReference<IServiceInitializer>>()
    private var isInitialized = false

    private var deviceReceiver: Atsc3DeviceReceiver? = null

    override fun onCreate() {
        super.onCreate()

        atsc3Receiver = Atsc3ReceiverStandalone.get(applicationContext)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Atsc3ForegroundService::lock")

        locationHolder = LocationHolder(applicationContext, atsc3Receiver)

        media = MediaHolder(applicationContext, atsc3Receiver).also {
            it.open()
            sessionToken = it.sessionToken
        }

        if (MiddlewareConfig.DEV_TOOLS) {
            telemetryHolder = TelemetryHolder(applicationContext, atsc3Receiver).also {
                it.open()
            }
        }

        webServer = WebServerHolder(applicationContext, atsc3Receiver,
            this::onWebServerCreated,
            this::onWebServerDestroyed
        )

        srtListHolder = SrtListHolder(applicationContext).apply {
            read()
        }

        atsc3Receiver.setRouteList(srtListHolder.getFullSrtList())

        startStateObservation()

        maybeInitialize()
    }

    private fun onWebServerCreated(server: IMiddlewareWebServer) {
        // used to rebuild data related to server
        atsc3Receiver.notifyNewSessionStarted()

        if (MiddlewareConfig.DEV_TOOLS) {
            telemetryHolder.notifyWebServerStarted(server)
        }
    }

    private fun onWebServerDestroyed() {
        if (MiddlewareConfig.DEV_TOOLS) {
            telemetryHolder.notifyWebServerStopped()
        }
    }

    private fun startStateObservation() {
        playbackState = combine(media.embeddedPlayerState, atsc3Receiver.viewController.rmpState) { firstState, secondState ->
            if (firstState == PlaybackState.PLAYING || secondState == PlaybackState.PLAYING) {
                PlaybackState.PLAYING
            } else if (firstState == PlaybackState.PAUSED || secondState == PlaybackState.PAUSED) {
                PlaybackState.PAUSED
            } else {
                PlaybackState.IDLE
            }
        }.stateIn(serviceScope, SharingStarted.Eagerly, PlaybackState.IDLE)

        locationHolder.open(serviceScope)

        serviceScope.launch {
            playbackState.collect { state ->
                withContext(Dispatchers.Main) {
                    media.onPlaybackStateChanged(state)
                }
            }
        }

        serviceScope.launch {
            atsc3Receiver.observeCombinedState(playbackState) { (receiverState, selectedService, playbackState) ->
                withContext(Dispatchers.Main) {
                    if (isForeground) {
                        pushNotification(createNotificationBuilder(receiverState, selectedService, playbackState))
                    }
                }
            }
        }

        serviceScope.launch {
            atsc3Receiver.observeReceiverState { state ->
                withContext(Dispatchers.Main) {
                    val receiverState = state.state
                    if (receiverState == ReceiverState.State.IDLE) {
                        onRouteClosed()
                    } else if (receiverState >= ReceiverState.State.READY) {
                        onRouteOpened()
                    }
                }
            }
        }

        serviceScope.launch {
            atsc3Receiver.observeRouteServices { services ->
                withContext(Dispatchers.Main) {
                    media.onServiceListChanged(services)
                }
            }
        }

        serviceScope.launch {
            atsc3Receiver.repository.selectedService.collect { service ->
                withContext(Dispatchers.Main) {
                    media.onServiceChanged(service)
                }
            }
        }

        serviceScope.launch {
            atsc3Receiver.repository.routeMediaUrl.collect { mediaPath ->
                withContext(Dispatchers.Main) {
                    media.onMediaUrlChanged(mediaPath)
                }
            }
        }

        if (resources.getBoolean(R.bool.testAlertsEnabled)) {
            serviceScope.launch {
                atsc3Receiver.repository.alertsForNotify.collect { alerts ->
                    val locale = atsc3Receiver.settings.locale.language
                    showAlertNotifications(alerts, locale)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        initializer.forEach { ref ->
            ref.get()?.cancel()
        }

        unlockService()

        media.close()
        webServer.close()
        atsc3Receiver.deInitialize()
        serviceScope.cancel()
        if (MiddlewareConfig.DEV_TOOLS) {
            telemetryHolder.close()
        }
    }

    internal abstract fun createServiceBinder(receiver: Atsc3ReceiverCore): IBinder?

    override fun onBind(intent: Intent): IBinder? {
        var binder = super.onBind(intent)

        if (intent.action == SERVICE_INTERFACE) {
            binder = createServiceBinder(atsc3Receiver)
        }

        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        super.onUnbind(intent)

        return true // allow reBind
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        media.handleIntent(intent)

        if (intent != null) {
            when (intent.action) {
                ACTION_START_FOREGROUND -> startForeground()

                ACTION_DEVICE_ATTACHED -> onDeviceAttached(
                        intent.getParcelableExtra(EXTRA_DEVICE),
                        intent.getIntExtra(EXTRA_DEVICE_TYPE, Atsc3Source.DEVICE_TYPE_AUTO),
                        intent.getBooleanExtra(EXTRA_FORCE_OPEN, true)
                )

                ACTION_DEVICE_DETACHED -> onDeviceDetached(intent.getParcelableExtra(EXTRA_DEVICE))

                ACTION_USB_PERMISSION -> intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false).let { granted ->
                    if (granted) {
                        onDevicePermissionGranted(
                                intent.getParcelableExtra(EXTRA_DEVICE),
                                intent.getIntExtra(EXTRA_DEVICE_TYPE, Atsc3Source.DEVICE_TYPE_AUTO),
                                intent.getBooleanExtra(EXTRA_FORCE_OPEN, true)
                        )
                    }
                }

                ACTION_RMP_PLAY -> media.resumePlayback()

                ACTION_RMP_PAUSE -> media.pausePlayback()

                ACTION_OPEN_ROUTE -> openRoute(
                    intent.getStringExtra(EXTRA_ROUTE_PATH),
                    intent.getBooleanExtra(EXTRA_FORCE_OPEN, true)
                )

                ACTION_CLOSE_ROUTE -> closeRoute()

                else -> {
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        //TODO: implement allowBrowsing() check like clientPackageName == packageName
        return MediaHolder.getRoot()
    }

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        if (MediaHolder.isRoot(parentId)) {
            result.sendResult(srtListHolder.getFullSrtList().map { source ->
                MediaHolder.getItem(source.title, source.path, source.id)
            })
            return
        }

        result.sendResult(emptyList())
    }

    private fun onRouteOpened() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        webServer.open()
    }

    private fun onRouteClosed() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        webServer.close()
    }

    private fun maybeInitialize() {
        if (isInitialized) return
        isInitialized = true

        val appContext = applicationContext
        val components = MetadataReader.discoverMetadata(this)

        val handler = CoroutineExceptionHandler { _, e ->
            Log.d(TAG, "Can't initialize, something is wrong in metadata", e)
        }

        try {
            val freqInitializer = FrequencyInitializer(atsc3Receiver).also {
                initializer.add(WeakReference(it))
            }
            serviceScope.launch(handler) {
                freqInitializer.initialize(appContext, components)
            }

            // Do not re-open the libatsc3 if it's already opened
            if (!atsc3Receiver.isIdle()) return

            val phyInitializer = OnboardPhyInitializer(atsc3Receiver).also {
                initializer.add(WeakReference(it))
            }

            serviceScope.launch(handler) {
                if (phyInitializer.initialize(appContext, components)) {
                    startForeground(applicationContext)
                } else {
                    withContext(Dispatchers.Main) {
                        UsbPhyInitializer().also {
                            initializer.add(WeakReference(it))
                        }
                    }.initialize(appContext, components)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Can't initialize, something is wrong in metadata", e)
        }

        val sourcePath = srtListHolder.getDefaultRoutes()
        val immutableSrtPool = srtListHolder.getPoolRoutes()
            ?.split(SRT_PATH_SEPARATOR)
            ?.takeIf { it.isNotEmpty() }
        if (sourcePath != null) {
            openRoute(applicationContext, sourcePath, false)
        } else if (immutableSrtPool != null){
            val random = Random(System.currentTimeMillis())
            val srtPool = immutableSrtPool.toMutableList()
            serviceScope.launch {
                var services: List<AVService>? = null
                while (isActive && services.isNullOrEmpty()) {
                    if (srtPool.isEmpty()) {
                        srtPool.addAll(immutableSrtPool)
                    }
                    val randomIndex = random.nextInt(srtPool.size)
                    val srt = srtPool[randomIndex]
                    srtPool.removeAt(randomIndex)
                    openRoute(applicationContext, srt, false)
                    services = withTimeoutOrNull(OPEN_TIMEOUT) {
                        var connectionServices:  List<AVService>?
                        do {
                            connectionServices = atsc3Receiver.repository.services.firstOrNull()
                        } while (isActive && connectionServices.isNullOrEmpty())
                        connectionServices
                    }
                }
            }
        }
    }

    private fun openRoute(sourcePath: String?, forceOpen: Boolean) {
        startForeground()
        // change source to file. So, let's unregister device receiver
        unregisterDeviceReceiver()

        sourcePath?.let {
            routePathToSource(applicationContext, sourcePath)?.let { source ->
                atsc3Receiver.openRoute(source, forceOpen)
            }
        }
    }

    private fun openRoute(device: UsbDevice, deviceType: Int, forceOpen: Boolean) {
        startForeground()
        unregisterDeviceReceiver()

        atsc3Receiver.openRoute(UsbPhyAtsc3Source(usbManager, device, deviceType), forceOpen)

        // Register BroadcastReceiver to detect when device is disconnected
        registerDeviceReceiver(device)
    }

    private fun closeRoute() {
        unregisterDeviceReceiver()

        atsc3Receiver.closeRoute()

        if (!isBinded) {
            unlockService()
        }

        stopSelf()
    }

    private fun unlockService() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        unregisterDeviceReceiver()
        stopForeground()
    }

    private fun onDeviceAttached(device: UsbDevice?, deviceType: Int, forceOpen: Boolean) {
        if (device == null) {
            if (!isForeground && !isBinded) {
                stopSelf()
            }
            return
        }

        //TODO: process case with second connected device
        if (usbManager.hasPermission(device)) {
            openRoute(device, deviceType, forceOpen)

            // ensure TV app is visible
            if (forceOpen) {
                startTVApplication(this)
            }
        } else {
            requestDevicePermission(device, deviceType, forceOpen)
        }
    }

    private fun onDeviceDetached(device: UsbDevice?) {
        closeRoute()
    }

    private fun onDevicePermissionGranted(device: UsbDevice?, deviceType: Int, forceOpen: Boolean) {
        device?.let {
            // open device using a new Intent to start Service as foreground
            startForDevice(this, device, deviceType, forceOpen)
        }
    }

    private fun registerDeviceReceiver(device: UsbDevice) {
        deviceReceiver = Atsc3DeviceReceiver(device.deviceName) {
            stopForDevice(applicationContext, device)
        }.also { receiver ->
            registerReceiver(receiver, receiver.intentFilter)
        }
    }

    private fun unregisterDeviceReceiver() {
        deviceReceiver?.let { receiver ->
            unregisterReceiver(receiver)
            deviceReceiver = null
        }
    }

    private fun requestDevicePermission(device: UsbDevice, deviceType: Int, forceOpen: Boolean) {
        val intent = Intent(this, clazz).apply {
            action = ACTION_USB_PERMISSION
            putExtra(EXTRA_DEVICE_TYPE, deviceType)
            putExtra(EXTRA_FORCE_OPEN, forceOpen)
        }
        usbManager.requestPermission(device, PendingIntent.getService(this, 0, intent, 0))
    }

    override fun getReceiverState() = atsc3Receiver.getReceiverState()

    private fun showAlertNotifications(alerts: List<AeaTable>, locale: String) {
        alerts.forEach { alert ->
            val message = alert.messages?.let { msgMap ->
                msgMap[locale] ?: msgMap[Locale.US.language]
                ?: msgMap.values.firstOrNull()
            }

            message?.let { msg ->
                alertNotificationHelper.showNotification(msg, alert.id, alert.effective)
                startActivity(
                    AlertDialogActivity.newIntent(
                        this@Atsc3ForegroundService, alert.id, msg, alert.effective
                    )
                )
            }
        }
    }

    companion object {
        val TAG: String = Atsc3ForegroundService::class.java.simpleName

        const val SERVICE_INTERFACE = "${BuildConfig.LIBRARY_PACKAGE_NAME}.INTERFACE"

        private val OPEN_TIMEOUT = TimeUnit.SECONDS.toMillis(25L)

        private const val SERVICE_ACTION = "${BuildConfig.LIBRARY_PACKAGE_NAME}.intent.action"

        internal const val ACTION_START_FOREGROUND = "$SERVICE_ACTION.START_FOREGROUND"

        const val ACTION_DEVICE_ATTACHED = "$SERVICE_ACTION.USB_ATTACHED"
        const val ACTION_DEVICE_DETACHED = "$SERVICE_ACTION.USB_DETACHED"
        const val ACTION_USB_PERMISSION = "$SERVICE_ACTION.USB_PERMISSION"
        const val ACTION_RMP_PLAY = "$SERVICE_ACTION.RMP_PLAY"
        const val ACTION_RMP_PAUSE = "$SERVICE_ACTION.RMP_PAUSE"
        const val ACTION_OPEN_ROUTE = "$SERVICE_ACTION.OPEN_ROUTE"
        const val ACTION_CLOSE_ROUTE = "$SERVICE_ACTION.CLOSE_ROUTE"

        const val EXTRA_DEVICE = UsbManager.EXTRA_DEVICE
        const val EXTRA_DEVICE_TYPE = "device_type"
        const val EXTRA_ROUTE_PATH = "route_path"
        const val EXTRA_PLAY_AUDIO_ON_BOARD = "play_audio_on_board"
        const val EXTRA_FORCE_OPEN = "force_open"

        internal lateinit var clazz: Class<out Atsc3ForegroundService>

        internal fun startForeground(context: Context) {
            newIntent(context, ACTION_START_FOREGROUND).let { serviceIntent ->
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        internal fun startForDevice(context: Context, device: UsbDevice, deviceType: Int, forceOpen: Boolean = true) {
            newIntent(context, ACTION_DEVICE_ATTACHED).let { serviceIntent ->
                serviceIntent.putExtra(EXTRA_DEVICE, device)
                serviceIntent.putExtra(EXTRA_DEVICE_TYPE, deviceType)
                serviceIntent.putExtra(EXTRA_FORCE_OPEN, forceOpen)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        internal fun stopForDevice(context: Context, device: UsbDevice) {
            newIntent(context, ACTION_DEVICE_DETACHED).let { serviceIntent ->
                serviceIntent.putExtra(EXTRA_DEVICE, device)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        internal fun openRoute(context: Context, filePath: String, forceOpen: Boolean = true) {
            newIntent(context, ACTION_OPEN_ROUTE).let { serviceIntent ->
                serviceIntent.putExtra(EXTRA_ROUTE_PATH, filePath)
                serviceIntent.putExtra(EXTRA_FORCE_OPEN, forceOpen)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }

        internal fun closeRoute(context: Context) {
            ContextCompat.startForegroundService(context, newIntent(context, ACTION_CLOSE_ROUTE))
        }

        private fun newIntent(context: Context, serviceAction: String) = Intent(context, clazz).apply {
            action = serviceAction
            putExtra(EXTRA_FOREGROUND, true)
        }
    }
}