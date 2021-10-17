package com.nextgenbroadcast.mobile.middleware.sample

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.nextgenbroadcast.mobile.core.atsc3.PhyInfoConstants
import com.nextgenbroadcast.mobile.core.model.*
import com.nextgenbroadcast.mobile.middleware.dev.atsc3.PHYStatistics
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.observer.StaticTelemetryObserver
import com.nextgenbroadcast.mobile.middleware.sample.view.PhyChartView
import com.nextgenbroadcast.mobile.middleware.sample.core.SwipeGestureDetector
import com.nextgenbroadcast.mobile.middleware.sample.core.mapWith
import com.nextgenbroadcast.mobile.middleware.sample.databinding.FragmentMainBinding
import com.nextgenbroadcast.mobile.middleware.sample.lifecycle.ViewViewModel
import com.nextgenbroadcast.mobile.middleware.sample.adapter.ServiceAdapter
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.TelemetryClient
import com.nextgenbroadcast.mobile.middleware.sample.model.*
import com.nextgenbroadcast.mobile.middleware.sample.settings.SettingsDialog
import com.nextgenbroadcast.mobile.view.AboutDialog
import com.nextgenbroadcast.mobile.view.TrackSelectionDialog
import com.nextgenbroadcast.mobile.view.UserAgentView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.util.*
import kotlin.concurrent.fixedRateTimer

class MainFragment : Fragment() {
    private val viewViewModel: ViewViewModel by activityViewModels()

    private lateinit var serviceAdapter: ServiceAdapter
    private lateinit var sourceAdapter: ArrayAdapter<String>
    private lateinit var receiverContentResolver: ReceiverContentResolver
    private lateinit var telemetryClient: TelemetryClient
    private lateinit var binding: FragmentMainBinding

    private var servicesList: List<AVService>? = null
    private var currentAppData: AppData? = null
    private var isShowingTrackSelectionDialog = false

    private var phyLoggingJob: Timer? = null

