package com.nextgenbroadcast.mobile.middleware.sample.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.*
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.nextgenbroadcast.mobile.middleware.sample.R
import com.nextgenbroadcast.mobile.middleware.sample.databinding.DialogSettingsBinding
import com.nextgenbroadcast.mobile.middleware.sample.lifecycle.ViewViewModel
import com.nextgenbroadcast.mobile.middleware.sample.settings.SettingsDialog.SettingsPage.*
import kotlin.system.exitProcess


class SettingsDialog : DialogFragment() {

    private val viewViewModel: ViewViewModel by activityViewModels()

    private lateinit var binding: DialogSettingsBinding
    private lateinit var pagerAdapter: SettingsPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.SettingsDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogSettingsBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = viewViewModel
        }
        dialog?.setTitle(R.string.settings_title)
        pagerAdapter = SettingsPagerAdapter()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewPager.adapter = pagerAdapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = pagerAdapter.getTabName(position)
        }.attach()
        setChildFragmentResultListener(REQUEST_KEY_SCAN_RANGE, ::setFragmentResultAndDismiss)
        setChildFragmentResultListener(REQUEST_KEY_FREQUENCY, ::setFragmentResultAndDismiss)
        setChildFragmentResultListener(REQUEST_KEY_APPLY_CONFIG) { _, _ -> showReloadAppDialog() }
    }

    private fun showReloadAppDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_reload_title)
            .setMessage(R.string.dialog_reload_message)
            .setPositiveButton(R.string.dialog_reload_positive) { _, _ ->
                triggerRebirth()
            }
            .setNegativeButton(R.string.dialog_reload_negative, null).show()
    }

    private fun triggerRebirth() {
        val activity = requireActivity()
        val component = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.component
            ?: return // null means unable to determine default activity
        activity.startActivity(Intent.makeRestartActivityTask(component))
        exitProcess(0)
    }

    private fun setChildFragmentResultListener(key: String, listener: (String, Bundle) -> Unit) {
        childFragmentManager.setFragmentResultListener(key, viewLifecycleOwner, listener)
    }

    private fun setFragmentResultAndDismiss(key: String, result: Bundle) {
        setFragmentResult(key, result)
        dismiss()
    }

    enum class SettingsPage {
        Tune,
        UI,
        Telemetry,
        Logs,
        Config;

        companion object {

            fun getOrNull(ordinal: Int): SettingsPage? {
                return values().getOrNull(ordinal)
            }
        }

    }

    private inner class SettingsPagerAdapter : FragmentStateAdapter(this) {

        override fun getItemCount(): Int = SettingsPage.values().size

        fun getTabName(position: Int): String = when (SettingsPage.getOrNull(position)) {
            Tune -> getString(R.string.settings_tab_tune)
            UI -> getString(R.string.settings_tab_ui)
            Telemetry -> getString(R.string.settings_tab_telemetry)
            Logs -> getString(R.string.settings_tab_logs)
            Config -> getString(R.string.settings_tab_config)
            null -> throw IllegalArgumentException("Unknown position $position")
        }

        override fun createFragment(position: Int): Fragment = when (SettingsPage.getOrNull(position)) {
            Tune -> TuneSettingsFragment().apply { arguments = this@SettingsDialog.arguments }
            UI -> UISettingsFragment()
            Telemetry -> TelemetrySettingsFragment()
            Logs -> LogsSettingsFragment()
            Config -> ConfigSettingsFragment()
            null -> throw IllegalArgumentException(
                "Unable to create instance of Fragment for position $position"
            )
        }

    }

    companion object {
        val TAG: String = SettingsDialog::class.java.simpleName

        const val PARAM_FREQUENCY = "param_frequency"

        const val REQUEST_KEY_FREQUENCY = "requestKey_frequency"
        const val REQUEST_KEY_SCAN_RANGE = "requestKey_scan_range"
        const val REQUEST_KEY_APPLY_CONFIG = "requestKey_dismiss"

        fun newInstance(freqKhz: Int?): SettingsDialog {
            return SettingsDialog().apply {
                if (freqKhz != null) {
                    arguments = Bundle().apply {
                        putInt(PARAM_FREQUENCY, freqKhz)
                    }
                }
            }
        }
    }
}