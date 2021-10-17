package com.nextgenbroadcast.mobile.middleware.sample.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.LocationFrequencyType
import com.nextgenbroadcast.mobile.middleware.dev.telemetry.reader.SensorFrequencyType
import com.nextgenbroadcast.mobile.middleware.sample.databinding.FragmentTelemetrySettingsBinding
import com.nextgenbroadcast.mobile.middleware.sample.lifecycle.ViewViewModel

class TelemetrySettingsFragment : Fragment() {

    private val viewViewModel: ViewViewModel by activityViewModels()
    private val locationFrequencyTypeList = LocationFrequencyType.values().toList()
    private val sensorFrequencyTypeList = SensorFrequencyType.values().toList()

    private lateinit var binding: FragmentTelemetrySettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTelemetrySettingsBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = viewViewModel
        }

        with(binding) {
            requireContext().let {
                spinnerLocationFrequency.adapter = ArrayAdapter(
                    it,
                    android.R.layout.simple_spinner_dropdown_item,
                    locationFrequencyTypeList
                )

                spinnerSensorsFrequency.adapter = ArrayAdapter(
                    it,
                    android.R.layout.simple_spinner_dropdown_item,
                    sensorFrequencyTypeList
                )

                spinnerSensorsFrequency.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) {}

                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            viewViewModel.sensorFrequencyType.postValue(sensorFrequencyTypeList[position])
                        }

                    }

                viewViewModel.sensorFrequencyType.value?.let {
                    spinnerSensorsFrequency.setSelection(sensorFrequencyTypeList.indexOf(it))
                }

                viewViewModel.locationFrequencyType.value?.let {
                    spinnerLocationFrequency.setSelection(locationFrequencyTypeList.indexOf(it))
                }

                spinnerLocationFrequency.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: AdapterView<*>?) {}

                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            viewViewModel.locationFrequencyType.postValue(locationFrequencyTypeList[position])
                        }

                    }
            }
        }

        return binding.root
    }

}