package com.example.khetmitra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ForecastAdapter(private val forecastList: List<ForecastModel>) :
    RecyclerView.Adapter<ForecastAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tvDayName)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val ivIcon: ImageView = itemView.findViewById(R.id.imgWeatherIcon)
        val tvHigh: TextView = itemView.findViewById(R.id.tvHighTemp)
        val tvLow: TextView = itemView.findViewById(R.id.tvLowTemp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_forecast_day, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = forecastList[position]
        holder.tvDay.text = item.day
        holder.tvDate.text = item.date
        holder.tvHigh.text = item.highTemp
        holder.tvLow.text = item.lowTemp
        holder.ivIcon.setImageResource(item.icon)

        holder.tvDay.tag = null
        holder.tvDate.tag = null
        holder.tvHigh.tag = null
        holder.tvLow.tag = null

        val prefs = holder.itemView.context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        val targetLang = prefs.getString("Language", com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
            ?: com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH

        // Only run translation if the target language is NOT English
        if (targetLang != com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH) {

            TranslationHelper.translateViewHierarchy(holder.tvDay, targetLang) { }
            TranslationHelper.translateViewHierarchy(holder.tvDate, targetLang) { }
            TranslationHelper.translateViewHierarchy(holder.tvHigh, targetLang) { }
            TranslationHelper.translateViewHierarchy(holder.tvLow, targetLang) { }
        }
    }

    override fun getItemCount() = forecastList.size
}