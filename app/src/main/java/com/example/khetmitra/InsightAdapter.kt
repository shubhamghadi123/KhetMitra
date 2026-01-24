package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InsightAdapter(private val insightList: List<InsightModel>) :
    RecyclerView.Adapter<InsightAdapter.InsightViewHolder>() {

    class InsightViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvInsightTitle)
        val tvDesc: TextView = itemView.findViewById(R.id.tvInsightDesc)
        val ivImage: ImageView = itemView.findViewById(R.id.ivInsightImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InsightViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_insight, parent, false)
        return InsightViewHolder(view)
    }

    override fun onBindViewHolder(holder: InsightViewHolder, position: Int) {
        val item = insightList[position]

        holder.tvTitle.text = item.title
        holder.tvDesc.text = item.description
        com.bumptech.glide.Glide.with(holder.itemView.context)
            .load(item.imageRes) // R.drawable.rain_image
            .override(300, 300) // Resize in memory to save RAM
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .into(holder.ivImage)

        // Clear tags for translation
        holder.tvTitle.tag = null
        holder.tvDesc.tag = null
    }

    override fun getItemCount(): Int {
        return insightList.size
    }
}