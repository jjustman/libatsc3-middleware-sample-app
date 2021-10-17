package com.nextgenbroadcast.mobile.middleware.controller.view

import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.core.model.ApplicationState
import com.nextgenbroadcast.mobile.core.model.RPMParams
import com.nextgenbroadcast.mobile.middleware.analytics.IAtsc3Analytics
import com.nextgenbroadcast.mobile.middleware.repository.IRepository
import kotlinx.coroutines.flow.*

internal class ViewControllerImpl(
    private val repository: IRepository,
    private val analytics: IAtsc3Analytics
) : IViewController {

    override val appState = MutableStateFlow(ApplicationState.UNAVAILABLE)

    override val rmpState = MutableStateFlow(PlaybackState.IDLE)
    override val rmpMediaTime = MutableStateFlow(0L)
    override val rmpPlaybackRate = MutableStateFlow(0f)

    override fun setApplicationState(state: ApplicationState) {
        appState.value = state
        reportApplicationState(state)
    }

    override fun requestPlayerLayout(scaleFactor: Double, xPos: Double, yPos: Double) {
        repository.setLayoutParams(RPMParams(scaleFactor, xPos.toInt(), yPos.toInt()))
    }

    override fun requestPlayerState(state: PlaybackState) {
        repository.setRequestedMediaState(state)
    }

    override fun requestPlayerState(state: PlaybackState, externalMediaUrl: String?) {
        repository.setExternalMediaUrl(externalMediaUrl)
        repository.setRequestedMediaState(state)
    }

    override fun rmpPlaybackChanged(state: PlaybackState) {
        rmpState.value = state
        reportPlaybackState(state)
    }

    override fun rmpPlaybackRateChanged(speed: Float) {
        rmpPlaybackRate.value = speed
    }

    override fun rmpMediaTimeChanged(currentTime: Long) {
        rmpMediaTime.value = currentTime
    }

    //TODO: Analytics should listen to ViewController and not vice versa
    private fun reportPlaybackState(state: PlaybackState) {
        when (state) {
            PlaybackState.PLAYING -> {
                analytics.startDisplayMediaContent()
            }
            PlaybackState.PAUSED,
            PlaybackState.IDLE -> {
                analytics.finishDisplayMediaContent()
            }
        }
    }

    //TODO: Analytics should listen to ViewController and not vice versa
    private fun reportApplicationState(state: ApplicationState) {
        when (state) {
            ApplicationState.OPENED -> analytics.startApplicationSession()
            ApplicationState.LOADED,
            ApplicationState.UNAVAILABLE -> analytics.finishApplicationSession()
            else -> {
                // ignore
            }
        }
    }
}