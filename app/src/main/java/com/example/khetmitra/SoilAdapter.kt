package com.example.khetmitra

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import androidx.core.graphics.toColorInt
import com.google.android.material.imageview.ShapeableImageView

class SoilAdapter(
    private var soils: List<SoilType>,
    private val onSelected: (SoilType) -> Unit
) : RecyclerView.Adapter<SoilAdapter.ViewHolder>() {

    private var selectedPosition = -1

    fun updateData(newItems: List<SoilType>) {
        this.soils = newItems
        this.selectedPosition = -1
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.soilCard)
        val tvSoilName: TextView = view.findViewById(R.id.tvSoilName)
        val ivSoilTexture: ShapeableImageView = view.findViewById(R.id.ivSoilTexture)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_soil_type, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val soil = soils[position]
        val context = holder.itemView.context
        holder.tvSoilName.text = soil.nameEn

        val colorBase = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(soil.hexColor.toColorInt())
        }

        val tiledPattern = ContextCompat.getDrawable(context, soil.textureRes)

        val combinedDrawable = LayerDrawable(arrayOf(colorBase, tiledPattern))

        holder.ivSoilTexture.setImageDrawable(combinedDrawable)

        val isSelected = position == selectedPosition
        holder.card.strokeWidth = if (isSelected) 4 else 0
        holder.card.strokeColor = "#4CAF50".toColorInt()

        holder.ivSoilTexture.strokeWidth = if (isSelected) 2.5f else 1f
        holder.ivSoilTexture.strokeColor = ColorStateList.valueOf(
            if (isSelected) "#4CAF50".toColorInt() else Color.LTGRAY
        )

        holder.itemView.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            if (prev != -1) notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onSelected(soil)
        }
    }

    fun clearSelection() {
        val previous = selectedPosition
        if (previous != -1) {
            selectedPosition = -1
            notifyItemChanged(previous)
        }
    }

    override fun getItemCount() = soils.size
}