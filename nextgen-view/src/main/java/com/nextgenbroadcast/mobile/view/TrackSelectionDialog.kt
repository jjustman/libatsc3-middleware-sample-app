package com.nextgenbroadcast.mobile.view

import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.DialogFragment
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.TrackNameProvider
import com.google.android.exoplayer2.util.Assertions

class TrackSelectionDialog : DialogFragment() {
    private var titleStr = ""
    private var onClickListener: DialogInterface.OnClickListener? = null
    private var onDismissListener: DialogInterface.OnDismissListener? = null

    private var mappedTrackInfo: MappedTrackInfo? = null
    private var initialParameters: DefaultTrackSelector.Parameters? = null

    private var trackNameProvider: TrackNameProvider? = null
    private var radioGroupMap = mutableMapOf<Int, RadioGroup>()
    private var overridesMap: MutableMap<Int, Pair<Int, Int>?> = mutableMapOf()
    private var disabledRendersSet: MutableSet<Int> = mutableSetOf()
    private var currentTrackSelection: TrackSelectionArray? = null

    private fun init(
            titleStr: String,
            mappedTrackInfo: MappedTrackInfo,
            initialParameters: DefaultTrackSelector.Parameters,
            onClickListener: DialogInterface.OnClickListener,
            onDismissListener: DialogInterface.OnDismissListener,
            currentTrackSelection: TrackSelectionArray) {
        this.titleStr = titleStr
        this.onClickListener = onClickListener
        this.onDismissListener = onDismissListener
        this.mappedTrackInfo = mappedTrackInfo
        this.initialParameters = initialParameters
        this.currentTrackSelection = currentTrackSelection
    }

    fun getIsDisabled(rendererIndex: Int): Boolean {
        return disabledRendersSet.contains(rendererIndex)
    }

    fun getOverrides(rendererIndex: Int): Pair<Int, Int>? {
        return overridesMap[rendererIndex]
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AppCompatDialog(activity, R.style.TrackSelectionDialogThemeOverlay)
        dialog.setTitle(titleStr)
        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.onDismiss(dialog)
    }

    private fun showTrackSelections() {
        val trackInfo = mappedTrackInfo ?: return
        val layoutInflater = LayoutInflater.from(context)
        val llTrackSelectionContainer = requireView().findViewById<ViewGroup>(R.id.llTrackSelectionContainer)

        for (i in 0 until trackInfo.rendererCount) {
            if (showTracksForRenderer(trackInfo, i)) {
                val trackType = trackInfo.getRendererType(i)
                val trackGroupArray = trackInfo.getTrackGroups(i)

                val titleTextView = layoutInflater.inflate(R.layout.track_text_view, llTrackSelectionContainer, false) as TextView
                val currentSelectedFormat = currentTrackSelection?.get(i)?.selectedFormat
                var radioButtonGroup = radioGroupMap[trackType]
                if (radioButtonGroup == null) {
                    titleTextView.text = getTrackTypeString(resources, trackType)
                    llTrackSelectionContainer.addView(titleTextView)
                    radioButtonGroup = layoutInflater.inflate(R.layout.track_radio_group, llTrackSelectionContainer, false) as RadioGroup
                    addNoneValueInGroup(radioButtonGroup, i, llTrackSelectionContainer, layoutInflater)
                    radioGroupMap[trackType] = radioButtonGroup
                }

                trackGroupArray?.let {
                    for (m in 0 until trackGroupArray.length) {
                        val trackGroup = trackGroupArray[m]
                        val trackFormat = trackGroup.getFormat(0)
                        val radioButton: RadioButton = layoutInflater.inflate(R.layout.track_radio_button, llTrackSelectionContainer, false) as RadioButton
                        radioButton.text = trackNameProvider?.getTrackName(trackFormat)

                        radioButton.tag = Triple(i, m, 0)
                        radioButtonGroup.addView(radioButton)

                        /** set selection in radioButton  */
                        if (!disabledRendersSet.contains(i)
                                && currentSelectedFormat == trackFormat) {
                            radioButtonGroup.check(radioButton.id)
                        }
                    }
                }
                if (radioButtonGroup.parent == null) {
                    llTrackSelectionContainer.addView(radioButtonGroup)
                }
            }
        }
    }
    private fun addNoneValueInGroup(radioButtonGroup: RadioGroup, renderNum: Int, root: ViewGroup, inflater: LayoutInflater) {
        val radioButton: RadioButton = inflater.inflate(R.layout.track_radio_button, root, false) as RadioButton
        radioButton.text = getString(R.string.exo_track_selection_none)
        radioButton.tag = Triple(renderNum, -1, -1)
        radioButtonGroup.addView(radioButton)
        initialParameters?.let {
            if (it.getRendererDisabled(renderNum)) {
                disabledRendersSet.add(renderNum)
                radioButtonGroup.check(radioButton.id)
            }
        }

    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false)

