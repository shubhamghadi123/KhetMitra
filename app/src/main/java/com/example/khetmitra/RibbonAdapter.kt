package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import androidx.core.graphics.toColorInt

class RibbonAdapter(
    private val items: List<RibbonData>,
    private val onSelected: (RibbonData) -> Unit
) : RecyclerView.Adapter<RibbonAdapter.ViewHolder>() {

    private var selectedPosition = -1

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.ribbonCard)
        val tvResult: TextView = view.findViewById(R.id.tvRibbonResult)
        val tvSoil: TextView = view.findViewById(R.id.tvRibbonSoilType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ribbon_test, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvResult.text = item.result
        holder.tvSoil.text = item.soilType

        val isSelected = position == selectedPosition

        holder.card.strokeWidth = if (isSelected) 6 else 0
        holder.card.strokeColor = "#4CAF50".toColorInt()

        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onSelected(item)
        }
    }

    override fun getItemCount() = items.size

    fun clearSelection() {
        val previous = selectedPosition
        selectedPosition = -1
        notifyItemChanged(previous)
    }
}