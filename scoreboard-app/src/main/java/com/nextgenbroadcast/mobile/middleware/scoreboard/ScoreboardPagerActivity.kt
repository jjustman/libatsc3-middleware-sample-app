package com.nextgenbroadcast.mobile.middleware.scoreboard

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.ClientTelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.entity.TelemetryEvent
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.BatteryData
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.GPSTelemetryReader
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.LocationData
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.RfPhyData
import com.nextgenbroadcast.mobile.middleware.scoreboard.databinding.ActivityScoreboardBinding
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.ChartConfiguration
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.ChartData
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.ChartDataSource
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.TDataPoint
import com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry.mapToDataPoint
import com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry.mapToEvent
import com.nextgenbroadcast.mobile.middleware.scoreboard.telemetry.sampleTelemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

class ScoreboardPagerActivity : FragmentActivity(), ServiceConnection {
    private val sharedViewModel: SharedViewModel by viewModels()

    private lateinit var binding: ActivityScoreboardBinding
    private lateinit var pagerAdapter: PagerAdapter

    private var serviceBinder: ScoreboardService.ScoreboardBinding? = null
    private var connectionJob: Job? = null
    private var gpsTelemetryJob: Job? = null
    private var gpsTelemetryReader: GPSTelemetryReader? = null
    private val currentDeviceLocation = MutableSharedFlow<TelemetryEvent>()

