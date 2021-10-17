package com.nextgenbroadcast.mobile.middleware.sample.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.nextgenbroadcast.mobile.middleware.sample.adapter.LogsAdapter
import com.nextgenbroadcast.mobile.middleware.sample.databinding.FragmentLogSettingsBinding
import com.nextgenbroadcast.mobile.middleware.sample.lifecycle.ViewViewModel
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfo
import kotlinx.coroutines.launch

class LogsSettingsFragment : Fragment() {

    private val viewViewModel: ViewViewModel by activityViewModels()
    private lateinit var binding: FragmentLogSettingsBinding

    private val adapter: LogsAdapter by lazy {
        LogsAdapter(onSwitchChanged = { name, enabled ->
            viewViewModel.changeLogFlagStatus(name, enabled)
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentLogSettingsBinding.inflate(inflater, container, false).apply {
        lifecycleOwner = viewLifecycleOwner
        binding = this
    }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvLogs.adapter = adapter
        viewViewModel.groupedLogsInfo.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }
    }

}