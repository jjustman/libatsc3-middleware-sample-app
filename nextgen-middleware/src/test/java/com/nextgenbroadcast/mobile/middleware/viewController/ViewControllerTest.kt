package com.nextgenbroadcast.mobile.middleware.viewController

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import com.nextgenbroadcast.mobile.core.model.MediaUrl
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.model.PlaybackState
import com.nextgenbroadcast.mobile.core.model.RPMParams
import com.nextgenbroadcast.mobile.core.model.ApplicationState
import com.nextgenbroadcast.mobile.middleware.analytics.IAtsc3Analytics
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3Application
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.Atsc3HeldPackage
import com.nextgenbroadcast.mobile.middleware.controller.view.ViewControllerImpl
import com.nextgenbroadcast.mobile.middleware.repository.IRepository
import com.nextgenbroadcast.mobile.middleware.service.provider.IMediaFileProvider
import com.nextgenbroadcast.mobile.middleware.settings.IClientSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@ExperimentalCoroutinesApi
@RunWith(PowerMockRunner::class)
@PrepareForTest(ViewControllerImpl::class, IRepository::class, IClientSettings::class, IMediaFileProvider::class, IAtsc3Analytics::class)
class ViewControllerTest {

    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope()

    private lateinit var viewController: ViewControllerImpl

    @JvmField
    @Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var repository: IRepository

    @Mock
    private lateinit var settings: IClientSettings

    @Mock
    private lateinit var mediaFileProvider: IMediaFileProvider

    @Mock
    private lateinit var analytics: IAtsc3Analytics

    private val scaleFactor: Double = 1.0
    private val xPos: Double = 11.0
    private val yPos: Double = 22.0
    private val mockUrl = "testMediaUrl"
    private val bsid = 1
    private val serviceId = 5003
    private val mockedAVService: AVService = AVService(bsid, serviceId, "WZTV", "tag:sinclairplatform.com,2020:WZTV:2727", 22, 1, 0, false)
    private val mockedMediaUrl = MediaUrl(mockUrl, bsid, serviceId)
    private var mockRouteMediaUrl: StateFlow<MediaUrl?> = MutableStateFlow(mockedMediaUrl)
    private val heldPackage: StateFlow<Atsc3HeldPackage?> = MutableStateFlow(null)
    private val applications: StateFlow<List<Atsc3Application>> = MutableStateFlow(emptyList())
    private var selectedService: StateFlow<AVService?> = MutableStateFlow(null)

    @Before
    fun initController() {
        Mockito.`when`(repository.heldPackage).thenReturn(heldPackage)
        Mockito.`when`(repository.applications).thenReturn(applications)
        Mockito.`when`(repository.selectedService).thenReturn(selectedService)

        Mockito.`when`(repository.routeMediaUrl).thenReturn(mockRouteMediaUrl)
        Mockito.`when`(repository.findServiceBy(bsid, serviceId)).thenReturn(mockedAVService)
        Mockito.`when`(mediaFileProvider.getMediaFileUri(mockUrl)).thenReturn(mockUrl.toUri())

        viewController = ViewControllerImpl(repository, analytics)

        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun cleanUp() {
        testDispatcher.cleanupTestCoroutines()
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun testViewControllerCreated() {
        Assert.assertNotNull(viewController)
    }

    @Test
    fun testSetApplicationState() = testScope.runBlockingTest {
        viewController.setApplicationState(ApplicationState.LOADED)

        Assert.assertEquals(ApplicationState.LOADED, viewController.appState.value)
    }

    @Test
    fun testRmpLayoutReset() = testScope.runBlockingTest {
        viewController.rmpLayoutReset()

        Assert.assertEquals(RPMParams(), viewController.rmpLayoutParams.value)
    }

    @Test
    fun testRmpPlaybackChangedToPlaying() = testScope.runBlockingTest {
        viewController.rmpPlaybackChanged(PlaybackState.PLAYING)

        Assert.assertEquals(PlaybackState.PLAYING, viewController.rmpState.value)

        verify(analytics).startDisplayMediaContent()
    }

    @Test
    fun testRmpPlaybackChangedToIdle() = testScope.runBlockingTest {
        viewController.rmpPlaybackChanged(PlaybackState.IDLE)

        Assert.assertEquals(PlaybackState.IDLE, viewController.rmpState.value)

        verify(analytics).finishDisplayMediaContent()
    }

    @Test
    fun testRmpPlaybackChangedToPaused() = testScope.runBlockingTest {
        viewController.rmpPlaybackChanged(PlaybackState.PAUSED)

        Assert.assertEquals(PlaybackState.PAUSED, viewController.rmpState.value)

        verify(analytics).finishDisplayMediaContent()
    }

    @Test
    fun testNotifyPause() {
        viewController.rmpPause()

        verify(callback).onPause(null)
    }

    @Test
    fun testNotifyResume() {
        viewController.rmpResume()

        verify(callback).onResume(null)
    }

    @Test
    fun testUpdateRMPPosition() {
        viewController.updateRMPPosition(scaleFactor, xPos, yPos)

        Assert.assertEquals(RPMParams(scaleFactor, xPos.toInt(), yPos.toInt()), viewController.rmpLayoutParams.value)
    }

    @Test
    fun testRmpPlaybackRateChanged() {
        val speed = 100f
        viewController.rmpPlaybackRateChanged(speed)

        Assert.assertEquals(speed, viewController.rmpPlaybackRate.value)
    }

    @Test
    fun testRmpMediaTimeChanged() = testScope.runBlockingTest {
        val currentTime = 1000L
        viewController.rmpMediaTimeChanged(currentTime)

        Assert.assertEquals(currentTime, viewController.rmpMediaTime.value)
    }

    @Test
    fun testRequestMediaPlayMediaUrlNotNull() {
        viewController.requestMediaPlay(mockUrl, 1000L)

        verify(callback).onPause(null)

        //TODO: Should I check rmpMediaUri here?

        verify(callback).onResume(null)
    }

    @Test
    fun testRequestMediaStop() {
        viewController.requestMediaStop(1000L)

        verify(callback).onPause(null)
    }
}
