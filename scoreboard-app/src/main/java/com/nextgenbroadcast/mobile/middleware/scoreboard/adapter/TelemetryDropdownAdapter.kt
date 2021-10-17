package com.nextgenbroadcast.mobile.middleware.scoreboard.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nextgenbroadcast.mobile.middleware.scoreboard.adapter.TelemetryDropdownAdapter.TelemetryHolder
import com.nextgenbroadcast.mobile.middleware.scoreboard.databinding.ItemTelemetryCommandBinding
import com.nextgenbroadcast.mobile.middleware.scoreboard.entities.TelemetryEntity

class TelemetryDropdownAdapter(
    private val onItemChecked: (Int, String, Boolean) -> Unit
) : ListAdapter<TelemetryEntity, TelemetryHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TelemetryHolder {
        return TelemetryHolder(
            ItemTelemetryCommandBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: TelemetryHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TelemetryHolder(
        private val binding: ItemTelemetryCommandBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(model: TelemetryEntity) = with(binding.checkbox) {
            setOnCheckedChangeListener(null)
            isChecked = model.checked
            text = model.name
            setOnCheckedChangeListener { _, isChecked -> onItemChecked(adapterPosition, model.name, isChecked) }
        }

    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TelemetryEntity>() {
            override fun areItemsTheSame(oldItem: TelemetryEntity, newItem: TelemetryEntity): Boolean {
                return oldItem.name == newItem.name
            }

            override fun areContentsTheSame(oldItem: TelemetryEntity, newItem: TelemetryEntity): Boolean {
                return oldItem == newItem
            }

        }
    }

}