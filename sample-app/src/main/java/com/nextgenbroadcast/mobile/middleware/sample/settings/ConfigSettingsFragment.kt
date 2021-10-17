package com.nextgenbroadcast.mobile.middleware.sample.settings

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.nextgenbroadcast.mobile.middleware.dev.config.DevConfig
import com.nextgenbroadcast.mobile.middleware.sample.databinding.FragmentConfigSettingsBinding
import com.nextgenbroadcast.mobile.middleware.sample.settings.SettingsDialog.Companion.REQUEST_KEY_APPLY_CONFIG

class ConfigSettingsFragment : Fragment() {

    private lateinit var binding: FragmentConfigSettingsBinding
    private lateinit var resultLauncher: ActivityResultLauncher<Unit>

    private val devConfig: DevConfig by lazy {
        DevConfig.get(requireContext())
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        resultLauncher = registerForActivityResult(GetFileUriContract(), ::onUriGranted)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentConfigSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkDevConfigStatus()
        with (binding) {
            buttonUpload.setOnClickListener {
                resultLauncher.launch(Unit)
            }
            buttonDelete.setOnClickListener {
                devConfig.remove(requireContext())
                checkDevConfigStatus()
            }
            buttonSave.setOnClickListener {
                devConfig.write(requireContext(), configContent.text.toString())
                setFragmentResult(REQUEST_KEY_APPLY_CONFIG, Bundle.EMPTY)
            }
        }
    }

    private fun onUriGranted(uri: Uri?) {
        uri ?: return
        devConfig.write(requireContext(), uri)
        checkDevConfigStatus()
        setFragmentResult(REQUEST_KEY_APPLY_CONFIG, Bundle.EMPTY)
    }

    private fun checkDevConfigStatus() {
        binding.buttonDelete.isEnabled = devConfig.configDetected
        binding.configContent.setText(devConfig.rawConfig)
    }

    class GetFileUriContract : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit?): Intent {
            val chooseFile = Intent(Intent.ACTION_GET_CONTENT)
            chooseFile.addCategory(Intent.CATEGORY_OPENABLE)
            chooseFile.type = "*/*"
            return chooseFile
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
            return if (resultCode == RESULT_OK) intent?.data else null
        }

    }

    override fun onDetach() {
        if (this::resultLauncher.isInitialized) {
            resultLauncher.unregister()
        }
        super.onDetach()
    }

}