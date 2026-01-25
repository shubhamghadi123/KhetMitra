package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DashboardAdapter(
    private val itemList: List<DataModels>,
    private val onItemClick: (DataModels) -> Unit
) : RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvCardTitle)
        val tvSubtitle: TextView = itemView.findViewById(R.id.tvCardSubtitle)
        val tvIcon: com.airbnb.lottie.LottieAnimationView = itemView.findViewById(R.id.tvIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itemList[position]

        holder.tvTitle.text = item.title
        holder.tvSubtitle.text = item.subtitle

        holder.tvIcon.cancelAnimation()
        holder.tvIcon.progress = 0f

        val cacheKey = "weather_${item.iconDrawable}"

        com.airbnb.lottie.LottieCompositionFactory.fromRawRes(holder.itemView.context, item.iconDrawable, cacheKey)
            .addListener { composition ->
                holder.tvIcon.setComposition(composition)
            }

        // We must clear the 'tag' so the Translator doesn't get confused by old tags from recycled views.
        holder.tvTitle.tag = null
        holder.tvSubtitle.tag = null

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}