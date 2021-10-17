package com.nextgenbroadcast.mobile.middleware.sample.view

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.nextgenbroadcast.mobile.core.LOG
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.player.Atsc3MediaPlayer
import com.nextgenbroadcast.mobile.player.MMTConstants

typealias OnPlaybackChangeListener = (state: PlaybackState, position: Long, rate: Float) -> Unit

class ReceiverPlayerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    private val atsc3Player = Atsc3MediaPlayer(context).apply {
        resetWhenLostAudioFocus = false
    }

    private var buffering = false
    private var onPlaybackChangeListener: OnPlaybackChangeListener? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (isInEditMode) return

        atsc3Player.setListener(object : Atsc3MediaPlayer.EventListener {
            override fun onPlayerStateChanged(state: PlaybackState) {
                onPlaybackChangeListener?.invoke(state, atsc3Player.playbackPosition, atsc3Player.playbackSpeed)

                if (state == PlaybackState.PLAYING) {
                    keepScreenOn = true
                    startMediaTimeUpdate()
                } else {
                    keepScreenOn = false
                    cancelMediaTimeUpdate()
                }
            }

            override fun onPlayerError(error: Exception) {
                LOG.d(TAG, error.message ?: "")
            }

            override fun onPlaybackSpeedChanged(speed: Float) {
                // will be updated with position in timer
            }
        })
    }

    fun setOnPlaybackChangeListener(listener: OnPlaybackChangeListener) {
        onPlaybackChangeListener = listener
    }

    fun play(mediaUri: Uri) {
        if (atsc3Player.lastMediaUri == mediaUri && (atsc3Player.isPlaying || atsc3Player.isPaused)) return

        val mimeType = context.contentResolver.getType(mediaUri)

        LOG.i(TAG, String.format("play: with mediaUri: %s and mimeType: %s", mediaUri, mimeType))

        // AO service content will be played out with ForegroundService embedded player which is
        // indicated on binding service with flag EXTRA_PLAY_AUDIO_ON_BOARD
        if (mimeType == MMTConstants.MIME_MMT_AUDIO) {
            stopAndClear()
            return
        }

        atsc3Player.play(mediaUri, mimeType)

        player = atsc3Player.player?.also {
            it.addListener(object : Player.EventListener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    updateBufferingState(playbackState == Player.STATE_BUFFERING)
                }
            })
        }
    }

    fun tryReplay() {
        if (atsc3Player.isPlaying) return

        atsc3Player.tryReplay()
        // ensure we still observing correct player
        player = atsc3Player.player
    }

    fun pause() {
        atsc3Player.pause()
    }

    fun stop() {
        atsc3Player.stop()
    }

    fun stopAndClear() {
        atsc3Player.reset()
        player = null
        atsc3Player.clearSavedState()
    }

    private fun updateBufferingState(isBuffering: Boolean) {
        if (isBuffering) {
            if (!buffering) {
                buffering = true
                postDelayed(enableBufferingProgress, 500)
            }
        } else {
            buffering = false
            removeCallbacks(enableBufferingProgress)
            setShowBuffering(SHOW_BUFFERING_NEVER)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        removeCallbacks(enableBufferingProgress)
    }

    fun getTrackSelector(): DefaultTrackSelector? {
        return atsc3Player.trackSelector
    }

    private val enableBufferingProgress = Runnable {
        setShowBuffering(SHOW_BUFFERING_ALWAYS)
    }

    private val updateMediaTimeRunnable = object : Runnable {
        override fun run() {
            onPlaybackChangeListener?.invoke(atsc3Player.playbackState, atsc3Player.playbackPosition, atsc3Player.playbackSpeed)

            postDelayed(this, MEDIA_TIME_UPDATE_DELAY)
        }
    }

    private fun startMediaTimeUpdate() {
        removeCallbacks(updateMediaTimeRunnable)
        postDelayed(updateMediaTimeRunnable, MEDIA_TIME_UPDATE_DELAY)
    }

    private fun cancelMediaTimeUpdate() {
        removeCallbacks(updateMediaTimeRunnable)
    }

    companion object {
        val TAG: String = ReceiverPlayerView::class.java.simpleName

        private const val MEDIA_TIME_UPDATE_DELAY = 500L
    }
}