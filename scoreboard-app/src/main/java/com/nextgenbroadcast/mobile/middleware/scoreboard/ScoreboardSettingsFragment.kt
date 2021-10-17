package com.nextgenbroadcast.mobile.middleware.scoreboard

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.ErrorData
import com.nextgenbroadcast.mobile.middleware.scoreboard.databinding.DeviceIdItemViewBinding
import com.nextgenbroadcast.mobile.middleware.scoreboard.databinding.FragmentSettingsBinding
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.DeviceScoreboardInfo

class ScoreboardSettingsFragment : Fragment() {
    private val sharedViewModel by activityViewModels<SharedViewModel>()

    private lateinit var deviceIdsAdapter: DeviceIdsAdapter
    private lateinit var binding: FragmentSettingsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        deviceIdsAdapter = DeviceIdsAdapter(layoutInflater, object : DeviceSelectListener {
            override fun addDeviceChart(deviceId: String) {
                sharedViewModel.addDeviceChart(deviceId)
            }

            override fun remoteDeviceChart(deviceId: String) {
                sharedViewModel.removeDeviceChart(deviceId)
            }

            override fun showErrorList(deviceId: String) {
                ConsoleActivity.startForDeviceFlow(requireContext(), ConsoleActivity.FlowType.ERROR, deviceId)
            }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.deviceIdsRecyclerIew.adapter = deviceIdsAdapter

        binding.selectAllCheckbox.setOnClickListener {
            sharedViewModel.selectAllDevices(binding.selectAllCheckbox.isChecked)
        }

        sharedViewModel.deviceInfoList.observe(viewLifecycleOwner) { devices ->
            deviceIdsAdapter.setData(devices)
        }

        sharedViewModel.selectedDeviceId.observe(viewLifecycleOwner) { deviceId ->
            deviceIdsAdapter.changeSelection(deviceId)
        }

        sharedViewModel.selectionState.observe(viewLifecycleOwner) { (isAnySelected, isAllSelected) ->
            with(binding.selectAllCheckbox) {
                isChecked = isAnySelected
                jumpDrawablesToCurrentState()
                alpha = if (isAllSelected || !isAnySelected) 1F else 0.3F
            }
        }
    }

    class DeviceIdsAdapter(
        private val inflater: LayoutInflater,
        private val deviceListener: DeviceSelectListener
    ) : RecyclerView.Adapter<DeviceIdsAdapter.DeviceIdViewHolder>() {
        private val deviceIdList = mutableListOf<DeviceScoreboardInfo>()
        private var selectedChartId: String? = null

        fun setData(deviceIdsList: List<DeviceScoreboardInfo>) {
            deviceIdList.clear()
            deviceIdList.addAll(deviceIdsList)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceIdViewHolder {
            return DeviceIdViewHolder(DeviceIdItemViewBinding.inflate(inflater, parent, false)
            )
        }

        override fun onBindViewHolder(holder: DeviceIdViewHolder, position: Int) {
            val (device, checked, distance, error) = deviceIdList.getOrNull(position) ?: return
            val context = holder.itemView.context
            with(holder) {
                this.deviceId = device.id

                deviceName.text = SpannableString(context.getString(R.string.device_id, device.id))
                    .apply {
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            length - device.id.length,
                            length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                deviceDistance.text = formatDistanceSpannableString(distance, context)

                deviceCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    // we can't change list in bind because it will lead to IllegalStateException
                    deviceCheckBox.post {
                        if (isChecked) {
                            deviceListener.addDeviceChart(device.id)
                        } else {
                            deviceListener.remoteDeviceChart(device.id)
                        }
                    }
                }

                deviceCheckBox.isChecked = checked

                if (error==null ||error.message.isBlank()) {
                    deviceContainer.setBackgroundResource(R.color.white)
                    deviceError.isVisible = false
                } else {
                    deviceError.isVisible = true
                    deviceError.text = error.message
                    deviceContainer.setBackgroundResource(R.drawable.red_boarder_rect_shape)
                    deviceError.setOnClickListener {
                        deviceListener.showErrorList(device.id)
                    }
                }
            }
        }

        override fun getItemCount() = deviceIdList.size

        fun changeSelection(deviceId: String?) {
            selectedChartId = deviceId
            notifyItemRangeChanged(0, itemCount, Any())
        }

        class DeviceIdViewHolder(private val itemBinding: DeviceIdItemViewBinding) : RecyclerView.ViewHolder(itemBinding.root) {
            var deviceId: String? = null
            val deviceName: TextView = itemBinding.deviceIdTextView
            val deviceCheckBox: CheckBox = itemBinding.deviceIdChechBox
            val deviceDistance: TextView = itemBinding.deviceDistanceTextView
            var deviceError: TextView = itemBinding.deviceError
            var deviceContainer: ConstraintLayout = itemBinding.deviceItemContainer
        }

    }

    interface DeviceSelectListener {
        fun addDeviceChart(deviceId: String)
        fun remoteDeviceChart(deviceId: String)
        fun showErrorList(deviceId: String)
    }

    companion object {
        private val TAG = ScoreboardSettingsFragment::class.java.simpleName
    }
}
