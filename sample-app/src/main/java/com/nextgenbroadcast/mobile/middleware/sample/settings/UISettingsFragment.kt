package com.nextgenbroadcast.mobile.middleware.sample.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.nextgenbroadcast.mobile.middleware.sample.databinding.FragmentUiSettingsBinding
import com.nextgenbroadcast.mobile.middleware.sample.lifecycle.ViewViewModel

class UISettingsFragment : Fragment() {

    private val viewViewModel: ViewViewModel by activityViewModels()

    private lateinit var binding: FragmentUiSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentUiSettingsBinding.inflate(inflater, container, false).apply {
        lifecycleOwner = viewLifecycleOwner
        viewModel = viewViewModel
        binding = this
    }.root

}