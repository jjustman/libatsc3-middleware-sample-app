package com.nextgenbroadcast.mobile.middleware.serviceController

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nextgenbroadcast.mobile.core.model.MediaUrl
import com.nextgenbroadcast.mobile.core.model.AVService
import com.nextgenbroadcast.mobile.core.model.PhyFrequency
import com.nextgenbroadcast.mobile.core.model.ReceiverState
import com.nextgenbroadcast.mobile.middleware.analytics.IAtsc3Analytics
import com.nextgenbroadcast.mobile.middleware.atsc3.Atsc3ModuleState
import com.nextgenbroadcast.mobile.middleware.atsc3.IAtsc3Module
import com.nextgenbroadcast.mobile.core.atsc3.SLTConstants
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.alerts.AeaTable
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.app.Atsc3Application
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.held.Atsc3HeldPackage
import com.nextgenbroadcast.mobile.middleware.atsc3.entities.service.Atsc3Service
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.IServiceGuideDeliveryUnitReader
import com.nextgenbroadcast.mobile.middleware.atsc3.serviceGuide.SGUrl
import com.nextgenbroadcast.mobile.middleware.atsc3.source.*
import com.nextgenbroadcast.mobile.middleware.controller.service.IServiceController
import com.nextgenbroadcast.mobile.middleware.controller.service.ServiceControllerImpl
import com.nextgenbroadcast.mobile.middleware.repository.IRepository
import com.nextgenbroadcast.mobile.middleware.settings.IMiddlewareSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.*
import org.hamcrest.CoreMatchers
import org.junit.*
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner


@ExperimentalCoroutinesApi
@RunWith(PowerMockRunner::class)
@PrepareForTest(IServiceController::class, ServiceControllerImpl::class, IRepository::class, IMiddlewareSettings::class, IAtsc3Analytics::class, IAtsc3Module::class, IServiceGuideDeliveryUnitReader::class)
class ServiceControllerTest {
    private val testDispatcher = TestCoroutineDispatcher()
    private val testScope = TestCoroutineScope()

    @JvmField
    @Rule
    var rule: TestRule = InstantTaskExecutorRule()

    @Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var serviceController: ServiceControllerImpl

    @Mock
    private lateinit var repository: IRepository

    @Mock
    private lateinit var settings: IMiddlewareSettings

    @Mock
    private lateinit var atsc3Analytics: IAtsc3Analytics

    @Mock
    private lateinit var atsc3Module: IAtsc3Module

    @Mock
    private lateinit var serviceGuideReader: IServiceGuideDeliveryUnitReader

    @Mock
    private lateinit var mockErrorFun: (message: String) -> Unit

    private val previousService = AVService(0, 0, "short_name", "globalServiceId", 1, 1, 0, false)
    private val selectedService = AVService(0, 1, "short_name", "globalServiceId", 1, 1, 0, false)
    private val nextService = AVService(0, 2, "short_name", "globalServiceId", 1, 1, 0, false)
    private val services = listOf(previousService, selectedService, nextService)

    private var selectedServiceLV = MutableStateFlow<AVService?>(null)
    private var sltServicesLV = MutableStateFlow<List<AVService>>(emptyList())

