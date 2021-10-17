package com.nextgenbroadcast.mobile.middleware.scoreboard

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.*
import com.nextgenbroadcast.mobile.middleware.scoreboard.ScoreboardFragment.DeviceListAdapter.ISelectChartListener
import com.nextgenbroadcast.mobile.middleware.scoreboard.databinding.FragmentScoreboardBinding
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.ChartGeneralInfo
import com.nextgenbroadcast.mobile.middleware.scoreboard.view.DeviceItemView

class ScoreboardFragment : Fragment() {
    private val sharedViewModel by activityViewModels<SharedViewModel>()

    private lateinit var deviceAdapter: DeviceListAdapter
    private lateinit var binding: FragmentScoreboardBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentScoreboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        deviceAdapter = DeviceListAdapter(layoutInflater, selectChartListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.chartList.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        binding.chartList.adapter = deviceAdapter
        binding.chartList.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        sharedViewModel.chartDeviceInfoWithFlowList.observe(viewLifecycleOwner) { devices ->
            deviceAdapter.submitList(devices ?: emptyList())
        }

        sharedViewModel.selectedDeviceId.observe(viewLifecycleOwner) { deviceId ->
            deviceAdapter.updateChartSelection(deviceId)
        }
    }

    private val selectChartListener = object : ISelectChartListener {
        override fun selectChart(chartId: String?) {
            sharedViewModel.selectedDeviceId.value = chartId
        }

        override fun showErrorList(deviceId: String) {
            ConsoleActivity.startForDeviceFlow(requireContext(), ConsoleActivity.FlowType.ERROR, deviceId)
        }
    }

    class DeviceListAdapter(
        private val inflater: LayoutInflater,
        private val selectChartListener: ISelectChartListener
    ) : ListAdapter<ChartGeneralInfo, DeviceListAdapter.Holder>(DIFF_CALLBACK) {

        private var selectedChartId: String? = null

        fun updateChartSelection(chartId: String?) {
            selectedChartId = chartId
            notifyItemRangeChanged(0, itemCount, Payload.UpdateBackground)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = inflater.inflate(R.layout.chart_item_view, parent, false) as DeviceItemView
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(getItem(position))
        }

        override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
            (payloads.firstOrNull() as? List<Payload>)?.let { payloads ->
                payloads.forEach { payload ->
                    when (payload) {
                        is Payload.DistanceChanged -> holder.updateLocationLabel(payload.newModel)
                        is Payload.UpdateError -> holder.updateErrorText(payload.newModel)
                        Payload.UpdateBackground -> holder.updateBackground(getItem(position))
                    }
                }
            } ?: super.onBindViewHolder(holder, position, payloads)
        }

        inner class Holder(
            private val deviceView: DeviceItemView
        ) : RecyclerView.ViewHolder(deviceView) {

            fun bind(chartGeneralInfo: ChartGeneralInfo) {
                with(deviceView) {
                    chartGeneralInfo.chartData?.let { data ->
                        observe(data)
                    }

                    updateLocationLabel(chartGeneralInfo)
                    updateBackground(chartGeneralInfo)
                    updateErrorText(chartGeneralInfo)

                    lostLabel.isVisible = chartGeneralInfo.isLost

                    deviceView.setOnClickListener {
                        val selectedChartId =
                            if (selectedChartId == chartGeneralInfo.deviceId) null else chartGeneralInfo.deviceId
                        selectChartListener.selectChart(selectedChartId)
                    }
                }
            }

            fun updateErrorText(deviceInfo: ChartGeneralInfo) = with(deviceView) {
                val errorMessage = deviceInfo.errorData?.message
                if (errorMessage.isNullOrBlank()) {
                    errorText.isVisible = false
                } else {
                    errorText.isVisible = true
                    errorText.text = errorMessage
                    errorText.setOnClickListener {
                        selectChartListener.showErrorList(deviceInfo.deviceId)
                    }
                }
            }

            fun updateLocationLabel(model: ChartGeneralInfo) = with(deviceView) {
                title.text = formatDistanceAndIdSpannableString(
                    id = model.deviceId,
                    distance = model.distance,
                    context = context
                )
            }

            fun updateBackground(model: ChartGeneralInfo) = with(deviceView) {
                isChartSelected = selectedChartId.equals(model.deviceId)
                setBackgroundColor(
                    context.getColor(if (isChartSelected) R.color.yellow_device_item_bg else R.color.white)
                )
            }

        }

        sealed class Payload {
            data class DistanceChanged(val newModel: ChartGeneralInfo) : Payload()
            data class UpdateError(val newModel: ChartGeneralInfo) : Payload()
            object UpdateBackground : Payload()
        }

        interface ISelectChartListener {
            fun selectChart(chartId: String?)
            fun showErrorList(deviceId: String)
        }

        companion object {
            val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChartGeneralInfo>() {
                override fun areItemsTheSame(oldItem: ChartGeneralInfo, newItem: ChartGeneralInfo): Boolean {
                    return oldItem.deviceId == newItem.deviceId
                }

                override fun areContentsTheSame(oldItem: ChartGeneralInfo, newItem: ChartGeneralInfo): Boolean {
                    return oldItem.distance == newItem.distance
                            && oldItem.errorData == newItem.errorData
                            && oldItem.chartData?.primaryDataSources?.map { it.topic } == newItem.chartData?.primaryDataSources?.map { it.topic }
                            && oldItem.chartData?.secondaryDataSources?.map { it.topic } == newItem.chartData?.secondaryDataSources?.map { it.topic }
                }

                override fun getChangePayload(oldItem: ChartGeneralInfo, newItem: ChartGeneralInfo): Any? {
                    val distanceDiff = oldItem.distance != newItem.distance
                    val errorDiff = oldItem.errorData != newItem.errorData
                    return when {
                        distanceDiff && errorDiff -> listOf(
                            Payload.DistanceChanged(newItem),
                            Payload.UpdateError(newItem)
                        )
                        distanceDiff -> listOf(Payload.DistanceChanged(newItem))
                        errorDiff -> listOf(Payload.UpdateError(newItem))
                        else -> null
                    }
                }

            }
        }
    }

    companion object {
        val TAG: String = ScoreboardFragment::class.java.simpleName
    }
}