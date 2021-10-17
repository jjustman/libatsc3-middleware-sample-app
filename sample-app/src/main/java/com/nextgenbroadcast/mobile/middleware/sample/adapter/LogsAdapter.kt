package com.nextgenbroadcast.mobile.middleware.sample.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.nextgenbroadcast.mobile.middleware.sample.adapter.LogsAdapter.LogViewHolder
import com.nextgenbroadcast.mobile.middleware.sample.databinding.ItemLogGroupBinding
import com.nextgenbroadcast.mobile.middleware.sample.databinding.ItemLogRecordBinding
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfo
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfo.Group
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfo.Record
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfoType
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfoType.GROUP
import com.nextgenbroadcast.mobile.middleware.sample.model.LogInfoType.RECORD

class LogsAdapter(
    private val onSwitchChanged: (String, Boolean) -> Unit
) : ListAdapter<LogInfo, LogViewHolder>(itemDiffUtil) {

    abstract class LogViewHolder(view: View) : ViewHolder(view) {
        abstract fun bind(model: LogInfo)
    }

    inner class RecordHolder(val binding: ItemLogRecordBinding) : LogViewHolder(binding.root) {
        override fun bind(model: LogInfo) = with(binding.enableDebuggingInformation) {
            if (model !is Record) return@with
            setOnCheckedChangeListener(null)
            isChecked = model.enabled
            text = model.displayName
            setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(model.name, isChecked)
            }
        }
    }

    inner class GroupHolder(private val binding: ItemLogGroupBinding) : LogViewHolder(binding.root) {
        override fun bind(model: LogInfo) {
            if (model !is Group) return
            binding.groupName.text = model.title
        }
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).type.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (LogInfoType.getOrNull(viewType)) {
            GROUP -> GroupHolder(ItemLogGroupBinding.inflate(inflater, parent, false))
            RECORD -> RecordHolder(ItemLogRecordBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Cannot create ViewHolder for viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int, payloads: List<Any>) {
        val enabled = payloads.getOrNull(0) as? Boolean
        if (holder is RecordHolder && enabled != null) {
            holder.binding.enableDebuggingInformation.isChecked = enabled
        } else {
             super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val itemDiffUtil = object : DiffUtil.ItemCallback<LogInfo>() {
            override fun areItemsTheSame(
                oldItem: LogInfo,
                newItem: LogInfo
            ): Boolean {
                return when {
                    oldItem is Group && newItem is Group -> oldItem.title == newItem.title
                    oldItem is Record && newItem is Record -> oldItem.name == newItem.name
                    else -> false
                }
            }

            override fun areContentsTheSame(
                oldItem: LogInfo,
                newItem: LogInfo
            ): Boolean = oldItem == newItem

            override fun getChangePayload(oldItem: LogInfo, newItem: LogInfo): Any? {
                return if (oldItem is Record && newItem is Record && oldItem.enabled != newItem.enabled) {
                    newItem.enabled
                } else null
            }

        }
    }

}