    @Before
    fun initController() {
        `when`(repository.selectedService).thenReturn(selectedServiceLV)
        `when`(repository.services).thenReturn(sltServicesLV)

        serviceController = ServiceControllerImpl(repository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope, mockErrorFun)

        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun cleanUp() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun testServerControllerCreated() {
        Assert.assertNotNull(serviceController)
    }

    @Test
    fun testOnStateChangedSCANNING() = testScope.runBlockingTest {
        val state = Atsc3ModuleState.SCANNING
        val newState = ReceiverState.scanning(-1, -1)

        serviceController.onStateChanged(state)

        delay(1) // interrupt current execution to get a chance the ServiceController internals process change

        Assert.assertEquals(newState, serviceController.receiverState.value)
        verify(repository, never()).reset()
    }

    @Test
    fun testOnStateChangedOPENED() = testScope.runBlockingTest {
        val state = Atsc3ModuleState.TUNED
        val newState = ReceiverState.tuning(-1, -1)

        serviceController.onStateChanged(state)

        delay(1) // interrupt current execution to get a chance the ServiceController internals process change

        Assert.assertEquals(newState, serviceController.receiverState.value)
        verify(repository, never()).reset()
    }

    @Test
    fun testOnStateChangedSTOPPED() = testScope.runBlockingTest {
        val state = Atsc3ModuleState.STOPPED
        val newState = ReceiverState.tuning(-1, -1)

        serviceController.onStateChanged(state)

        delay(1) // interrupt current execution to get a chance the ServiceController internals process change

        Assert.assertEquals(newState, serviceController.receiverState.value)
        verify(repository, never()).reset()
    }

    @Test
    fun testOnStateChangedIDLE() = testScope.runBlockingTest {
        val state = Atsc3ModuleState.IDLE
        val newState = ReceiverState.idle()

        serviceController.onStateChanged(state)

        delay(1) // interrupt current execution to get a chance the ServiceController internals process change

        Assert.assertEquals(newState, serviceController.receiverState.value)
        verify(repository).reset()
    }

    @Test
    fun testOnApplicationPackageReceived() {
        val mockData = Atsc3Application("uid", "packageName", listOf(), "cachePath", mapOf())

        serviceController.onApplicationPackageReceived(mockData)

        verify(repository).addOrUpdateApplication(mockData)
    }

    @Test
    fun testOnServiceLocationTableChanged() {
        val services: List<Atsc3Service> = listOf(Atsc3Service(serviceCategory = SLTConstants.SERVICE_CATEGORY_ESG))
        val reportServerUrl: String? = null

        serviceController.onServiceLocationTableChanged(services, reportServerUrl)

        verify(atsc3Analytics).setReportServerUrl(reportServerUrl)
        verify(repository).setServices(emptyList())
        verify(atsc3Module).selectAdditionalService(services.first().serviceId)
    }

    @Test
    fun testOnServiceLocationTableChangedWithDefaultServiceCategory() {
        val services: List<Atsc3Service> = listOf(Atsc3Service())
        val reportServerUrl: String? = null

        serviceController.onServiceLocationTableChanged(services, reportServerUrl)

        verify(atsc3Analytics).setReportServerUrl(reportServerUrl)
        verify(repository).setServices(emptyList())
        verify(atsc3Module, never()).selectAdditionalService(services.first().serviceId)
    }

    @Test
    fun testOnServicePackageChanged() {
        val mockPkg = Atsc3HeldPackage()

        serviceController.onServicePackageChanged(mockPkg)

        verify(repository).setHeldPackage(mockPkg)
    }

    @Test
    fun testOnServiceMediaReady() {
        val mediaUrl = MediaUrl("path", 0, 0)
        serviceController.onServiceMediaReady(mediaUrl, 0L)

        verify(repository).setMediaUrl(mediaUrl)
    }

    @Test
    fun testOnServiceMediaReadyWithDelay() = testScope.runBlockingTest {
        val mediaUrl = MediaUrl("path", 0, 0)
        val delayMS = 1000L
        serviceController.onServiceMediaReady(mediaUrl, delayMS)
        delay(delayMS)
        verify(repository).setMediaUrl(mediaUrl)
    }

    @Test
    fun testOnServiceGuideUnitReceived() {
        serviceController.onServiceGuideUnitReceived("filePath", 1717)

        verify(serviceGuideReader).readDeliveryUnit("filePath", 1717)
    }

    @Test
    fun testOpenRouteByPcapDEMUXEDPathReturnFalse() {
        val path = "pcap.demux."
        val type = PcapAtsc3Source.PcapType.DEMUXED
        val sourceMock = PcapFileAtsc3Source(path, type)

        `when`(atsc3Module.open(sourceMock)).thenReturn(false)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertFalse(atsc3Module.open(sourceMock))

        Assert.assertFalse(result)
    }

    @Test
    fun testOpenRouteByPcapDEMUXEDPathReturnTrue() {
        val path = "pcap.demux."
        val type = PcapAtsc3Source.PcapType.DEMUXED
        val sourceMock = PcapFileAtsc3Source(path, type)

        `when`(atsc3Module.open(sourceMock)).thenReturn(true)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertTrue(atsc3Module.open(sourceMock))

        Assert.assertTrue(result)
    }

    @Test
    fun testOpenRouteByPcapSTLTPPathReturnFalse() {
        val path = "pcapSTLTP"
        val type = PcapAtsc3Source.PcapType.STLTP
        val sourceMock = PcapFileAtsc3Source(path, type)

        `when`(atsc3Module.open(sourceMock)).thenReturn(false)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertFalse(atsc3Module.open(sourceMock))

        Assert.assertFalse(result)
    }

    @Test
    fun testOpenRouteByPcapSTLTPPathReturnTrue() {
        val path = "pcapSTLTP."
        val type = PcapAtsc3Source.PcapType.STLTP
        val sourceMock = PcapFileAtsc3Source(path, type)

        `when`(atsc3Module.open(sourceMock)).thenReturn(true)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertTrue(atsc3Module.open(sourceMock))

        Assert.assertTrue(result)
    }

    @Test
    fun testOpenRouteBySrtListPathReturnFalse() {
        val path = "srt://path\n"
        val sources = path.split('\n')
        val sourceMock = SrtListAtsc3Source(sources)

        `when`(atsc3Module.open(sourceMock)).thenReturn(false)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertFalse(atsc3Module.open(sourceMock))

        Assert.assertFalse(result)
    }

    @Test
    fun testOpenRouteBySrtListPathReturnTrue() {
        val path = "srt://path\n"
        val sources = path.split('\n')
        val sourceMock = SrtListAtsc3Source(sources)

        `when`(atsc3Module.open(sourceMock)).thenReturn(true)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertTrue(atsc3Module.open(sourceMock))

        Assert.assertTrue(result)
    }

    @Test
    fun testOpenRouteBySrtPathReturnFalse() {
        val path = "srt://path"
        val sourceMock = SrtAtsc3Source(path)

        `when`(atsc3Module.open(sourceMock)).thenReturn(false)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertFalse(atsc3Module.open(sourceMock))

        Assert.assertFalse(result)
    }

    @Test
    fun testOpenRouteBySrtPathReturnTrue() {
        val path = "srt://path"
        val sourceMock = SrtAtsc3Source(path)

        `when`(atsc3Module.open(sourceMock)).thenReturn(true)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertTrue(atsc3Module.open(sourceMock))

        Assert.assertTrue(result)
    }

    @Test
    fun testOpenRouteBySourceNotConnectedReturnFalse() {
        val sourceMock = mock(IAtsc3Source::class.java)
        `when`(atsc3Module.open(sourceMock)).thenReturn(false)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertFalse(atsc3Module.open(sourceMock))

        Assert.assertFalse(result)
    }

    @Test
    fun testOpenRouteBySourceConnectedWithITunableSourceReturnTrue() {
        val sourceMock = mock(MockTunableAtsc3Source::class.java)
        `when`(atsc3Module.open(sourceMock)).thenReturn(true)

        val result = serviceController.openRoute(sourceMock)

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertTrue(atsc3Module.open(sourceMock))
        Assert.assertTrue(sourceMock is ITunableSource)

        Assert.assertTrue(result)
    }

    @Test
    fun testOpenRouteBySourceConnectedWithoutITunableSourceReturnTrue() {
        val sourceMock = mock(MockAtsc3Source::class.java)
        `when`(atsc3Module.open(sourceMock)).thenReturn(true)

        val result = serviceController.openRoute(sourceMock)

        // Check that closeRoute functionality invoke
        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()

        verify(atsc3Module).open(sourceMock)
        Assert.assertTrue(atsc3Module.open(sourceMock))
        Assert.assertFalse(sourceMock is ITunableSource)

        Assert.assertTrue(result)
    }

    @Test
    fun testStopRoute() {
        serviceController.stopRoute()

        verify(atsc3Module).stop()
    }

    @Test
    fun testCloseRoute() {
        serviceController.closeRoute()

        verify(atsc3Analytics).finishSession()
        verify(atsc3Module).close()
        verify(serviceGuideReader).clearAll()
        verify(repository).reset()
    }

    @Test
    fun testSelectServiceAlreadySelectedReturnTrue() = testScope.runBlockingTest {
        val mockRepository = MockRepository()
        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)

        mockRepository.selectedService.value = selectedService

        Assert.assertTrue(serviceController.selectService(selectedService))
    }

