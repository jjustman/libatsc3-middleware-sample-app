package com.nextgenbroadcast.mobile.middleware.sample

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.tasks.Task
import androidx.lifecycle.lifecycleScope
import com.nextgenbroadcast.mobile.core.getApkBaseServicePackage
import com.nextgenbroadcast.mobile.core.media.MediaSessionConstants
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.dev.service.presentation.IControllerPresenter
import com.nextgenbroadcast.mobile.core.dev.service.binder.IServiceBinder
import com.nextgenbroadcast.mobile.core.model.toAVService
import com.nextgenbroadcast.mobile.middleware.sample.lifecycle.ViewViewModel
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.ReceiverTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {
    private val viewViewModel: ViewViewModel by viewModels()

    private val hasFeaturePIP: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private val permissionResolver: PermissionResolver by lazy {
        PermissionResolver(this)
    }

    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var rootView: View

    private var controllerPresentationJob: Job? = null

    override fun onBind(binder: IServiceBinder) {
        binder.controllerPresenter?.let { controllerPresenter ->
            bindControlPresenter(controllerPresenter)
        }
    }

    override fun onUnbind() {
        viewViewModel.clearSubscriptions(this)
        controllerPresentationJob?.cancel()
        controllerPresentationJob = null
    }

    override fun onSourcesAvailable(sources: List<MediaBrowserCompat.MediaItem>) {
        viewViewModel.sources.value = sources.mapNotNull { mediaItem ->
            if (mediaItem.isBrowsable) {
                val title = mediaItem.description.title
                val mediaUri = mediaItem.description.mediaUri
                if (title != null && mediaUri != null) {
                    return@mapNotNull Pair(title.toString(), mediaUri.toString())
                }
            }
            null
        }
    }

    override fun onMediaSessionCreated() {
        with(mediaController) {
            viewViewModel.services.value = queue?.mapNotNull { it.toService() } ?: emptyList()
            viewViewModel.currentServiceTitle.value = queueTitle?.toString()
            viewViewModel.isPlaying.value = playbackState?.state == PlaybackState.STATE_PLAYING

            registerCallback(mediaControllerCallback)
        }
    }

    private fun MediaSession.QueueItem.toService(): AVService? {
        return description.extras?.toAVService()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootView = window.decorView.findViewById(android.R.id.content)
        appUpdateManager = AppUpdateManagerFactory.create(applicationContext)

        // ignore if activity was restored
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .add(android.R.id.content,
                    MainFragment.newInstance(),
                    MainFragment.TAG
                )
                .commit()

            tryOpenPcapFile(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        tryOpenPcapFile(intent)
    }

    override fun onStart() {
        super.onStart()

        checkForAppUpdates()

        //make sure we can read from device pcap files and get location
        if (permissionResolver.checkSelfPermission()) {
            bindService()
        }
    }

    override fun onResume() {
        super.onResume()

        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, APP_UPDATE_REQUEST_CODE)
            }
        }
    }

    override fun onStop() {
        Log.w(TAG, "onStop() invoked - our activity is no longer visible")

        super.onStop()

        mediaController?.unregisterCallback(mediaControllerCallback)

        unbindService()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        updateSystemUi(resources.configuration)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        updateSystemUi(newConfig)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val granted = permissionResolver.processPermissionsResult(requestCode, permissions, grantResults)
        if (granted) {
            bindService()
        }
    }

    override fun onUserLeaveHint() {
        mediaController?.playbackState?.let { playbackState ->
            val embedded = playbackState.extras?.getBoolean(MediaSessionConstants.MEDIA_PLAYBACK_EXTRA_EMBEDDED)
            if (hasFeaturePIP && playbackState.state == PlaybackState.STATE_PLAYING && embedded != true) {
                enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            }
        }
    }

    private fun updateSystemUi(config: Configuration) {
        if (isInPictureInPictureMode) return

        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            WindowInsetsControllerCompat(window, rootView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, rootView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun bindControlPresenter(controllerPresenter: IControllerPresenter) {
        with(viewViewModel) {
            controllerPresentationJob = lifecycleScope.launch {
                launch {
                    controllerPresenter.debugInfoSettings.collect { debugInfoSetting ->
                        val isDebugInfoEnable = debugInfoSetting[ReceiverTelemetry.INFO_DEBUG] ?: false
                        val isPhyInfoEnable = debugInfoSetting[ReceiverTelemetry.INFO_PHY] ?: false
                        val isPhyChartEnable = debugInfoSetting[ReceiverTelemetry.INFO_PHY_CHART] ?: false

                        showDebugInfo.value = isDebugInfoEnable || isPhyInfoEnable
                        showPhyInfo.value = isPhyInfoEnable
                        showPhyChart.value = isPhyChartEnable
                    }
                }

                launch {
                    controllerPresenter.telemetryEnabled.collect { enableMap ->
                        val distValues = enableMap.values.distinct()
                        if (distValues.size == 1) {
                            enableTelemetry.value = distValues.first()
                        } else {
                            enableTelemetry.value = distValues.size > 1
                        }
                        val distSensorValues = enableMap.filterKeys {
                            it.startsWith(ReceiverTelemetry.TELEMETRY_SENSORS)
                        }.values.distinct()
                        sensorTelemetryEnabled.value = distSensorValues.isNotEmpty() && (distSensorValues.size > 1 || distSensorValues.first())
                        locationTelemetryEnabled.value = enableMap[ReceiverTelemetry.TELEMETRY_LOCATION]
                    }
                }

                launch {
                    controllerPresenter.logInfoSettings.collect { logsMap ->
                        logsInfo.value = logsMap
                    }
                }

                launch {
                    for (event in viewViewModel.logChangingChannel) {
                        controllerPresenter.setAtsc3LogEnabledByName(event.first, event.second)
                    }
                }
            }

            showDebugInfo.observe(this@MainActivity) { showDebugInfo ->
                if (showDebugInfo != null) {
                    val actualValue = controllerPresenter.debugInfoSettings.value[ReceiverTelemetry.INFO_DEBUG]
                    setIfChanged(actualValue, showDebugInfo) { enabled ->
                        controllerPresenter.setDebugInfoVisible(ReceiverTelemetry.INFO_DEBUG, enabled)
                    }
                }
            }

            showPhyInfo.observe(this@MainActivity) { showPhyInfo ->
                if (showPhyInfo != null) {
                    val actualValue = controllerPresenter.debugInfoSettings.value[ReceiverTelemetry.INFO_PHY]
                    setIfChanged(actualValue, showPhyInfo) { enabled ->
                        controllerPresenter.setDebugInfoVisible(ReceiverTelemetry.INFO_PHY, enabled)
                    }
                }
            }

            showPhyChart.observe(this@MainActivity) { showPhyChart ->
                if (showPhyChart != null) {
                    val actualValue = controllerPresenter.debugInfoSettings.value[ReceiverTelemetry.INFO_PHY_CHART]
                    setIfChanged(actualValue, showPhyChart) { enabled ->
                        controllerPresenter.setDebugInfoVisible(ReceiverTelemetry.INFO_PHY_CHART, enabled)
                    }
                }
            }

            enableTelemetry.observe(this@MainActivity) { enableTelemetry ->
                if (enableTelemetry != null) {
                    val actualValue = controllerPresenter.telemetryEnabled.value.distinctValue()
                    // switch must be On if telemetry partially switched On. We allow only switching off partially active telemetry
                    setIfChanged(actualValue, enableTelemetry) { enabled ->
                        controllerPresenter.setTelemetryEnabled(enabled)
                    }
                }
            }

            sensorTelemetryEnabled.observe(this@MainActivity) { sensorEnabled ->
                if (sensorEnabled != null) {
                    val actualValue = controllerPresenter.telemetryEnabled.value.distinctValue(ReceiverTelemetry.TELEMETRY_SENSORS)
                    // switch must be On if if one of sensors is active. We allow only switching off partially active sensors
                    setIfChanged(actualValue, sensorEnabled) { enabled ->
                        controllerPresenter.setTelemetryEnabled(ReceiverTelemetry.TELEMETRY_SENSORS, enabled)
                    }
                }
            }
            sensorFrequencyType.observe(this@MainActivity) { frequencyType ->
                if (frequencyType != null) {
                    controllerPresenter.setTelemetryUpdateDelay(ReceiverTelemetry.TELEMETRY_SENSORS, frequencyType.delayMils)
                }
            }
            locationTelemetryEnabled.observe(this@MainActivity) { sensorEnabled ->
                if (sensorEnabled != null) {
                    controllerPresenter.setTelemetryEnabled(ReceiverTelemetry.TELEMETRY_LOCATION, sensorEnabled)
                }
            }
            locationFrequencyType.observe(this@MainActivity) { frequencyType ->
                if (frequencyType != null) {
                    controllerPresenter.setTelemetryUpdateDelay(ReceiverTelemetry.TELEMETRY_LOCATION, frequencyType.delay())
                }
            }

        }
    }

    private fun setIfChanged(actual: Boolean?, new: Boolean, block: (value: Boolean) -> Unit) {
        if (actual != null) {
            if (actual != new) {
                block(new)
            }
        } else if (!new) {
            block(false)
        }
    }

    private fun <K, V> Map<out K, V>.distinctValue(): V? {
        return values.distinct().let {
            if (it.size == 1) it.first() else null
        }
    }

    private fun <V> Map<String, V>.distinctValue(key: String): V? {
        return filterKeys {
            it.startsWith(key)
        }.distinctValue()
    }

    private fun checkForAppUpdates() {
        // Creates instance of the manager, returns an intent object that you use to check for an update.
        val appUpdateInfoTask: Task<AppUpdateInfo> = appUpdateManager.appUpdateInfo

        // Checks that the platform will allow the specified type of update. // For a flexible update, use AppUpdateType.FLEXIBLE
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            Log.i(TAG, "appUpdateInfoTask.onSuccessListener: appUpdateInfo: $appUpdateInfo")
            //jjustman-2021-05-04 - always push our ATSC3 sample app updates, e.g. dont gate on only && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                // Request the update.
                appUpdateManager.startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, this, APP_UPDATE_REQUEST_CODE)
            }
        }

        appUpdateInfoTask.addOnFailureListener { failedInfo ->
            Log.w(TAG, "appUpdateInfoTask.onFailureListener: failedInfo: $failedInfo")
        }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            viewViewModel.services.value = queue?.mapNotNull { it.toService() } ?: emptyList()
        }

        override fun onQueueTitleChanged(title: CharSequence?) {
            viewViewModel.currentServiceTitle.value = title?.toString()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            viewViewModel.isPlaying.value = state?.state == PlaybackState.STATE_PLAYING
        }

        override fun onSessionDestroyed() {
            mediaController?.unregisterCallback(this)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata?.let {
                val serviceCategory = metadata.getLong(MediaMetadata.METADATA_KEY_DISC_NUMBER).toInt()
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)?.let { serviceGlobalId ->
                    getApkBaseServicePackage(serviceCategory, serviceGlobalId)?.let { appPackage ->
                        launchApplication(appPackage)
                    }
                }
            }
        }
    }

    private fun launchApplication(appPackage: String) {
        val started: Boolean = packageManager.getLaunchIntentForPackage(appPackage)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        } ?: false

        if (!started) {
            Toast.makeText(this@MainActivity, getString(R.string.message_service_apk_not_found, appPackage), Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryOpenPcapFile(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                ReceiverContentResolver.openRoute(applicationContext, uri)
            }
        }
    }

    companion object {
        val TAG: String = MainActivity::class.java.simpleName

        private const val APP_UPDATE_REQUEST_CODE = 31337
    }
}