    private val swipeGestureDetector: GestureDetector by lazy {
        GestureDetector(requireContext(), object : SwipeGestureDetector() {
            override fun onClose() {
                binding.userAgentWebView.actionExit()
            }

            override fun onOpen() {
                binding.userAgentWebView.actionEnter()
            }
        })
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            receiverContentResolver.openRoute(uri.toString())
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.userAgentWebView.checkContentVisible()) {
                    binding.userAgentWebView.actionExit()
                } else {
                    if (isEnabled) {
                        isEnabled = false
                        requireActivity().onBackPressed()
                    }
                }
            }
        })

        receiverContentResolver = ReceiverContentResolver(context)

        telemetryClient = TelemetryClient(
            StaticTelemetryObserver(),
            //WebTelemetryObserver("localhost", 8081, listOf("phy")),
            300
        )

        setFragmentResultListener(SettingsDialog.REQUEST_KEY_FREQUENCY) { _, bundle ->
            val freqKhz = bundle.getInt(SettingsDialog.PARAM_FREQUENCY, 0)
            receiverContentResolver.tune(freqKhz)
        }

        setFragmentResultListener(SettingsDialog.REQUEST_KEY_SCAN_RANGE) { _, bundle ->
            bundle.getIntegerArrayList(SettingsDialog.PARAM_FREQUENCY)?.let { freqKhzList ->
                receiverContentResolver.tune(freqKhzList)
            }
        }

        serviceAdapter = ServiceAdapter(requireContext())
        sourceAdapter = ArrayAdapter<String>(requireContext(), R.layout.service_list_item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = viewViewModel
        }

        ReceiverContentResolver.resetPlayerState(requireContext())

        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.serviceList.adapter = sourceAdapter

        binding.userAgentWebView.setOnTouchListener { _, motionEvent -> swipeGestureDetector.onTouchEvent(motionEvent) }
        binding.userAgentWebView.setListener(object : UserAgentView.IListener {
            override fun onOpened() {
                receiverContentResolver.publishApplicationState(ApplicationState.OPENED)
            }

            override fun onClosed() {
                receiverContentResolver.publishApplicationState(ApplicationState.LOADED)
            }

            override fun onLoadingError() {
                onBALoadingError()
            }

            override fun onLoadingSuccess() {
                receiverContentResolver.publishApplicationState(ApplicationState.LOADED)
            }
        })
        binding.userAgentWebView.captureContentVisibility = true
        binding.userAgentWebView.isContentVisible.observe(viewLifecycleOwner) { isBAContentVisible ->
            if (isBAContentVisible) {
                binding.bottomSheetTitle.alpha = 0.2f
                binding.bottomSheetTitle.isClickable = false
                binding.atsc3DataLog.alpha = 0.2f
            } else {
                binding.bottomSheetTitle.alpha = 1f
                binding.bottomSheetTitle.isClickable = true
                binding.atsc3DataLog.alpha = 1f
            }
        }

        val bottomSheetBehavior = BottomSheetBehavior.from<View>(binding.bottomSheet).apply {
            isHideable = false
            state = BottomSheetBehavior.STATE_COLLAPSED
        }

        binding.serviceList.setOnItemClickListener { _, _, position, _ ->
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

            servicesList?.getOrNull(position)?.let { service ->
                selectService(service)
            } ?: if (position == 0) {
                showFileChooser()
            } else {
                viewViewModel.sources.value?.getOrNull(position - 1)?.let { (_, path) ->
                    receiverContentResolver.openRoute(path)
                }
            }
        }

        binding.bottomSheetTitle.setOnClickListener {
            when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.STATE_EXPANDED -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                else -> {
                    // do nothing
                }
            }
        }

        binding.cancelScanBtn.setOnClickListener {
            receiverContentResolver.tune(-1 /* cancel tuning */)
        }

        binding.settingsButton.setOnClickListener {
            showPopupSettingsMenu(binding.settingsButton)
        }

        binding.phyChart.setOnLongClickListener {
            binding.phyChart.takeSnapshotAndShare(requireContext(), "phyChart", getString(R.string.chart_phy_share_title))
            true
        }
        binding.phyChart.setDataSource(newChartDataSource())

        binding.receiverPlayer.setOnPlaybackChangeListener { state, position, rate ->
            receiverContentResolver.publishPlayerState(state, position, rate)
        }

        observeLocalData()
    }

    override fun onStart() {
        super.onStart()

        receiverContentResolver.register()
        subscribeReceiverData()

        if (viewViewModel.showPhyChart.value == true) {
            telemetryClient.start(false)
        }
    }

    override fun onStop() {
        super.onStop()

        receiverContentResolver.unregister()

        binding.receiverPlayer.stop()
        telemetryClient.stop()
    }

    override fun onDestroy() {
        super.onDestroy()

        phyLoggingJob?.cancel()
        phyLoggingJob = null
    }

    private fun subscribeReceiverData() {
        receiverContentResolver.observeApplicationData { appData ->
            switchApplication(appData)
            viewViewModel.appData.value = appData
        }

        receiverContentResolver.observeReceiverState { state ->
            viewViewModel.receiverState.value = state

            when (state.state) {
                ReceiverState.State.IDLE -> {
                    // exit PIP mode when Receiver is de-initialized
                    val activity = requireActivity()
                    if (activity.isInPictureInPictureMode) {
                        startActivity(Intent(activity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        })
                    }
                }

                ReceiverState.State.READY -> {
                    // Automatically start playing the first service in list when Receiver became ready
                    with(viewViewModel.defaultService) {
                        removeObservers(this@MainFragment)
                        observe(this@MainFragment) { defaultService ->
                            if (defaultService != null) {
                                removeObservers(this@MainFragment)
                                selectService(defaultService)
                            }
                        }
                    }
                }

                else -> {
                }
            }
        }

        receiverContentResolver.observePlayerState { mediaUri, params, state ->
            Log.d(TAG, "Receiver Media Player changed - uri: $mediaUri, params: $params, state: $state")

            viewViewModel.mediaUri.value = mediaUri

            updateRMPLayout(params.x, params.y, params.scale)

            if (mediaUri != null) {
                binding.receiverPlayer.play(mediaUri)
            } else {
                binding.receiverPlayer.stopAndClear()
            }

            when (state) {
                PlaybackState.PAUSED -> binding.receiverPlayer.pause()
                PlaybackState.PLAYING -> binding.receiverPlayer.tryReplay()
                PlaybackState.IDLE -> binding.receiverPlayer.stop()
            }
        }
    }

    private fun observeLocalData() {
        viewViewModel.showPhyInfo.mapWith(viewViewModel.showDebugInfo) { (showPhy, showInfo) ->
            (showInfo ?: true) && (showPhy ?: false)
        }.observe(viewLifecycleOwner) { phyInfoEnabled ->
            if (phyInfoEnabled == true) {
                if (phyLoggingJob == null) {
                    phyLoggingJob = fixedRateTimer(period = PHY_INFO_UPDATE_INTERVAL_MS) {
                        //text.setText(Html.fromHtml("<font color='#ff0000'>text</font>"));
                        //no text highlight color for SFN timing error
                        // viewViewModel.debugData.postValue("${PHYStatistics.PHYRfStatistics}\n${PHYStatistics.PHYBWStatistics}\n\n${PHYStatistics.PHYL1dTimingStatistics}")
                        //double space hack...
                        viewViewModel.debugData.postValue(
                            with(PHYStatistics) {
                                Html.fromHtml("$PHYRfStatistics\n$PHYBWStatistics\n\n$PHYL1dTimingStatistics"
                                    .replace("\n", "<br>")
                                    .replace("  ", "&nbsp;&nbsp;"),
                                    Html.FROM_HTML_MODE_LEGACY
                                )
                            }
                        )
                    }
                }
            } else if (phyLoggingJob != null) {
                phyLoggingJob?.cancel()
                phyLoggingJob = null
            }
        }

        viewViewModel.showPhyChart.observe(viewLifecycleOwner) { phyChartEnabled ->
            if (phyChartEnabled == true) {
                if (!telemetryClient.isStarted()) {
                    binding.phyChart.setDataSource(newChartDataSource())
                    telemetryClient.start()
                }
            } else {
                if (telemetryClient.isStarted()) {
                    binding.phyChart.setDataSource(null)
                    telemetryClient.stop()
                }
            }
        }

        viewViewModel.sources.observe(viewLifecycleOwner) { sourceList ->
            val sources = sourceList.map { (title) -> title }.toMutableList().apply {
                add(0, "Select pcap file...")
            }
            sourceAdapter.clear()
            sourceAdapter.addAll(sources)
        }

        viewViewModel.services.observe(viewLifecycleOwner) { services ->
            updateServices(services)
        }
        viewViewModel.currentServiceTitle.observe(viewLifecycleOwner) { currentServiceTitle ->
            setSelectedService(currentServiceTitle)
        }
    }

    private fun showPopupSettingsMenu(v: View) {
        PopupMenu(context, v).apply {
            inflate(R.menu.settings_menu)
            if (binding.receiverPlayer.player == null) {
                menu.findItem(R.id.menu_select_tracks)?.isEnabled = false
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_settings -> {
                        openSettingsDialog(
                                receiverContentResolver.queryReceiverFrequency()
                        )
                        true
                    }
                    R.id.menu_select_tracks -> {
                        openSelectTracksDialog()
                        true
                    }
                    R.id.menu_dialog_about -> {
                        openInfoDialog()
                        true
                    }

                    else -> false
                }
            }
        }.show()
    }

    private fun openInfoDialog() {
        var sdkVersion: String? = null
        var firmwareVersion: String? = null
        var deviceType: String? = null
        var deviceId: String? = null
        receiverContentResolver.getPhyInfo()?.let { info ->
            sdkVersion = info[PhyInfoConstants.INFO_SDK_VERSION]
            firmwareVersion = info[PhyInfoConstants.INFO_FIRMWARE_VERSION]
            deviceType = info[PhyInfoConstants.INFO_PHY_TYPE]
            deviceId = info[PhyInfoConstants.INFO_DEVICE_ID]
        }
        val frequency = receiverContentResolver.queryReceiverFrequency()

        AboutDialog(sdkVersion, firmwareVersion, deviceType, frequency, deviceId)
            .show(parentFragmentManager, null)
    }

    private fun openSelectTracksDialog() {
        val trackSelection = binding.receiverPlayer.getTrackSelector()
        val currentTrackSelection = binding.receiverPlayer.player.currentTrackSelections
        if (!isShowingTrackSelectionDialog
                && trackSelection != null
                && TrackSelectionDialog.willHaveContent(trackSelection)) {
            isShowingTrackSelectionDialog = true
            val trackSelectionDialog = TrackSelectionDialog.createForTrackSelector(
                    getString(R.string.track_selection_title),
                    currentTrackSelection,
                    trackSelection
            ) { isShowingTrackSelectionDialog = false }
            trackSelectionDialog.show(parentFragmentManager, null)
        }
    }

    private fun updateServices(services: List<AVService>) {
        servicesList = services

        if (services.isNotEmpty()) {
            binding.serviceList.adapter = serviceAdapter
            serviceAdapter.setServices(services)
        } else {
            binding.serviceList.adapter = sourceAdapter
            setSelectedService(null)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        val visibility = if (isInPictureInPictureMode) {
            setBAAvailability(false)
            View.INVISIBLE
        } else {
            if (isBAvailable()) {
                setBAAvailability(true)
                receiverContentResolver.queryPlayerData()?.let { (_, params) ->
                    updateRMPLayout(params.x, params.y, params.scale)
                }
            }
            View.VISIBLE
        }
        binding.atsc3DataLog.visibility = visibility
        binding.bottomSheet.visibility = visibility
    }

    private fun selectService(service: AVService) {
        receiverContentResolver.selectService(service)
    }

    private fun setSelectedService(serviceName: String?) {
        binding.bottomSheetTitle.text = serviceName ?: getString(R.string.no_service_available)
    }

    private fun openSettingsDialog(freqKhz: Int?) {
        SettingsDialog.newInstance(freqKhz)
                .show(parentFragmentManager, SettingsDialog.TAG)
    }

    private fun onBALoadingError() {
        currentAppData = null
        setBAAvailability(false)
        unloadBroadcasterApplication()

        Toast.makeText(requireContext(), getText(R.string.ba_loading_problem), Toast.LENGTH_SHORT).show()
    }

    private fun setBAAvailability(available: Boolean) {
        // GONE will prevent BA from sending resize requests when UA is not visible
        binding.userAgentWebView.isVisible = available
        if (!available) {
            updateRMPLayout(1f, 1f, 1f)
        }
    }

    private fun showFileChooser() {
        getContent.launch("*/*")
    }

    private fun isBAvailable() = currentAppData?.isAvailable ?: false

    private fun switchApplication(appData: AppData?) {
        if (appData != null && appData.isAvailable) {
            if (!requireActivity().isInPictureInPictureMode) {
                setBAAvailability(true)
            }
            val isAppEquals = appData.isAppEquals(currentAppData)
            if (!isAppEquals
                || appData.isBCastAvailable != currentAppData?.isBCastAvailable
                || appData.isBBandAvailable != currentAppData?.isBBandAvailable
            ) {
                loadBroadcasterApplication(appData, !isAppEquals)
            }
        } else {
            unloadBroadcasterApplication()
        }
    }

    private fun loadBroadcasterApplication(appData: AppData, forceReload: Boolean) {
        with(binding.userAgentWebView) {
            if (forceReload || !isContentLoaded) {
                currentAppData = appData
                serverCertificateHash = receiverContentResolver.queryServerCertificate() ?: emptyList()
                loadFirstAvailable(
                    mutableListOf<String>().apply {
                        if (appData.isBCastAvailable) {
                            appData.bCastEntryPageUrlFull?.let { add(it) }
                        }
                        if (appData.isBBandAvailable) {
                            appData.bBandEntryPageUrl?.let { add(it) }
                        }
                    }
                )
            }
        }
    }

    private fun unloadBroadcasterApplication() {
        currentAppData = null
        binding.userAgentWebView.unload()
        receiverContentResolver.publishApplicationState(ApplicationState.UNAVAILABLE)
    }

    private fun updateRMPLayout(x: Int, y: Int, scale: Double) {
        updateRMPLayout(x.toFloat() / 100, y.toFloat() / 100, scale.toFloat() / 100)
    }

    private fun updateRMPLayout(x: Float, y: Float, scale: Float) {
        ConstraintSet().apply {
            clone(binding.userAgentRoot)
            setHorizontalBias(R.id.receiver_player, if (scale == 1f) 0f else x / (1f - scale))
            setVerticalBias(R.id.receiver_player, if (scale == 1f) 0f else y / (1f - scale))
            constrainPercentHeight(R.id.receiver_player, scale)
            constrainPercentWidth(R.id.receiver_player, scale)
        }.applyTo(binding.userAgentRoot)
    }

    private fun newChartDataSource() = PhyChartView.DataSource(
        telemetryClient.getPayloadFlow<Int>().filterNotNull().map {
            it.toDouble() / 1000
        }
//        telemetryClient.getPayloadFlow<PhyChart.PhyPayload>().filterNotNull().map {
//            it.snr1000.toDouble() / 1000
//        }
    )

    companion object {
        val TAG: String = MainFragment::class.java.simpleName

        private const val PHY_INFO_UPDATE_INTERVAL_MS = 250L

        fun newInstance(): MainFragment {
            return MainFragment()
        }
    }
}