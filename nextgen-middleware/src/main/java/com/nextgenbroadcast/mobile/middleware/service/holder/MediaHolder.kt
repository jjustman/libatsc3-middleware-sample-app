package com.nextgenbroadcast.mobile.middleware.service.holder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.MainThread
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.MiddlewareConfig
import com.nextgenbroadcast.mobile.middleware.Atsc3ReceiverCore
import com.nextgenbroadcast.mobile.middleware.service.Atsc3ForegroundService
import com.nextgenbroadcast.mobile.core.media.MediaSessionConstants
import com.nextgenbroadcast.mobile.core.model.*
import com.nextgenbroadcast.mobile.middleware.dev.config.DevConfig
import com.nextgenbroadcast.mobile.player.Atsc3MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow

internal class MediaHolder(
        private val context: Context,
        private val receiver: Atsc3ReceiverCore
) {
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private lateinit var player: Atsc3MediaPlayer

    val sessionToken: MediaSessionCompat.Token
        get() = mediaSession.sessionToken

    val embeddedPlayerState = MutableStateFlow(PlaybackState.IDLE)

    @MainThread
    fun open() {
        stateBuilder = PlaybackStateCompat.Builder()
        mediaSession = MediaSessionCompat(context, Atsc3ForegroundService.TAG).apply {
            setPlaybackState(stateBuilder.build())
            setCallback(MediaSessionCallback())
        }

        player = Atsc3MediaPlayer(context).apply {
            setListener(object : Atsc3MediaPlayer.EventListener {
                override fun onPlayerStateChanged(state: PlaybackState) {
                    embeddedPlayerState.value = state
                }

                override fun onPlayerError(error: Exception) {
                    LOG.d(Atsc3ForegroundService.TAG, error.message ?: "")
                }

                override fun onPlaybackSpeedChanged(speed: Float) {
                    receiver.viewController.rmpPlaybackRateChanged(speed)
                }
            })
        }
    }

    @MainThread
    fun close() {
        player.reset()
        mediaSession.release()
    }

    @MainThread
    fun handleIntent(intent: Intent?) {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
    }

    fun onServiceListChanged(services: List<AVService>) {
        val defaultService = if (MiddlewareConfig.DEV_TOOLS) {
            DevConfig.get(context).service
        } else null

        val queue = services.map { service ->
            val srv = if (service.isServiceEquals(defaultService)) {
                service.copy(default = true)
            } else service

            MediaSessionCompat.QueueItem(
                    MediaDescriptionCompat.Builder()
                            .setMediaId(srv.globalId)
                            .setTitle(srv.shortName)
                            .setExtras(srv.toBundle())
                            .build(),
                srv.uniqueId
            )
        }

        try {
            mediaSession.setQueue(queue)
        } catch (e: IllegalArgumentException) {
            LOG.e(Atsc3ForegroundService.TAG, "Can't set media queue", e)
        }
    }

    fun onServiceChanged(service: AVService?) {
        mediaSession.setQueueTitle(service?.shortName)
        mediaSession.isActive = service != null
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder().apply {
                if (service != null) {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, service.globalId)
                    putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, service.category.toLong())
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, service.shortName)
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "${service.majorChannelNo}-${service.minorChannelNo}")
                }
            }.build()
        )
    }

    fun selectMediaService(service: AVService) {
        player.reset()
        receiver.selectService(service)
    }

    fun onPlaybackStateChanged(state: PlaybackState) {
        if (state == PlaybackState.PLAYING) {
            setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        } else {
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
        }
    }

    private fun setPlaybackState(@PlaybackStateCompat.State state: Int) {
        val playbackState = if (state == PlaybackStateCompat.STATE_PLAYING) {
            PlaybackStateCompat.ACTION_PAUSE
        } else {
            PlaybackStateCompat.ACTION_PLAY
        }
        stateBuilder
                .setActions(
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                or playbackState
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                .setExtras(Bundle().apply {
                    putBoolean(MediaSessionConstants.MEDIA_PLAYBACK_EXTRA_EMBEDDED, embeddedPlayerState.value != PlaybackState.IDLE)
                })
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    fun onMediaUrlChanged(mediaUrl: MediaUrl?) {
        val canPlay = mediaUrl?.let {
            receiver.findServiceById(mediaUrl.bsid, mediaUrl.serviceId)
        }?.let { service ->
            receiver.playEmbedded(service)
        }

        if (canPlay == true) {
            player.play(receiver.getMediaUri(mediaUrl))
        } else {
            player.reset()
        }
    }

    fun stopPlaybackIfInitialized() {
        if (player.isInitialized) {
            val service = receiver.repository.routeMediaUrl.value?.let { mediaPath ->
                receiver.findServiceById(mediaPath.bsid, mediaPath.serviceId)
            }

            if (service != null && !receiver.playEmbedded(service)) {
                player.reset()
            }
        }
    }

    fun pausePlayback() {
        // ignore action if playback forced to stop
        if (receiver.repository.requestedMediaState.value == PlaybackState.IDLE) return

        if (player.isInitialized) {
            player.pause()
        } else {
            receiver.repository.setRequestedMediaState(PlaybackState.PAUSED)
        }
    }

    fun resumePlayback() {
        // ignore action if playback forced to stop
        if (receiver.repository.requestedMediaState.value == PlaybackState.IDLE) return

        if (player.isInitialized) {
            player.tryReplay()
        } else {
            receiver.repository.setRequestedMediaState(PlaybackState.PLAYING)
        }
    }

    inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            resumePlayback()
        }

        override fun onPause() {
            pausePlayback()
        }

        override fun onSkipToNext() {
            receiver.getNextService()?.let { service ->
                selectMediaService(service)
            }
        }

        override fun onSkipToPrevious() {
            receiver.getPreviousService()?.let { service ->
                selectMediaService(service)
            }
        }

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val globalId = mediaId ?: return
            receiver.findServiceById(globalId)?.let { service ->
                selectMediaService(service)
            }
        }
    }

    companion object {
        private const val MEDIA_ROOT_ID = "2262d068-67cf-11eb-ae93-0242ac130002"

        fun isRoot(id: String) = id == MEDIA_ROOT_ID

        fun getRoot() = MediaBrowserServiceCompat.BrowserRoot(MEDIA_ROOT_ID, null)

        fun getItem(title: String, path: String, id: String) = MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                        .setMediaId(id)
                        .setTitle(title)
                        .setMediaUri(Uri.parse(path))
                        .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        )
    }
}