package com.nextgenbroadcast.mobile.middleware.sample.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.nextgenbroadcast.mobile.middleware.sample.R
import com.nextgenbroadcast.mobile.middleware.sample.databinding.FragmentTuneSettingsBinding
import com.nextgenbroadcast.mobile.middleware.sample.settings.SettingsDialog.Companion.PARAM_FREQUENCY
import com.nextgenbroadcast.mobile.middleware.sample.settings.SettingsDialog.Companion.REQUEST_KEY_FREQUENCY
import com.nextgenbroadcast.mobile.middleware.sample.settings.SettingsDialog.Companion.REQUEST_KEY_SCAN_RANGE
import org.xmlpull.v1.XmlPullParser

class TuneSettingsFragment : Fragment() {

    private lateinit var binding: FragmentTuneSettingsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTuneSettingsBinding.inflate(inflater, container, false).apply {
            lifecycleOwner = viewLifecycleOwner
        }

        with(binding) {
            frequencyEditText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    tuneAndDismiss()
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }

            arguments?.let { args ->
                val freqKhz = args.getInt(PARAM_FREQUENCY, 0)
                if (freqKhz > 0) {
                    frequencyEditText.setText((freqKhz / 1000).toString())
                }
            }

            tuneBtn.setOnClickListener {
                tuneAndDismiss()
            }

            scanRangeBtn.setOnClickListener {
                scanAndDismiss()
            }
        }

        return binding.root
    }

    private fun tuneAndDismiss() {
        val freqKhz =
            binding.frequencyEditText.text.toString().toIntOrNull()?.let { it * 1000 } ?: 0

        val result = Bundle().apply {
            putInt(PARAM_FREQUENCY, freqKhz)
        }
        setFragmentResult(REQUEST_KEY_FREQUENCY, result)
    }

    private fun scanAndDismiss() {
        val frequencyList = readDefaultFrequencies()

        val result = Bundle().apply {
            putIntegerArrayList(PARAM_FREQUENCY, frequencyList)
        }
        setFragmentResult(REQUEST_KEY_SCAN_RANGE, result)
    }

    private fun readDefaultFrequencies(): ArrayList<Int> {
        val frequencyList = arrayListOf<Int>()
        val xrp = resources.getXml(R.xml.default_frequency_range)
        while (xrp.next() != XmlPullParser.END_DOCUMENT) {
            if (xrp.eventType != XmlPullParser.START_TAG) {
                continue
            }

            while (xrp.next() != XmlPullParser.END_TAG) {
                if (xrp.eventType != XmlPullParser.START_TAG) {
                    continue
                }

                while (xrp.next() != XmlPullParser.END_TAG) {
                    if (xrp.eventType == XmlPullParser.TEXT) {
                        xrp.text?.toIntOrNull()?.let { frequency ->
                            frequencyList.add(frequency * 1000)
                        }
                    } else {
                        check(xrp.eventType == XmlPullParser.START_TAG)
                        var depth = 1
                        while (depth != 0) {
                            when (xrp.next()) {
                                XmlPullParser.END_TAG -> depth--
                                XmlPullParser.START_TAG -> depth++
                            }
                        }
                    }
                }
            }
        }
        return frequencyList
    }

}