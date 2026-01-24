package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HourlyAdapter(private val hourlyList: List<HourlyModel>) :
    RecyclerView.Adapter<HourlyAdapter.HourlyViewHolder>() {

    class HourlyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDigits: TextView = itemView.findViewById(R.id.tvHourDigits)
        val tvAmPm: TextView = itemView.findViewById(R.id.tvAmPm)
        val tvTemp: TextView = itemView.findViewById(R.id.tvHourTemp)
        val lottieView: com.airbnb.lottie.LottieAnimationView = itemView.findViewById(R.id.ivHourIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourlyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hourly, parent, false)
        return HourlyViewHolder(view)
    }

    override fun onBindViewHolder(holder: HourlyViewHolder, position: Int) {
        val item = hourlyList[position]
        holder.tvTemp.text = item.temp

        val fullTime = item.time.toString()
        val parts = fullTime.split("\n")

        if (parts.size >= 2) {
            holder.tvDigits.text = parts[0]
            holder.tvAmPm.text = parts[1]
            holder.tvAmPm.visibility = View.VISIBLE
        } else {
            holder.tvDigits.text = fullTime
            holder.tvAmPm.visibility = View.GONE
        }

        holder.lottieView.cancelAnimation()
        holder.lottieView.progress = 0f

        val cacheKey = "lottie_${item.icon}"

        com.airbnb.lottie.LottieCompositionFactory.fromRawRes(holder.itemView.context, item.icon, cacheKey)
            .addListener { result ->
                holder.lottieView.setComposition(result)
            }

        holder.lottieView.setOnClickListener {
            holder.lottieView.playAnimation()
        }
    }

    override fun getItemCount() = hourlyList.size
}