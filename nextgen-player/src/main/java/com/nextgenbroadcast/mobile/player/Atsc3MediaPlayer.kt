package com.nextgenbroadcast.mobile.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.upstream.ContentDataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy
import com.google.android.exoplayer2.util.Util
import com.nextgenbroadcast.mmt.exoplayer2.ext.MMTLoadControl
import com.nextgenbroadcast.mmt.exoplayer2.ext.MMTMediaSource
import com.nextgenbroadcast.mmt.exoplayer2.ext.MMTRenderersFactory
import com.nextgenbroadcast.mobile.core.exception.ServiceNotFoundException
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.player.exoplayer.Atsc3MMTExtractor
import com.nextgenbroadcast.mobile.player.exoplayer.RouteDASHLoadControl
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class Atsc3MediaPlayer(
        private val context: Context
): AudioManager.OnAudioFocusChangeListener {

    interface EventListener {
        fun onPlayerStateChanged(state: PlaybackState) {}
        fun onPlayerError(error: Exception) {}
        fun onPlaybackSpeedChanged(speed: Float) {}
    }

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var audioFocusRequest: AudioFocusRequest? = null
    private var listener: EventListener? = null
    private var _player: SimpleExoPlayer? = null
    private var _trackSelector: DefaultTrackSelector? = null
    private var isMMTPlayback = false
    private var rmpState: PlaybackState? = null
    private var resetPlayerJob: Job? = null

    var lastMediaUri: Uri? = null
        private set
    var lastMimeType: String? = null
        private set

    val player: Player?
        get() = _player
    val trackSelector: DefaultTrackSelector?
        get() = _trackSelector

    var resetWhenLostAudioFocus: Boolean = true

    private var playWhenReady: Boolean = true
        set(value) {
            _player?.playWhenReady = value
            field = value
        }

    val isPlaying: Boolean
        get() = _player?.isPlaying ?: false

    val isPaused: Boolean
        get() = !playWhenReady

    val playbackState: PlaybackState
        get() = _player?.let {
            playbackState(it.playbackState, it.playWhenReady)
        } ?: PlaybackState.IDLE

    val playbackPosition
        get() = _player?.currentPosition ?: 0

    val playbackSpeed: Float
        get() = _player?.playbackParameters?.speed ?: 0f

    val isInitialized: Boolean
        get() = playbackState != PlaybackState.IDLE

    fun setListener(listener: EventListener) {
        this.listener = listener
    }

    fun play(mediaUri: Uri, requestAudioFocus: Boolean = true) {
        play(mediaUri, context.contentResolver.getType(mediaUri), requestAudioFocus)
    }

    fun play(mediaUri: Uri, mimeType: String? = null, requestAudioFocus: Boolean = true) {
        Log.i(TAG, "play: with mediaUri: $mediaUri and mimeType: $mimeType, requestAudioFocus: $requestAudioFocus")

        reset()

        lastMediaUri = mediaUri
        lastMimeType = mimeType

        val selector = DefaultTrackSelector().also {
            _trackSelector = it
        }

        val mediaSource = createMediaSource(mediaUri, mimeType)

        _player =
            if (mimeType == MMTConstants.MIME_MMT_VIDEO || mimeType == MMTConstants.MIME_MMT_AUDIO) {
                isMMTPlayback = true
                createMMTExoPlayer(selector)
            } else {
                createDefaultExoPlayer(selector)
            }.apply {
                prepare(mediaSource)
                if (!requestAudioFocus || tryRetrievedAudioFocus()) {
                    playWhenReady = this@Atsc3MediaPlayer.playWhenReady
                }
            }
    }

    fun tryReplay(requestAudioFocus: Boolean = true) {
        Log.i(TAG, "replay: with requestAudioFocus: $requestAudioFocus")

        playWhenReady = true

        val mediaUri = lastMediaUri
        if (mediaUri == null || (requestAudioFocus && !tryRetrievedAudioFocus())) {
            return
        }

        cancelDelayedPlayerReset()

        val player = _player
        if (playbackState == PlaybackState.IDLE) {
            if (player != null) {
                player.prepare(createMediaSource(mediaUri, lastMimeType))
                player.playWhenReady = this@Atsc3MediaPlayer.playWhenReady
            } else {
                play(mediaUri, lastMimeType, false)
            }
        }
    }

    fun pause() {
        Log.i(TAG, "pause: with lastMediaUri: $lastMediaUri")

        playWhenReady = false
    }

    fun stop() {
        Log.i(TAG, "stop: with lastMediaUri: $lastMediaUri")

        _player?.stop(true)

        releaseAudioFocus()
    }

    fun reset() {
        cancelDelayedPlayerReset()

        _player?.let {
            it.stop()
            it.release()
            _player = null
            _trackSelector = null
        }
        isMMTPlayback = false

        releaseAudioFocus()
    }

    fun clearSavedState() {
        lastMediaUri = null
    }

    fun getSubtitleFormats(): List<Triple<Format, Boolean, Int>> {
        val rendererType = C.TRACK_TYPE_AUDIO

        return mutableListOf<Triple<Format, Boolean, Int>>().apply {
            trackSelector?.let { selector ->
                val parameters = selector.parameters
                selector.currentMappedTrackInfo?.let { trackInfo ->
                    for (rendererIndex in 0 until trackInfo.rendererCount) {
                        if (rendererType == trackInfo.getRendererType(rendererIndex)) {
                            val trackGroups = trackInfo.getTrackGroups(rendererIndex)
                            val override = parameters.getSelectionOverride(rendererIndex, trackGroups)
                            for (groupIndex in 0 until trackGroups.length) {
                                val group = trackGroups[groupIndex]
                                for (trackIndex in 0 until group.length) {
                                    val format = group.getFormat(trackIndex)
                                    val compositeIndex = ((rendererIndex and 0xff) shl 16) or ((groupIndex and 0xff) shl 8) or (trackIndex and 0xff)
                                    val isSelected = override != null && override.groupIndex == groupIndex && override.containsTrack(trackIndex)
                                    add(Triple(format, isSelected, compositeIndex))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectTrack(compositeIndex: Int) {
        val trackIndex = compositeIndex and 0xff
        val groupIndex = (compositeIndex shr 8) and 0xff
        val rendererIndex = (compositeIndex shr 16) and 0xff

        trackSelector?.let { selector ->
            val parameters = selector.parameters

            selector.currentMappedTrackInfo?.let { trackInfo ->
                val trackGroups = trackInfo.getTrackGroups(rendererIndex)
                //val override = parameters.getSelectionOverride(rendererIndex, trackGroups)

                val builder: DefaultTrackSelector.ParametersBuilder = parameters.buildUpon()
                builder.clearSelectionOverrides(rendererIndex)
                //TODO: override.tracks to add/replace tracks
                builder.setSelectionOverride(rendererIndex, trackGroups, SelectionOverride(groupIndex, trackIndex))

                selector.setParameters(builder)
            }
        }
    }

    private fun createMediaSource(mediaUri: Uri, mimeType: String?): MediaSource {
        return if (mimeType == MMTConstants.MIME_MMT_VIDEO || mimeType == MMTConstants.MIME_MMT_AUDIO) {
            MMTMediaSource.Factory({
                ContentDataSource(context)
            }, {
                arrayOf(Atsc3MMTExtractor())
            }).apply {
                setLoadErrorHandlingPolicy(createMMTLoadErrorHandlingPolicy())
            }.createMediaSource(mediaUri)
        } else {
            createMediaSourceFactory().createMediaSource(mediaUri)
        }
    }

    private fun createMediaSourceFactory(): DashMediaSource.Factory {
        val userAgent = Util.getUserAgent(context, AppUtils.getUserAgent(context))
        val manifestDataSourceFactory = DefaultDataSourceFactory(context, userAgent)
        val mediaDataSourceFactory = DefaultDataSourceFactory(context, userAgent)

        return DashMediaSource.Factory(
                DefaultDashChunkSource.Factory(mediaDataSourceFactory),
                manifestDataSourceFactory
        ).apply {
            setLoadErrorHandlingPolicy(createDASHLoadErrorHandlingPolicy())
        }
    }

    private fun createDefaultExoPlayer(trackSelector: TrackSelector): SimpleExoPlayer {
        //jjustman-2021-09-08 - TODO - refactor DefaultRenderersFactory out into RouteDASHRenderersFactory for AC-4 and MPEGH support
        return createExoPlayer(RouteDASHLoadControl(), DefaultRenderersFactory(context), trackSelector)
    }

    private fun createMMTExoPlayer(trackSelector: TrackSelector): SimpleExoPlayer {
        return createExoPlayer(MMTLoadControl(), MMTRenderersFactory(context), trackSelector)
    }

    private fun createExoPlayer(loadControl: LoadControl, renderersFactory: RenderersFactory, trackSelector: TrackSelector): SimpleExoPlayer {
        return ExoPlayerFactory.newSimpleInstance(context, renderersFactory, trackSelector, loadControl).apply {
            addListener(object : Player.EventListener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    val state = playbackState(playbackState, playWhenReady) ?: return
                    if (rmpState != state) {
                        rmpState = state
                        listener?.onPlayerStateChanged(state)
                    }
                }

                override fun onPlayerError(error: ExoPlaybackException) {
                    listener?.onPlayerError(error)

                    // Do not retry if source media service not found
                    if (isContentDataSourceServiceNotFoundException(error.cause)) {
                        return
                    }

                    if (isMMTPlayback) {
                        seekToDefaultPosition()
                    }

                    retry()
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    listener?.onPlaybackSpeedChanged(playbackParameters.speed)
                }
            })
        }
    }

    private fun createDASHLoadErrorHandlingPolicy(): DefaultLoadErrorHandlingPolicy {
        return object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(dataType: Int, loadDurationMs: Long, exception: IOException?, errorCount: Int): Long {
                Log.w("ExoPlayerCustomLoadErrorHandlingPolicy", "dataType: $dataType, loadDurationMs: $loadDurationMs, exception ex: $exception, errorCount: $errorCount")

                //jjustman-2019-11-07 - retry every 1s for exoplayer errors from ROUTE/DASH
                return 1000
            }

            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return 1
            }
        }
    }

    private fun createMMTLoadErrorHandlingPolicy(): DefaultLoadErrorHandlingPolicy {
        return object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(dataType: Int, loadDurationMs: Long, exception: IOException?, errorCount: Int): Long {
                Log.w("ExoPlayerMMTLoadErrorHandlingPolicy", "dataType: $dataType, loadDurationMs: $loadDurationMs, exception ex: $exception, errorCount: $errorCount")

                if (isContentDataSourceServiceNotFoundException(exception)) {
                    return C.TIME_UNSET
                }

                return super.getRetryDelayMsFor(dataType, loadDurationMs, exception, errorCount)
            }
        }
    }

    private fun isContentDataSourceServiceNotFoundException(exception: Throwable?): Boolean {
        if (exception is ContentDataSource.ContentDataSourceException) {
            if (exception.cause is ServiceNotFoundException) {
                return true
            }
        }

        return false
    }

    private fun playbackState(playbackState: Int, playWhenReady: Boolean): PlaybackState? {
        return when (playbackState) {
            Player.STATE_BUFFERING, Player.STATE_READY -> {
                if (playWhenReady) PlaybackState.PLAYING else PlaybackState.PAUSED
            }
            Player.STATE_IDLE, Player.STATE_ENDED -> {
                PlaybackState.IDLE
            }
            else -> null
        }
    }

    private fun releaseAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        audioFocusRequest = null
    }

    private fun tryRetrievedAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setOnAudioFocusChangeListener(this)
                .build().also {
                    audioFocusRequest = it
                }

        val result = audioManager.requestAudioFocus(request)
        return result == AudioManager.AUDIOFOCUS_GAIN
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                playWhenReady = false
                if (resetWhenLostAudioFocus) {
                    startDelayedPlayerReset()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                playWhenReady = false
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                playWhenReady = true
            }
        }
    }

    private fun startDelayedPlayerReset() {
        resetPlayerJob = CoroutineScope(Dispatchers.IO).launch {
            delay(PLAYER_RESET_DELAY)
            withContext(Dispatchers.Main) {
                reset()
                resetPlayerJob = null
            }
        }
    }

    private fun cancelDelayedPlayerReset() {
        resetPlayerJob?.let {
            it.cancel()
            resetPlayerJob = null
        }
    }

    companion object {
        val TAG: String = Atsc3MediaPlayer::class.java.simpleName

        private val PLAYER_RESET_DELAY = TimeUnit.SECONDS.toMillis(30)
    }
}