    private val scorebordPermissionResolver: ScorebordPermissionResolver by lazy {
        ScorebordPermissionResolver(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScoreboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pagerAdapter = PagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 2
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.viewPager.keepScreenOn = (Pages.getOrNull(position) == Pages.Charts)
            }
        })

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(tabLayout, binding.viewPager) { tab, position ->
            tab.text = getTabName(position)
        }.attach()

        gpsTelemetryReader = GPSTelemetryReader(this)

        lifecycleScope.launch {
            currentDeviceLocation.collect {
                sharedViewModel.currentDeviceLiveData.value = it.payload as LocationData
            }
        }

        sharedViewModel.devicesToAdd.observe(this) { devices ->
            val binder = serviceBinder ?: return@observe

            devices?.mapNotNull { deviceId ->
                binder.connectDevice(deviceId)?.let { flow ->
                    deviceId to ChartData(
                        primaryDataSources = flow.createPrimaryCartSourceList(),
                        secondaryDataSources = flow.createSecondaryCartSourceList(),
                        secondaryChartConfiguration = ChartConfiguration(
                            minValue = SNR_MIN_VALUE,
                            maxValue = SNR_MAX_VALUE
                        ),
                    )
                }
            }?.let {
                sharedViewModel.addOrReplaceChartData(it)
            }
        }

        sharedViewModel.devicesToRemove.observe(this) { devices ->
            val binder = serviceBinder ?: return@observe
            devices?.let { it ->
                it.forEach { deviceId ->
                    binder.disconnectDevice(deviceId)
                }
                sharedViewModel.removeFlows(it.toList())
            }
        }

        sharedViewModel.selectedDeviceId.observe(this@ScoreboardPagerActivity) { deviceId ->
            serviceBinder?.selectDevice(deviceId)
        }

        if (savedInstanceState == null) {
            binding.viewPager.setCurrentItem(1, false)
        }
    }

    override fun onStart() {
        super.onStart()
        if (scorebordPermissionResolver.checkSelfPermission()) {
            if (gpsTelemetryJob == null || gpsTelemetryJob?.isActive == false) {
                startGpsTelemetryReader()
            }
        }
        bindService()
    }

    override fun onStop() {
        super.onStop()

        unbindService()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = (service as? ScoreboardService.ScoreboardBinding).also {
            serviceBinder = it
        } ?: return

        with(binder) {
            sharedViewModel.setDevicesList(deviceIdList.value)

            binder.getConnectedDevices().forEach { deviceId ->
                sharedViewModel.addDeviceChart(deviceId)
            }

            connectionJob = lifecycleScope.launch {
                launch {
                    deviceIdList.collect { deviceList ->
                        sharedViewModel.setDevicesList(deviceList)
                    }
                }

                launch {
                    selectedDeviceId.collect { selectedDevice ->
                        sharedViewModel.setDeviceSelection(selectedDevice)
                    }
                }

                launch {
                    getGlobalLocationEventFlow()?.collect { (deviceId, event) ->
                        sharedViewModel.addDeviceLocation(deviceId, event)
                    }
                }

                launch {
                    getGlobalErrorFlow()?.collect{ (deviceId, event) ->
                        sharedViewModel.addDeviceError(deviceId, event)
                    }
                }
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.d(TAG, "onServiceDisconnected, $name")
        connectionJob?.cancel("onServiceDisconnected()")
        serviceBinder = null
        sharedViewModel.selectedDeviceId.value = null
        finish()
    }

    private fun bindService() {
        Intent(this, ScoreboardService::class.java).run {
            startForegroundService(this)
            bindService(this, this@ScoreboardPagerActivity, BIND_IMPORTANT)
        }
    }

    private fun unbindService() {
        connectionJob?.cancel("activity onStop()")
        unbindService(this)
        serviceBinder = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (scorebordPermissionResolver.processPermissionsResult(requestCode, permissions, grantResults)) {
            startGpsTelemetryReader()
        }
    }

    private fun startGpsTelemetryReader() {
        gpsTelemetryJob?.cancel()
        gpsTelemetryJob = lifecycleScope.launch(Dispatchers.IO) {
            gpsTelemetryReader?.read(currentDeviceLocation)
        }
    }

    private fun getTabName(position: Int): CharSequence = getString(
        when (Pages.getOrNull(position)) {
            Pages.Commands -> R.string.command_tab_title
            Pages.Devices -> R.string.devices_tab_title
            Pages.Charts -> R.string.chart_tab_title
            null -> throw IllegalArgumentException("Wrong Fragment index: $position")
        }
    )

    private fun Flow<ClientTelemetryEvent>.createPrimaryCartSourceList() = listOf(
        ChartDataSource(
            topic = "BAT_LEVEL",
            series = seriesConfigFactory(
                color = 0xFF00FF00.toInt(),
                title = "BAT"
            ),
            data = mapToTPoint<BatteryData>(
                filter = TelemetryEvent.EVENT_TOPIC_BATTERY,
                delay = TimeUnit.SECONDS.toMillis(70),
                replayLast = true
            ) {
                level.toDouble()
            }
        )
    )

    private fun Flow<ClientTelemetryEvent>.createSecondaryCartSourceList() = listOf(
        ChartDataSource(
            topic = "PHY_SNR",
            series = seriesConfigFactory(
                color = 0xFFFF0000.toInt(),
                title = "SNR"
            ),
            data = mapToTPoint<RfPhyData>(
                filter = TelemetryEvent.EVENT_TOPIC_PHY,
                delay = TimeUnit.SECONDS.toMillis(5),
                replayLast = false
            ) {
                stat.snr1000_global.toDouble() / 1000
            }
        )
    )

    private fun <T> Flow<ClientTelemetryEvent>.mapToTPoint(filter: String, delay: Long, replayLast: Boolean, selector: T.() -> Double): Flow<TDataPoint> =
        mapToEvent(filter)
            .mapToDataPoint(selector)
            .sampleTelemetry(lifecycleScope, TELEMETRY_SAMPLE_DELAY, delay, replayLast)
            .shareIn(lifecycleScope, SharingStarted.Lazily, TELEMETRY_REPLAY_COUNT)

    private enum class Pages {
        Commands, Devices, Charts;

        companion object {
            fun size() = values().size
            fun getOrNull(index: Int) = values().getOrNull(index)
        }
    }

    class PagerAdapter(
        activity: FragmentActivity
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = Pages.size()

        override fun createFragment(position: Int): Fragment = when (Pages.getOrNull(position)) {
            Pages.Commands -> CommandFragment()
            Pages.Devices -> ScoreboardSettingsFragment()
            Pages.Charts -> ScoreboardFragment()
            null -> throw IllegalArgumentException("Wrong Fragment index: $position")
        }
    }

    companion object {
        private val TAG = ScoreboardPagerActivity::class.java.simpleName

        private val TELEMETRY_SAMPLE_DELAY = TimeUnit.SECONDS.toMillis(1)
        private val TELEMETRY_REPLAY_COUNT = 100

        private const val SNR_MAX_VALUE = 30.0
        private const val SNR_MIN_VALUE = 0.0

        private fun seriesConfigFactory(color: Int, title: String) = LineGraphSeries<DataPoint>().apply {
            this.color = color
            this.title = title
        }
    }
}