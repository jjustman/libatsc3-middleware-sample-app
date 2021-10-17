package com.nextgenbroadcast.mobile.middleware.controller.view

import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.core.model.ApplicationState
import kotlinx.coroutines.flow.StateFlow

internal interface IViewController {
    val appState: StateFlow<ApplicationState>

    val rmpState: StateFlow<PlaybackState>
    val rmpMediaTime: StateFlow<Long>
    val rmpPlaybackRate: StateFlow<Float>

    fun setApplicationState(state: ApplicationState)

    fun rmpPlaybackChanged(state: PlaybackState)
    fun rmpPlaybackRateChanged(speed: Float)
    fun rmpMediaTimeChanged(currentTime: Long)

    fun requestPlayerLayout(scaleFactor: Double, xPos: Double, yPos: Double)
    fun requestPlayerState(state: PlaybackState)
    fun requestPlayerState(state: PlaybackState, externalMediaUrl: String?)
}