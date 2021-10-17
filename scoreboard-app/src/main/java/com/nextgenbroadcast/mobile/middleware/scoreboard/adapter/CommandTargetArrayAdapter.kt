package com.nextgenbroadcast.mobile.middleware.scoreboard.adapter

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import com.nextgenbroadcast.mobile.middleware.scoreboard.R
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.CommandTarget
import com.nextgenbroadcast.mobile.middleware.scoreboard.setBoldSpanFromTheEnd

class CommandTargetArrayAdapter(
    context: Context,
    private val targets: MutableList<CommandTarget>
) : ArrayAdapter<CommandTarget>(context, android.R.layout.simple_spinner_dropdown_item, targets) {

    private val inflater: LayoutInflater by lazy {
        LayoutInflater.from(context)
    }

    fun changeItems(items: List<CommandTarget>) {
        targets.clear()
        targets.addAll(items)
        notifyDataSetChanged()
    }

    fun indexOf(commandTarget: CommandTarget): Int {
        return when (commandTarget) {
            CommandTarget.Broadcast,
            is CommandTarget.SelectedDevices -> targets.indexOfFirst { commandTarget::class.java == it::class.java }
            is CommandTarget.Device -> targets.indexOf(commandTarget)
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        getDropDownView(position, convertView, parent)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(
            android.R.layout.simple_spinner_dropdown_item, parent, false
        )
        (view as? CheckedTextView)?.text = text(position)
        return view
    }

    private fun text(position: Int): CharSequence = when (val target = getItem(position)) {
        CommandTarget.Broadcast -> context.getString(R.string.command_target_broadcast)
        is CommandTarget.Device -> target.deviceId
        is CommandTarget.SelectedDevices -> context.getString(R.string.command_target_selected_devices, target.deviceIdList.size)
        null -> ""
    }
    
}