    @Test
    fun testSelectServiceReturnFalse() = testScope.runBlockingTest {
        val mockRepository = spy(MockRepository())
        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)

        `when`(atsc3Module.selectService(selectedService.bsid, selectedService.id)).thenReturn(false)

        val result = serviceController.selectService(selectedService)

        verify(mockRepository).setMediaUrl(null)
        verify(atsc3Module).selectService(selectedService.bsid, selectedService.id)
        verify(mockRepository).setHeldPackage(null)
        verify(mockRepository).setSelectedService(null)
        Assert.assertFalse(result)
    }

    @Test
    fun testSelectServiceHeldPackageIsNullReturnTrue() = testScope.runBlockingTest {
        val mockRepository = spy(MockRepository())
        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)

        `when`(atsc3Module.selectService(selectedService.bsid, selectedService.id)).thenReturn(true)

        val result = serviceController.selectService(selectedService)

        verify(mockRepository).setMediaUrl(null)
        verify(atsc3Module).selectService(selectedService.bsid, selectedService.id)
        verify(atsc3Analytics).startSession(selectedService.bsid, selectedService.id, selectedService.globalId, selectedService.category)
        verify(mockRepository).setSelectedService(selectedService)
        Assert.assertTrue(result)
    }

    @Test
    fun testSelectServiceHeldPackageNotNullResetHeldWithoutDelayReturnTrue() = testScope.runBlockingTest {
        val mockRepository = spy(MockRepository())
        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)
        mockRepository.setHeldPackage(Atsc3HeldPackage())

        `when`(atsc3Module.selectService(selectedService.bsid, selectedService.id)).thenReturn(true)

        val result = serviceController.selectService(selectedService)

        verify(mockRepository).setMediaUrl(null)
        verify(atsc3Module).selectService(selectedService.bsid, selectedService.id)
        verify(atsc3Analytics).startSession(selectedService.bsid, selectedService.id, selectedService.globalId, selectedService.category)
        verify(mockRepository).setSelectedService(selectedService)
        verify(mockRepository).setHeldPackage(null)
        Assert.assertTrue(result)
    }

    @Test
    fun testSelectServiceHeldPackageNotNullResetHeldWithDelayReturnTrue() = testScope.runBlockingTest {
        val mockRepository = spy(MockRepository())
        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)
        mockRepository.setHeldPackage(Atsc3HeldPackage(coupledServices = listOf(123)))

        `when`(atsc3Module.selectService(selectedService.bsid, selectedService.id)).thenReturn(true)

        val result = serviceController.selectService(selectedService)

        verify(mockRepository).setMediaUrl(null)
        verify(atsc3Module).selectService(selectedService.bsid, selectedService.id)
        verify(atsc3Analytics).startSession(selectedService.bsid, selectedService.id, selectedService.globalId, selectedService.category)
        verify(mockRepository).setSelectedService(selectedService)

        delay(5000L)
        verify(mockRepository).setHeldPackage(null)

        Assert.assertTrue(result)
    }

    @Test
    fun testTune() {
        val frequency = PhyFrequency(listOf(1, 2, 3), PhyFrequency.Source.USER)
        val freqKhz = frequency.list.first()

        serviceController.tune(frequency)

        verify(settings).lastFrequency = freqKhz
        verify(atsc3Module).tune(
                freqKhz = freqKhz,
                frequencies = frequency.list,
                force = frequency.source == PhyFrequency.Source.USER
        )
    }

    @Test
    fun testFindServiceByIdReturnNull() {
        `when`(repository.findServiceBy("someIdForNull")).thenReturn(null)

        val result = serviceController.findServiceById("someIdForNull")

        verify(repository).findServiceBy("someIdForNull")
        Assert.assertNull(result)
    }

    @Test
    fun testFindServiceByIdReturnAVService() {
        `when`(repository.findServiceBy("someIdForAVService")).thenReturn(selectedService)

        val result = serviceController.findServiceById("someIdForAVService")

        verify(repository).findServiceBy("someIdForAVService")
        Assert.assertThat(result, CoreMatchers.instanceOf<AVService>(AVService::class.java))
    }

    @Test
    fun onErrorTest() {
        val errorMessage = "error"
        serviceController.onError(errorMessage)
        verify(mockErrorFun).invoke(errorMessage)
    }

    @Test
    fun testGetNearbyServiceReturnNext() = testScope.runBlockingTest {
        val offset = 1
        val selectedServiceIndex = services.indexOf(selectedService)

        selectedServiceLV.value = selectedService
        sltServicesLV.value = services

        val mockRepository = spy(MockRepository())
        `when`(mockRepository.selectedService).thenReturn(selectedServiceLV)

        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)

        val mock = spy(serviceController)
        `when`(mock.routeServices).thenReturn(sltServicesLV.asStateFlow())

        val result = mock.getNearbyService(offset)

        Assert.assertEquals(services[selectedServiceIndex + offset], result)
    }

    @Test
    fun testGetNearbyServiceReturnPrevious() = testScope.runBlockingTest {
        val offset = -1
        val selectedServiceIndex = services.indexOf(selectedService)

        selectedServiceLV.value = selectedService
        sltServicesLV.value = services

        val mockRepository = spy(MockRepository())
        `when`(mockRepository.selectedService).thenReturn(selectedServiceLV)

        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)

        val mock = spy(serviceController)
        `when`(mock.routeServices).thenReturn(sltServicesLV.asStateFlow())

        val result = mock.getNearbyService(offset)

        Assert.assertEquals(services[selectedServiceIndex + offset], result)
    }

    @Test
    fun testGetNearbyServiceReturnNull() = testScope.runBlockingTest {
        val offset = 1

        selectedServiceLV.value = null
        sltServicesLV.value = emptyList()

        val mockRepository = spy(MockRepository())
        `when`(mockRepository.selectedService).thenReturn(selectedServiceLV)

        serviceController = ServiceControllerImpl(mockRepository, settings, atsc3Module, atsc3Analytics, serviceGuideReader, testScope)

        val mock = spy(serviceController)
        `when`(mock.routeServices).thenReturn(sltServicesLV.asStateFlow())

        val result = mock.getNearbyService(offset)

        Assert.assertEquals(null, result)
    }

    abstract class MockTunableAtsc3Source : IAtsc3Source, ITunableSource
    abstract class MockAtsc3Source : IAtsc3Source

    open class MockRepository : IRepository {
        override val selectedService = MutableStateFlow<AVService?>(null)
        override val serviceGuideUrls = MutableStateFlow<List<SGUrl>>(emptyList())

        override val routeMediaUrl = MutableStateFlow<MediaUrl?>(null)

        override val applications = MutableStateFlow<List<Atsc3Application>>(emptyList())
        override val services = MutableStateFlow<List<AVService>>(emptyList())
        override val heldPackage = MutableStateFlow<Atsc3HeldPackage?>(null)
        override val alertsForNotify = MutableStateFlow<List<AeaTable>>(emptyList())

        override fun addOrUpdateApplication(application: Atsc3Application) {
        }

        override fun findApplication(appContextId: String): Atsc3Application? {
            TODO("Not yet implemented")
        }

        override fun setServices(services: List<AVService>) {
        }

        override fun setSelectedService(service: AVService?) {
        }

        override fun findServiceBy(globalServiceId: String): AVService? {
            return AVService(0, 0, "short_name", globalServiceId, 1, 1, 0, false)
        }

        override fun findServiceBy(bsid: Int, serviceId: Int): AVService? {
            return AVService(bsid, serviceId, "short_name", "globalServiceId", 1, 1, 0, false)
        }

        override fun setMediaUrl(mediaUrl: MediaUrl?) {
        }

        override fun setHeldPackage(data: Atsc3HeldPackage?) {
            heldPackage.value = data
        }

        override fun reset() {
        }

        override fun setAlertList(newAlerts: List<AeaTable>) {
        }
    }
}