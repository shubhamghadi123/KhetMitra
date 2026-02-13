package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView

class SoilAdapter(
    private val soils: List<SoilType>,
    private val onSelected: (SoilType) -> Unit
) : RecyclerView.Adapter<SoilAdapter.ViewHolder>() {

    private var selectedPosition = -1

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rbSelect: RadioButton = view.findViewById(R.id.rbSoil)
        val container: View = view.findViewById(R.id.itemContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_soil_type, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val soil = soils[position]
        holder.rbSelect.text = "(${soil.nameEn})"
        holder.rbSelect.isChecked = position == selectedPosition

        val clickListener = View.OnClickListener {
            selectedPosition = holder.adapterPosition
            notifyDataSetChanged()
            onSelected(soil)
        }

        holder.rbSelect.setOnClickListener(clickListener)
        holder.container.setOnClickListener(clickListener)
    }

    override fun getItemCount() = soils.size
}