package com.nextgenbroadcast.mobile.middleware.scoreboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import com.nextgenbroadcast.mobile.middleware.scoreboard.adapter.TelemetryDropdownAdapter
import com.nextgenbroadcast.mobile.middleware.scoreboard.databinding.TelemetryCommandsDialogBinding

class TelemetryCommandsDialog : DialogFragment() {

    private lateinit var binding: TelemetryCommandsDialogBinding
    private val viewModel: SharedViewModel by activityViewModels()
    private val adapter: TelemetryDropdownAdapter by lazy {
        TelemetryDropdownAdapter(onItemChecked = ::onItemChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CommandsDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = TelemetryCommandsDialogBinding.inflate(inflater, container, false)
        dialog?.setTitle(R.string.dialog_telemetry_commands_title)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvCommands.adapter = adapter
        binding.buttonOk.setOnClickListener { dismiss() }
        viewModel.telemetryCommands.observe(viewLifecycleOwner, adapter::submitList)
    }

    private fun onItemChecked(position: Int, name: String, isChecked: Boolean) {
        viewModel.selectCommand(position, isChecked)
        // callback to the root fragment/activity to modify EditText's value
        setFragmentResult(TELEMETRY_RESULT_ITEM_CHECKED, packResult(name, isChecked))
    }

    private fun packResult(name: String, isChecked: Boolean): Bundle = Bundle().apply {
        putString(TELEMETRY_KEY_NAME, name)
        putBoolean(TELEMETRY_KEY_IS_CHECKED, isChecked)
    }

    companion object {
        val TAG: String = TelemetryCommandsDialog::class.java.simpleName

        private const val TELEMETRY_KEY_IS_CHECKED = "telemetry_dialog_is_checked_key"
        private const val TELEMETRY_KEY_NAME = "telemetry_dialog_name_key"

        const val TELEMETRY_RESULT_ITEM_CHECKED = "telemetry_dialog_item_checked_result"

        fun unpackResult(result: Bundle): Pair<String, Boolean> = result.run {
            getString(TELEMETRY_KEY_NAME, "") to getBoolean(TELEMETRY_KEY_IS_CHECKED)
        }

        fun newInstance(): TelemetryCommandsDialog = TelemetryCommandsDialog()

    }

}