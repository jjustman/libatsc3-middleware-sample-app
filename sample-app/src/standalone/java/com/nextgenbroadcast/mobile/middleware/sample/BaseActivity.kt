package com.nextgenbroadcast.mobile.middleware.sample

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.appcompat.app.AppCompatActivity
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.dev.service.binder.IServiceBinder
import com.nextgenbroadcast.mobile.core.dev.service.presentation.IControllerPresenter
import com.nextgenbroadcast.mobile.middleware.service.StandaloneAtsc3Service

abstract class BaseActivity : AppCompatActivity() {
    private lateinit var mediaBrowser: MediaBrowserCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, StandaloneAtsc3Service::class.java),
            connectionCallbacks,
            null
        )
    }

    fun bindService() {
        // The media session connection will start ForegroundService that requires permission.
        // This method called after permission request, that's why it should be here.
        if (!mediaBrowser.isConnected) {
            try {
                mediaBrowser.connect()
            } catch (e: IllegalStateException) {
                LOG.d(TAG, "Failed to connect Media Browser: ", e)
            }
        }

        onBind(object : IServiceBinder {
            override val controllerPresenter: IControllerPresenter? = null
        })
    }

    fun unbindService() {
        try {
            mediaBrowser.disconnect()
        } catch (e: IllegalStateException) {
            LOG.d(TAG, "Failed to disconnect Media Browser: ", e)
        }

        onUnbind()
    }

    abstract fun onBind(binder: IServiceBinder)
    abstract fun onUnbind()

    abstract fun onSourcesAvailable(sources: List<MediaBrowserCompat.MediaItem>)
    abstract fun onMediaSessionCreated()

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val mediaController = MediaControllerCompat(this@BaseActivity, mediaBrowser.sessionToken)
            MediaControllerCompat.setMediaController(this@BaseActivity, mediaController)

            mediaBrowser.subscribe(mediaBrowser.root, object : MediaBrowserCompat.SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
                    onSourcesAvailable(children)
                }
            })

            onMediaSessionCreated()
        }

        override fun onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
        }

        override fun onConnectionFailed() {
            // The Service has refused our connection
        }
    }

    companion object {
        val TAG: String = BaseActivity::class.java.simpleName
    }
}