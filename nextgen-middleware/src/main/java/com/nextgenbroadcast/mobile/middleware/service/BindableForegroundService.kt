package com.nextgenbroadcast.mobile.middleware.service

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.core.model.ReceiverState
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.middleware.R
import com.nextgenbroadcast.mobile.core.atsc3.SLTConstants
import com.nextgenbroadcast.mobile.middleware.notification.NotificationHelper

abstract class BindableForegroundService : MediaBrowserServiceCompat() {
    private lateinit var notificationHelper: NotificationHelper

    protected var isForeground = false
        private set
    protected var isBinded = false
        private set
    protected var isStartedAsForeground = false
        private set

    override fun onCreate() {
        super.onCreate()

        notificationHelper = NotificationHelper(this, NOTIFICATION_CHANNEL_ID).also {
            it.createNotificationChannel(getString(R.string.atsc3_chanel_name))
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        notificationHelper.cancel(NOTIFICATION_ID)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isStartedAsForeground = isStartedAsForeground or (intent?.getBooleanExtra(EXTRA_FOREGROUND, false)
                ?: false)

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        val binder = super.onBind(intent)

        startForeground()
        isBinded = true

        return binder
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        isBinded = true
    }

    override fun onUnbind(intent: Intent): Boolean {
        super.onUnbind(intent)
        isBinded = false
        return false
    }

    protected fun startForeground() {
        if (isForeground) return
        isForeground = true

        startForeground(NOTIFICATION_ID, createNotificationBuilder(getReceiverState()).build())
    }

    protected fun stopForeground() {
        stopForeground(true)

        isForeground = false
    }

    protected fun createNotificationBuilder(receiverState: ReceiverState? = null, service: AVService? = null, playbackState: PlaybackState? = null): NotificationCompat.Builder {
        val state = receiverState?.state
        val title = if (state == null || state == ReceiverState.State.IDLE) {
            getString(R.string.atsc3_source_is_not_initialized)
        } else {
            service?.shortName ?: getString(R.string.atsc3_no_service_available)
        }

        val text = if (state == null || state == ReceiverState.State.IDLE) {
            getString(R.string.atsc3_receiver_state_idle)
        } else if (playbackState != PlaybackState.IDLE) {
            if (playbackState == PlaybackState.PLAYING) {
                getString(R.string.atsc3_receiver_state_playing)
            } else {
                getString(R.string.atsc3_receiver_state_paused)
            }
        } else if (service != null) {
            getString(R.string.atsc3_receiver_state_buffering)
        } else {
            getString(R.string.atsc3_receiver_state_connecting)
        }

        val fixedPlaybackState = if (service == null || playbackState == null) {
            PlaybackState.IDLE
        } else if (playbackState == PlaybackState.IDLE) {
            if (service.category == SLTConstants.SERVICE_CATEGORY_AV || service.category == SLTConstants.SERVICE_CATEGORY_AO) {
                PlaybackState.PAUSED
            } else {
                PlaybackState.IDLE
            }
        } else {
            playbackState
        }

        return notificationHelper.createMediaNotificationBuilder(title, text, fixedPlaybackState, sessionToken)
    }

    protected fun pushNotification(notification: NotificationCompat.Builder) {
        pushNotification(notification.build())
    }

    protected fun pushNotification(notification: Notification) {
        notificationHelper.notify(NOTIFICATION_ID, notification)
    }

    protected abstract fun getReceiverState(): ReceiverState

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "Atsc3ServiceChannel"
        private const val NOTIFICATION_ID = 1

        const val EXTRA_FOREGROUND = "foreground"
    }
}