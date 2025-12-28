package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DashboardAdapter(private val itemList: List<DashboardModel>) :
    RecyclerView.Adapter<DashboardAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvCardTitle)
        val tvSubtitle: TextView = itemView.findViewById(R.id.tvCardSubtitle)
        val tvIcon: ImageView = itemView.findViewById(R.id.tvIcon)
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
        holder.tvIcon.setImageResource(item.iconDrawable)
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}