        val cancelButton = dialogView.findViewById<Button>(R.id.track_selection_dialog_cancel_button)
        val okButton = dialogView.findViewById<Button>(R.id.track_selection_dialog_ok_button)
        cancelButton.setOnClickListener { view: View? -> dismiss() }
        okButton.setOnClickListener {
            onClickListener?.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
            dismiss()
        }

        return dialogView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        trackNameProvider = DefaultTrackNameProvider(resources)
        showTrackSelections()
    }

    companion object {
        fun willHaveContent(trackSelector: DefaultTrackSelector): Boolean {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo
            return mappedTrackInfo != null && willHaveContent(mappedTrackInfo)
        }

        private fun willHaveContent(mappedTrackInfo: MappedTrackInfo): Boolean {
            for (i in 0 until mappedTrackInfo.rendererCount) {
                if (showTracksForRenderer(mappedTrackInfo, i)) {
                    return true
                }
            }
            return false
        }

        fun createForTrackSelector(title: String, currentTrackSelection: TrackSelectionArray,
                trackSelector: DefaultTrackSelector, onDismissListener: DialogInterface.OnDismissListener): TrackSelectionDialog {

            val mappedTrackInfo = Assertions.checkNotNull(trackSelector.currentMappedTrackInfo)

            val trackSelectionDialog = TrackSelectionDialog()
            val parameters = trackSelector.parameters
            trackSelectionDialog.init(
                    title,
                    mappedTrackInfo,
                    parameters,
                    onClickListener = { _, _ ->
                        positiveOnClickListener(parameters, mappedTrackInfo, trackSelectionDialog, trackSelector)
                    },
                    onDismissListener = onDismissListener, currentTrackSelection)
            return trackSelectionDialog
        }


        private fun positiveOnClickListener(parameters: DefaultTrackSelector.Parameters,
                                            mappedTrackInfo: MappedTrackInfo,
                                            trackSelectionDialog: TrackSelectionDialog,
                                            trackSelector: DefaultTrackSelector) {

            trackSelectionDialog.updateOverrides()
            val builder = parameters.buildUpon()

            for (i in 0 until mappedTrackInfo.rendererCount) {
                builder
                        .clearSelectionOverrides(i)
                        .setRendererDisabled(
                                i,
                                trackSelectionDialog.getIsDisabled(i))
                val overrides = trackSelectionDialog.getOverrides(i)

                if (overrides != null && !trackSelectionDialog.getIsDisabled(i)) {
                    val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
                    val (groupIndex, trackIndex) = overrides
                    for (k in 0 until trackGroupArray.length) {
                        if (groupIndex == k) {
                            builder.setSelectionOverride(i, mappedTrackInfo.getTrackGroups(i), SelectionOverride(groupIndex, trackIndex))
                        }
                    }
                }
            }
            trackSelector.setParameters(builder)
        }

        private fun showTracksForRenderer(mappedTrackInfo: MappedTrackInfo?, rendererIndex: Int): Boolean {
            if (mappedTrackInfo == null) return false
            val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)
            if (trackGroupArray?.length == 0) {
                return false
            }
            val trackType = mappedTrackInfo.getRendererType(rendererIndex)
            return isSupportedTrackType(trackType)
        }

        private fun isSupportedTrackType(trackType: Int): Boolean {
            return when (trackType) {
                C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT -> true
                else -> false
            }
        }

        private fun getTrackTypeString(resources: Resources, trackType: Int): String {
            return when (trackType) {
                C.TRACK_TYPE_AUDIO -> resources.getString(R.string.exo_track_selection_title_audio)
                C.TRACK_TYPE_TEXT -> resources.getString(R.string.exo_track_selection_title_text)
                else -> throw IllegalArgumentException()
            }
        }
    }

    private fun updateOverrides() {
        overridesMap.clear()

        radioGroupMap.values.forEach { radioGroup ->
            var selectedRendererIndex = -1

            val isNoneSelected = (radioGroup[0] as RadioButton).isChecked
            val hasSelection = radioGroup.checkedRadioButtonId >= 0
            for (i in 1 until radioGroup.size) {
                val radioButton = radioGroup[i] as RadioButton

                if (radioButton.tag is Triple<*, *, *>) {
                    val (renderIndex, groupIndex, trackIndex) = radioButton.tag as Triple<Int, Int, Int>
                    if (isNoneSelected) {
                        disabledRendersSet.add(renderIndex)
                    } else if (hasSelection) {
                        if (radioButton.isChecked) {
                            selectedRendererIndex = renderIndex
                            disabledRendersSet.remove(renderIndex)
                            overridesMap[renderIndex] = Pair(groupIndex, trackIndex)
                        } else if (selectedRendererIndex != renderIndex) {
                            disabledRendersSet.add(renderIndex)
                        }
                    } else {
                        disabledRendersSet.remove(renderIndex)
                    }
                }
            }
        }
    }

    init {
        // Retain instance across activity re-creation to prevent losing access to init data.
        retainInstance = true
    }
}