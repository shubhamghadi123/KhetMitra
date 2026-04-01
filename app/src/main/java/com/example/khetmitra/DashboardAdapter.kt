package com.example.khetmitra

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip

class DashboardAdapter(
    private val items: ArrayList<DataModels>,
    private val onItemClick: (DataModels) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: MaterialCardView = view.findViewById(R.id.cardRoot)
        val cardIcon: MaterialCardView = view.findViewById(R.id.cardIcon)
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val chipTag: Chip = view.findViewById(R.id.chipTag)
        val viewBottomBar: View = view.findViewById(R.id.viewBottomBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        holder.tvTitle.setTextColor(item.textColor)
        holder.tvSubtitle.text = item.subtitle
        holder.tvSubtitle.setTextColor(item.textColor)
        holder.tvIcon.text = item.emoji
        holder.chipTag.text = item.tag
        holder.chipTag.setTextColor(item.textColor)

        val accent = item.accentColor

        holder.root.setCardBackgroundColor(item.bgColor)
        holder.root.strokeColor = ColorUtils.setAlphaComponent(accent, 50)

        holder.cardIcon.setCardBackgroundColor(ColorUtils.setAlphaComponent(accent, 40))
        holder.cardIcon.strokeColor = ColorUtils.setAlphaComponent(accent, 60)

        holder.chipTag.setTextColor(accent)
        holder.chipTag.chipBackgroundColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(accent, 30)
        )
        holder.chipTag.chipStrokeColor = ColorStateList.valueOf(
            ColorUtils.setAlphaComponent(accent, 55)
        )
        holder.viewBottomBar.backgroundTintList = ColorStateList.valueOf(accent)

        holder.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}